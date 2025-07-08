package turniplabs.examplemod.client.render.region;

import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.render.SectionRender;
import turniplabs.examplemod.client.vertex.writer.TerrainVertexWriter;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

public class RegionAllocation {
	private static final int STRIDE = TerrainVertexWriter.STRIDE;
	private static final int MIN_ALLOC = 1024 * STRIDE;

	public final RegionVertexBuffer vertexBuffer;

	private int offset;
	private int capacity;

	private Allocation firstEntry;
	private Allocation lastEntry;
	private Allocation freeAllocations;

	public RegionAllocation() {
		this(MIN_ALLOC);
	}

	public RegionAllocation(int size) {
		this.vertexBuffer = new RegionVertexBuffer(Math.max(MIN_ALLOC, size));
		this.capacity = Math.max(MIN_ALLOC, size);
	}

	public void resize() {
		this.vertexBuffer.allocateSpace(this.capacity *= 2);

		Allocation alloc = this.firstEntry;

		while (alloc != null) {
			alloc.render.dirty = true;
			alloc = alloc.next;
		}
	}

	// Returns first << 32 | count.
	public long allocate(SectionRender render, ByteBuffer vertexData, int size) {
		Allocation alloc = this.fitInFree(size);

		if (alloc == null) {
			alloc = this.allocateNew(render, size);
		}

		alloc.render = render;
		this.uploadAllocation(alloc, vertexData, size);

		return packDrawData(size, alloc.offset);
	}

	private Allocation allocateNew(SectionRender render, int size) {
		int maxOffset = this.offset / STRIDE;
		int sizeInBytes = size * STRIDE;

		while (this.capacity <= this.offset + sizeInBytes) {
			this.resize();
		}

		Allocation newAlloc = new Allocation(render, maxOffset, size);
		this.offset += sizeInBytes;

		if (this.firstEntry == null) {
			return this.lastEntry = this.firstEntry = newAlloc;
		}

		return this.lastEntry = this.lastEntry.next = newAlloc;
	}

	public long renewAllocation(SectionRender render, ByteBuffer data, int size) {
		Allocation alloc = this.findRenderAlloc(render);
		long drawData;

		if (alloc.size < size) {
			this.uploadAllocation(alloc, data, size);
			drawData = packDrawData(alloc.size, alloc.offset);
		} else {
			this.remove(render);
			drawData = this.allocate(render, data, size);
		}

		return drawData;
	}

	private @Nullable Allocation fitInFree(int spaceNeeded) {
		Allocation alloc = this.freeAllocations;

		while (alloc != null && alloc.size < spaceNeeded) {
			alloc = alloc.next;
		}

		return alloc;
	}

	public Allocation findRenderAlloc(SectionRender render) {
		Allocation alloc = this.firstEntry;

		while (alloc != null && alloc.render != render) {
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

	public void remove(SectionRender render) {
		Allocation alloc = this.firstEntry;

		if (alloc.render == render) {
			alloc.render = null;
			this.firstEntry = alloc.next;
			return;
		}

		while (alloc.next.render != render) {
			alloc = alloc.next;
		}

		Allocation renderAlloc = alloc.next;

		this.freeAllocations.next = renderAlloc;
		alloc.next = renderAlloc.next;
	}

	public void uploadAllocation(Allocation alloc, ByteBuffer data, int size) {
		this.vertexBuffer.upload(data, alloc.offset * STRIDE, size * STRIDE);
	}

	public class Allocation {
		public Allocation next;
		public SectionRender render;

		public Allocation(SectionRender render, int offset, int size) {
			this.render = render;
			this.offset = offset;
			this.size = size;
		}

		// Size and offset are written in vertex amount and not bytes to
		// avoid division to translate byte sizes to vertex counts.
		public int offset;
		public int size;
	}
}
