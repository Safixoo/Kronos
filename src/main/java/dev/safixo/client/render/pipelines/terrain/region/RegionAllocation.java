package dev.safixo.client.render.pipelines.terrain.region;

import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.util.GlBufferUtil;
import dev.safixo.client.render.gfx.util.RenderBuffer;
import org.lwjgl.opengl.*;
import dev.safixo.client.render.pipelines.terrain.SectionFlags;
import dev.safixo.client.render.pipelines.terrain.SectionManager;
import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.render.vertex.writers.TerrainFormat;

public class RegionAllocation {
	private static final int SPARE_BUFFER_ALLOC = 1024 * 1024 * 64;
	private static final int MIN_ALLOC = 1024 * 256;
	private static final int STRIDE = TerrainFormat.STRIDE;

	public RegionBuffer vertexBuffer;

	public long offset;
	public long capacity;

	private Allocation firstEntry;
	private Allocation freeAllocations;

	public static RenderBuffer SPARE_BUFFER;

	public RegionAllocation() {
		this(MIN_ALLOC);
	}

	public RegionAllocation(int size) {
		int newCapacity = Math.max(MIN_ALLOC, size);

		if (SPARE_BUFFER == null) {
			SPARE_BUFFER = new RenderBuffer(SPARE_BUFFER_ALLOC, GL15.GL_DYNAMIC_COPY);
		}

		this.vertexBuffer = new RegionBuffer(newCapacity, GL15.GL_STATIC_DRAW);
		SectionManager.getCurrentInstance().addMemory(newCapacity);

		this.capacity = newCapacity;
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

		// If it has been overpassed the copying buffer limit, do mental gymnastics.
		if (newSize > SPARE_BUFFER_ALLOC) {

			// If the region is more than 64MB avoid allocating a temporal buffer as is preferred
			// to not duplicate that much memory.
			if (this.offset > (64 << 20)) {
				this.vertexBuffer.allocateSpace((int) newSize, GL15.GL_STATIC_DRAW);

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
				RegionBuffer tempBuffer = new RegionBuffer((int) this.offset, GL15.GL_DYNAMIC_COPY);

				GlVertexBuffer regionBuffer = this.vertexBuffer.getVertexBuffer();
				GlVertexBuffer spareBuffer = tempBuffer.getVertexBuffer();

				GlBufferUtil.copyBufferToBuffer(regionBuffer, spareBuffer, 0, 0, (int) this.offset);

				this.vertexBuffer.allocateSpace((int) newSize, GL15.GL_STATIC_DRAW);

				GlBufferUtil.copyBufferToBuffer(spareBuffer, regionBuffer, 0, 0, (int) this.offset);

				tempBuffer.clear();
			}

			this.capacity = newSize;
			return;
		}

		GlVertexBuffer regionBuffer = this.vertexBuffer.getVertexBuffer();
		GlVertexBuffer spareBuffer = SPARE_BUFFER.getVertexBuffer();

		GlBufferUtil.copyBufferToBuffer(regionBuffer, spareBuffer, 0, 0, (int) this.offset);

		this.vertexBuffer.allocateSpace((int) newSize, GL15.GL_STATIC_DRAW);
		SectionManager.getCurrentInstance().addMemory(size);

		GlBufferUtil.copyBufferToBuffer(spareBuffer, regionBuffer, 0, 0, (int) this.offset);

		this.capacity = newSize;
	}

	// When freeing all the allocations of the region, is also wanted to avoid any interference
	// with the sections and the invalid region/allocation.
	public void clear() {
		this.vertexBuffer.clear();
		SectionManager.getCurrentInstance().removeMemory(this.vertexBuffer.getCapacity());

		this.capacity = 0;
		this.offset = 0;

		Allocation alloc = this.firstEntry;

		while (alloc != null) {
			int flags = alloc.render.flags;

			flags = SectionFlags.setPassesNonEmpty(flags, 0b00);
			flags = SectionFlags.setDrawableFaces(flags, 0b0);
			flags = SectionFlags.setDirty(flags, true);

			alloc.render.flags = flags;
			alloc.render.clearAllocations();
			alloc.render = null;

			alloc = alloc.next;
		}

		this.firstEntry = null;
		this.freeAllocations = null;
	}

	public boolean isEmpty() {
		return this.firstEntry == null;
	}

	// Returns first << 32 | count.
	public long allocate(SectionRender render, long vertexData, int size, int side) {
		Allocation alloc = this.fitInFree(size);

		if (alloc == null) {
			alloc = this.allocateNew(render, size, side);
		}

		alloc.render = render;
		alloc.sectionId = Allocation.sectionId(render, side);
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
				this.remove(render, side);
			}
			drawData = this.allocate(render, data, size, side);
		}

		return drawData;
	}

	// Searches for a space in allocations already freed.
	private Allocation fitInFree(int spaceNeeded) {
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
	public Allocation findPrevAlloc(SectionRender render, int side) {
		Allocation alloc = this.firstEntry;
		int sectionId = Allocation.sectionId(render, side);

		while (alloc != null && alloc.sectionId != sectionId) {
			alloc = alloc.next;
		}

		return alloc;
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
	public void remove(SectionRender render, int side) {
		Allocation alloc = this.firstEntry;

		// shouldn't happen
		if (alloc == null) {
			return;
		}

		int sectionId = Allocation.sectionId(render, side);

		// The allocation shouldn't be null as we are removing an existent
		// allocation.
		if (alloc.sectionId == sectionId) {
			this.firstEntry = this.firstEntry.next;
			this.addToFreeList(alloc);
		} else {
			while (alloc.next != null && alloc.next.sectionId != sectionId) {
				alloc = alloc.next;
			}

			// This shouldn't happen, but in the life there's a lot of things that shouldn't,
			// but they still happen, right?
			if (alloc.next == null) {
				return;
			}

			// alloc.next == render && alloc.next.size == side
			Allocation next = alloc.next;
			alloc.next = next.next;

			this.addToFreeList(next);
		}
	}

	private void addToFreeList(Allocation alloc) {
		Allocation free = this.freeAllocations;

		alloc.render = null;
		alloc.sectionId = Allocation.UNDEFINED;
		alloc.next = null;

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
		this.vertexBuffer.upload(data, (int) (alloc.offset * STRIDE), size * STRIDE);
	}

	public static class Allocation {
		public static final int UNDEFINED = 0x8000000;

		public Allocation next;
		public SectionRender render;

		public int sectionId;

		public Allocation(SectionRender render, long offset, int size, int side) {
			this.render = render;
			this.sectionId = sectionId(render, side);
			this.offset = offset;
			this.size = size;
		}

		// Size and offset are written in vertex amount and not bytes to
		// avoid division to translate byte sizes to vertex counts.
		public long offset;
		public int size;

		public static int sectionId(SectionRender render, int side) {
			return render.regionIndex << 16 | side;
		}
	}
}
