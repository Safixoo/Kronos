package turniplabs.examplemod.client.render.region;

import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.render.SectionFlags;
import turniplabs.examplemod.client.render.SectionManager;
import turniplabs.examplemod.client.render.SectionRender;
import turniplabs.examplemod.client.vertex.writer.TerrainVertexWriter;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

public class RegionAllocation {
	private static final int STRIDE = TerrainVertexWriter.STRIDE;
	private static final int MIN_ALLOC = 16000;

	public final RegionVertexBuffer vertexBuffer;

	public long offset;
	public long capacity;

	private Allocation firstEntry;
	private Allocation lastEntry;
	private Allocation freeAllocations;

	public static final int SPARE_BUFFER_ALLOC = 1024 * 1024 * 24;
	public static RegionVertexBuffer SPARE_BUFFER;

	public RegionAllocation() {
		this(MIN_ALLOC);
	}

	public RegionAllocation(int size) {
		int newCapacity = Math.max(MIN_ALLOC, size);

		if (SPARE_BUFFER == null) {
			SPARE_BUFFER = new RegionVertexBuffer(SPARE_BUFFER_ALLOC);
		}

		this.vertexBuffer = new RegionVertexBuffer(newCapacity);
		this.capacity = newCapacity;
	}

	public void resize(long size) {
		if (SPARE_BUFFER == null) {
			return;
		}

		long newSize = Math.max((this.capacity * 3) >>> 1, size);

		if (newSize > SPARE_BUFFER_ALLOC && size <= SPARE_BUFFER_ALLOC) {
			newSize = SPARE_BUFFER_ALLOC;
		}

		SectionManager.getCurrentInstance().removeMemory(this.capacity);

		if (newSize > SPARE_BUFFER_ALLOC) {
			this.vertexBuffer.allocateSpace(newSize);

			Allocation alloc = this.firstEntry;

			while (alloc != null) {
				alloc.render.flags = SectionFlags.setDirty(alloc.render.flags, true);
				alloc = alloc.next;
			}

			this.capacity = newSize;

			return;
		}

		GL45.glCopyNamedBufferSubData(this.vertexBuffer.vboId, SPARE_BUFFER.vboId, 0, 0, this.offset);
		this.vertexBuffer.allocateSpace(newSize);
		GL45.glCopyNamedBufferSubData(SPARE_BUFFER.vboId, this.vertexBuffer.vboId, 0, 0, this.offset);

		this.capacity = newSize;
	}

	// Returns first << 32 | count.
	public long allocate(SectionRender render, ByteBuffer vertexData, int size, int side) {
		Allocation alloc = this.fitInFree(size);

		if (alloc == null || alloc.size < size) {
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

		SectionManager.getCurrentInstance().addUsedMemory(size * TerrainVertexWriter.STRIDE);

		if (this.capacity < this.offset + sizeInBytes) {
			this.resize(this.offset + sizeInBytes);
		}

		Allocation newAlloc = new Allocation(render, maxOffset, size, side);
		this.offset += sizeInBytes;

		if (this.firstEntry == null) {
			return this.lastEntry = this.firstEntry = newAlloc;
		}

		return this.lastEntry = this.lastEntry.next = newAlloc;
	}

	public long renewAllocation(SectionRender render, ByteBuffer data, int size, int side) {
		Allocation alloc = this.findRenderAlloc(render, side);
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

	private @Nullable Allocation fitInFree(int spaceNeeded) {
		Allocation alloc = this.freeAllocations;

		if (alloc == null) {
			return null;
		}

		if (alloc.size >= spaceNeeded) {
			this.freeAllocations = this.freeAllocations.next;
			alloc.next = null;
			return alloc;
		}

		while (alloc.next != null && alloc.next.size < spaceNeeded) {
			alloc = alloc.next;
		}

		Allocation returnAlloc = null;

		if (alloc.next != null) {
			returnAlloc = alloc.next;
			alloc.next = alloc.next.next;
		}

		return returnAlloc;
	}

	public @Nullable Allocation findRenderAlloc(SectionRender render, int side) {
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

	public void remove(SectionRender render) {
		Allocation alloc = this.firstEntry;

		if (alloc.render == render) {
			alloc.render = null;
			alloc.side = -1;
			this.firstEntry = alloc.next;
			return;
		}

		while (alloc.next != null && alloc.next.render != render) {
			alloc = alloc.next;
		}

		if (alloc.next == null) {
			return;
		}

		Allocation renderAlloc = alloc.next;
		renderAlloc.render = null;
		renderAlloc.side = -1;
		renderAlloc.next = null;

		if (this.freeAllocations == null) {
			this.freeAllocations = renderAlloc;
		} else {
			Allocation lastAlloc = this.freeAllocations;

			while (lastAlloc.next != null) {
				lastAlloc = lastAlloc.next;
			}

			lastAlloc.next = renderAlloc;
		}

		alloc.next = renderAlloc.next;
	}

	public void uploadAllocation(Allocation alloc, ByteBuffer data, int size) {
		this.vertexBuffer.upload(data, alloc.offset * STRIDE, size * STRIDE);
	}

	public class Allocation {
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
