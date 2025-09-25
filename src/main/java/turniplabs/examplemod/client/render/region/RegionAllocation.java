package turniplabs.examplemod.client.render.region;

import org.lwjgl.opengl.*;
import turniplabs.examplemod.client.render.SectionFlags;
import turniplabs.examplemod.client.render.SectionManager;
import turniplabs.examplemod.client.render.SectionRender;
import turniplabs.examplemod.client.render.vertex.writers.TerrainFormat;

import javax.annotation.Nullable;

public class RegionAllocation {
	private static final int SPARE_BUFFER_ALLOC = 1024 * 1024 * 16;
	private static final int MIN_ALLOC = 1024 * 256;
	private static final int STRIDE = TerrainFormat.STRIDE;

	private static int GL31_SUPPORT = -1;

	public final RegionVertexBuffer vertexBuffer;

	public long offset;
	public long capacity;

	private Allocation firstEntry;
	private Allocation freeAllocations;

	public static RegionVertexBuffer SPARE_BUFFER;

	public RegionAllocation() {
		this(MIN_ALLOC);
	}

	public RegionAllocation(int size) {
		int newCapacity = Math.max(MIN_ALLOC, size);

		if (SPARE_BUFFER == null) {
			SPARE_BUFFER = new RegionVertexBuffer(SPARE_BUFFER_ALLOC, GL15.GL_DYNAMIC_COPY);
		}

		if (GL31_SUPPORT == -1) {
			ContextCapabilities cap = GLContext.getCapabilities();
			GL31_SUPPORT = (cap.GL_ARB_copy_buffer || cap.OpenGL31) ? 1 : 0;
		}

		this.vertexBuffer = new RegionVertexBuffer(newCapacity);
		this.capacity = newCapacity;
	}

	private static void copyReadToTargetBuffer(int fromBuff, int fromOff, int toBuff, int toOff, long size) {
		GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, fromBuff);
		GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, toBuff);

		GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, fromOff, toOff, size);

		GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
		GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
	}

	public long getAmplification(long size) {
		return (size * 3) >>> 1;
	}

	public void resize(long size) {
		if (SPARE_BUFFER == null) {
			return;
		}

		long newSize = Math.max(getAmplification(size), size);

		// If there's a possibility of avoiding using a temp-buffer for the geometry
		// it's preferable.
		if (newSize > SPARE_BUFFER_ALLOC && size <= SPARE_BUFFER_ALLOC) {
			newSize = SPARE_BUFFER_ALLOC;
		}

		SectionManager.getCurrentInstance().removeMemory(this.capacity);

		// If there isn't OpenGL support for using a copy function, or it has been
		// overpassed the copying buffer limit, do mental gymnastics.
		if (newSize > SPARE_BUFFER_ALLOC || GL31_SUPPORT == 0) {

			// If the region is more than 64MB avoid allocating a temporal buffer as is preferred
			// to not duplicate that much memory.
			if (this.offset > (64 << 20) || GL31_SUPPORT == 0) {
				this.vertexBuffer.allocateSpace(newSize);

				Allocation alloc = this.firstEntry;

				while (alloc != null) {
					int flags = alloc.render.flags;

					flags = SectionFlags.setPassesNonEmpty(flags, 0b00);
					flags = SectionFlags.setDirty(flags, true);

					alloc.render.flags = flags;
					alloc = alloc.next;
				}

			} else {
				// Allocate a temporal buffer to hold region memory.
				RegionVertexBuffer tempBuffer = new RegionVertexBuffer(this.offset);

				int regionBuffer = this.vertexBuffer.vboId;
				int spareBuffer = tempBuffer.vboId;

				copyReadToTargetBuffer(regionBuffer, 0, spareBuffer, 0, this.offset);
				this.vertexBuffer.allocateSpace(newSize);
				copyReadToTargetBuffer(spareBuffer, 0, regionBuffer, 0, this.offset);

				tempBuffer.clear();
			}

			this.capacity = newSize;
			return;
		}

		int regionBuffer = this.vertexBuffer.vboId;
		int spareBuffer = SPARE_BUFFER.vboId;

		copyReadToTargetBuffer(regionBuffer, 0, spareBuffer, 0, this.offset);
		this.vertexBuffer.allocateSpace(newSize);
		copyReadToTargetBuffer(spareBuffer, 0, regionBuffer, 0, this.offset);

		this.capacity = newSize;
	}

	// When freeing all the allocations of the region, is also wanted to avoid any interference
	// with the sections and the invalid region/allocation.
	public void clear() {
		this.vertexBuffer.clear();
		Allocation alloc = this.firstEntry;

		while (alloc != null) {
			int flags = alloc.render.flags;

			flags = SectionFlags.setPassesNonEmpty(flags, 0b00);
			flags = SectionFlags.setDrawableFaces(flags, 0b0);
			flags = SectionFlags.setDirty(flags, true);

			alloc.render.flags = flags;
			alloc.render.region = RegionRender.NULL;
			alloc = alloc.next;
		}
	}

	// Returns first << 32 | count.
	public long allocate(SectionRender render, long vertexData, int size, int side) {
		Allocation alloc = this.fitInFree(size);

		if (alloc == null) {
			alloc = this.allocateNew(render, size, side);
		}

		alloc.render = render;
		alloc.side = side;
		this.uploadAllocation(alloc, vertexData, size);

		return packDrawData(size, (int) alloc.offset);
	}

	private Allocation allocateNew(SectionRender render, int size, int side) {
		long maxOffset = this.offset / STRIDE;
		int sizeInBytes = size * STRIDE;

		SectionManager.getCurrentInstance().addUsedMemory(size * TerrainFormat.STRIDE);

		if (this.offset + sizeInBytes > this.capacity) {
			this.resize(this.offset + sizeInBytes);
		}

		Allocation newAlloc = new Allocation(render, maxOffset, size, side);
		Allocation first = this.firstEntry;
		this.offset += sizeInBytes;

		if (first == null) {
			return this.firstEntry = newAlloc;
		}

		while (first.next != null) {
			first = first.next;
		}

		first.next = newAlloc;
		return newAlloc;
	}

	public long renewAllocation(SectionRender render, long data, int size, int side) {
		Allocation alloc = this.findPrevAlloc(render, side);
		long drawData;

		if (alloc != null && alloc.size >= size) {
			this.uploadAllocation(alloc, data, size);
			drawData = packDrawData(size, (int) alloc.offset);
		} else {
			if (alloc != null) {
				this.remove(render);
			}
			drawData = this.allocate(render, data, size, side);
		}

		return drawData;
	}

	// Searches for a space in allocations already freed.
	private @Nullable Allocation fitInFree(int spaceNeeded) {
		Allocation alloc = this.freeAllocations;

		// Base case: isn't any free space.
		if (alloc == null) {
			return null;
		}

		// Base case: first meets the criteria.
		if (alloc.size >= spaceNeeded) {
			this.freeAllocations = this.freeAllocations.next;
			alloc.next = null;

			return alloc;
		}

		while (alloc.next != null && alloc.next.size < spaceNeeded) {
			alloc = alloc.next;
		}

		// Didn't find any free allocation that meet the space needs.
		if (alloc.next == null) {
			return null;
		}

		Allocation returnAlloc = alloc.next;

		// If next isn't null, we find the alloc we wanted.
		alloc.next = alloc.next.next;
		returnAlloc.next = null;

		return returnAlloc;
	}

	// Searches for a previous allocation.
	public @Nullable Allocation findPrevAlloc(SectionRender render, int side) {
		Allocation alloc = this.firstEntry;

		while (alloc != null) {
			if (alloc.render == render && alloc.side == side) {
				return alloc;
			}

			alloc = alloc.next;
		}

		return null;
	}

	public static long packDrawData(int count, int first) {
		return (first & 0xFFFFFFFFL) << 32L | (count & 0xFFFFFFFFL);
	}

	public static int unpackFirst(long drawData) {
		return (int) (drawData >>> 32L);
	}

	public static int unpackCount(long drawData) {
		return (int) (drawData & 0xFFFFFFFFL);
	}

	// Removes allocation from the main pool, and saves in the free pool.
	public void remove(SectionRender render) {
		Allocation alloc = this.firstEntry;

		// alloc isn't null as we are already removing an existent.
		if (alloc.render == render) {
			alloc.render = null;
			alloc.side = -1;
			alloc.next = null;

			this.firstEntry = this.firstEntry.next;
		}

		// Next shouldn't be null as first doesn't fit the base case.
		//noinspection DataFlowIssue
		while (alloc.next.render != render) {
			alloc = alloc.next;
		}

		Allocation free = this.freeAllocations;

		// Save removed alloc in free pool.
		if (free != null) {
			while (free.next != null) {
				free = free.next;
			}

			free.next = alloc;
		} else {
			this.freeAllocations = alloc;
		}
	}

	public void uploadAllocation(Allocation alloc, long data, int size) {
		this.vertexBuffer.upload(data, alloc.offset * STRIDE, size * STRIDE);
	}

	public static class Allocation {
		public Allocation next;
		public SectionRender render;

		public Allocation(SectionRender render, long offset, int size, int side) {
			this.render = render;
			this.side = side;
			this.offset = offset;
			this.size = size;
		}

		// Size and offset are written in vertex amount and not bytes to
		// avoid division to translate byte sizes to vertex counts.
		public long offset;
		public int size;
		public int side;
	}
}
