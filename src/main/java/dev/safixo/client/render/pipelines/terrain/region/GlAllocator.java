package dev.safixo.client.render.pipelines.terrain.region;

import dev.safixo.client.render.gfx.buffer.GlBuffer;
import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.util.GlBufferUtil;
import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.render.pipelines.terrain.sorting.IndexBufferWriter;
import dev.safixo.client.render.vertex.writers.TerrainFormat;
import dev.safixo.client.util.memory.UnsafeUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.lwjgl.opengl.*;

public class GlAllocator<T extends GlBuffer> {
	private static final int MIN_ALLOC = 4 * 1024 * 1024;

	private static final int MAX_INCREMENT = 10 * 1024 * 1024;
	private static final int MIN_INCREMENT = 4 * 1024 * 1024;

	private static final int INTS_PER_ENTRY = 3;

	private static final int SIZE_POS = 0;
	private static final int ID_POS_LSB = 1;
	private static final int ID_POS_MSB = 2;

	private final IntArrayList entries = new IntArrayList(64);
	private final LongArrayList freeEntries = new LongArrayList();

	private int freeCount;
	private int frontPos;

	public long capacity, offset;

	private final GlBuffer spareBuffer;
	private final T allocatedBuffer;

	private int allocationSum;

	public GlAllocator(T buffer, GlBuffer spareBuffer, int size, boolean useMinAlloc) {
		size = useMinAlloc ? Math.max(size, MIN_ALLOC) : size;
		buffer.bufferData(UnsafeUtil.NULL, 0, size);

		this.allocatedBuffer = buffer;
		this.spareBuffer = spareBuffer;
		this.capacity = size;
	}

	private long getAmplification(long minSize) {
		return Math.max(minSize + MIN_INCREMENT, Math.min(this.capacity + MAX_INCREMENT, this.capacity << 1));
	}

	public int getAllocationSum() {
		return this.allocationSum;
	}

	public int getCapacity() {
		return (int) this.capacity;
	}

	public T getBuffer() {
		return this.allocatedBuffer;
	}

	private void resize(long minSize) {
		if (this.spareBuffer == null) {
			return;
		}

		long newSize = getAmplification(minSize);

		// If there's a possibility of avoiding using a temp-buffer for the geometry
		// it's preferable.
		if (newSize > this.spareBuffer.getCapacity() && minSize <= this.spareBuffer.getCapacity()) {
			newSize = this.spareBuffer.getCapacity();
		}

		T regionBuffer = this.allocatedBuffer;

		if (newSize > this.spareBuffer.getCapacity()) {
			// Allocate a temporal buffer to hold region memory.
			GlBuffer spareBuffer = new GlVertexBuffer((int) this.offset, GL15.GL_STATIC_COPY);

			GlBufferUtil.copyBufferToBuffer(regionBuffer, spareBuffer, 0, 0, (int) this.offset);
			this.allocatedBuffer.bufferData(UnsafeUtil.NULL, 0, (int) newSize);
			GlBufferUtil.copyBufferToBuffer(spareBuffer, regionBuffer, 0, 0, (int) this.offset);

			spareBuffer.delete();
			this.capacity = newSize;
		} else {
			GlBuffer spareBuffer = this.spareBuffer;

			GlBufferUtil.copyBufferToBuffer(regionBuffer, spareBuffer, 0, 0, (int) this.offset);
			this.allocatedBuffer.bufferData(UnsafeUtil.NULL, 0, (int) newSize);
			GlBufferUtil.copyBufferToBuffer(spareBuffer, regionBuffer, 0, 0, (int) this.offset);

			this.capacity = newSize;
		}
	}

	public void delete() {
		this.allocatedBuffer.delete();
		this.capacity = 0;
		this.offset = 0;
		this.allocationSum = 0;
	}

	public boolean isEmpty() {
		return this.entries.isEmpty();
	}

	private int getSizeFromIndex(int index) {
		return this.entries.getInt(index * INTS_PER_ENTRY + SIZE_POS);
	}

	private long getIdFromIndex(int index) {
		return (this.entries.getInt(index * INTS_PER_ENTRY + ID_POS_LSB) & 0xFFFFFFFFL) |
			(this.entries.getInt(index * INTS_PER_ENTRY + ID_POS_MSB) & 0xFFFFFFFFL) << 32L;
	}

	private int getOffsetFromAlloc(long allocation) {
		return (int) (allocation >>> 32L);
	}

	private int getIndexFromAlloc(long allocation) {
		return (int) (allocation & 0xFFFFFFFFL);
	}

	private void setAllocationId(int allocIndex, long id) {
		int lsbId = (int) (id & 0xFFFFFFFFL);
		int msbId = (int) (id >>> 32L);

		this.entries.set(allocIndex * INTS_PER_ENTRY + ID_POS_LSB, lsbId);
		this.entries.set(allocIndex * INTS_PER_ENTRY + ID_POS_MSB, msbId);
	}

	private static long getAllocationPacked(int allocIndex, int offset) {
		return (allocIndex & 0xFFFFFFFFL) | (offset & 0xFFFFFFFFL) << 32L;
	}

	private long instanceAllocation(long id, int offset, int size) {
		int index = this.frontPos++;

		this.entries.add(size);
		this.entries.add((int) (id & 0xFFFF_FFFFL));
		this.entries.add((int) (id >>> 32L));

		return getAllocationPacked(index, offset);
	}

	private long createAllocation(long id, int size) {
		long allocation = this.fitInFree(size);

		if (allocation == -1) {
			allocation = this.addAllocation(id, size);
		}

		int allocIndex = this.getIndexFromAlloc(allocation);

		this.setAllocationId(allocIndex, id);
		this.allocationSum += this.getSizeFromIndex(allocIndex);

		return packAllocation(size, this.getOffsetFromAlloc(allocation));
	}

	private long addAllocation(long id, int size) {
		if (this.offset + size > this.capacity) {
			this.resize(this.offset + size);
		}

		int offset = (int) this.offset;
		this.offset += size;

		return this.instanceAllocation(id, offset, size);
	}

	public long allocate(long id, int size) {
		long allocation = this.findPrevAlloc(id);
		int allocIndex = this.getIndexFromAlloc(allocation);
		long allocData;

		if (allocIndex != -1 && this.getSizeFromIndex(allocIndex) >= size) {
			allocData = packAllocation(size, this.getOffsetFromAlloc(allocation));
		} else {
			if (allocIndex != -1) {
				this.remove(id);
			}
			allocData = this.createAllocation(id, size);
		}

		return allocData;
	}

	// Searches for a space in allocations already freed.
	private long fitInFree(int spaceNeeded) {
		if (this.freeCount == 0) {
			return -1;
		}

		int minSpace = Integer.MAX_VALUE;
		int bestIndex = -1;

		for (int i = 0; i < this.freeEntries.size(); i++) {
			long allocation = this.freeEntries.getLong(i);
			int freeAllocIndex = this.getIndexFromAlloc(allocation);

			int size = this.getSizeFromIndex(freeAllocIndex);

			if (size < minSpace && size >= spaceNeeded) {
				bestIndex = i;
				minSpace = size;
			}
		}

		if (bestIndex != -1) {
			this.freeCount--;
			return this.freeEntries.removeLong(bestIndex);
		}

		return -1;
	}

	// Searches for a previous allocation.
	public long findPrevAlloc(long id) {
		int offset = 0;

		for (int i = 0; i < this.frontPos; i++) {
			if (this.getIdFromIndex(i) == id) {
				return getAllocationPacked(i, offset);
			}

			offset += this.getSizeFromIndex(i);
		}

		return -1;
	}

	public static long packAllocation(int size, int offset) {
		return (offset & 0xFFFFFFFFL) << 32L | (size & 0xFFFFFFFFL);
	}

	public static int unpackOffset(long mask) {
		return (int) (mask >>> 32L);
	}

	public static int unpackSize(long mask) {
		return (int) (mask & 0xFFFFFFFFL);
	}

	// Removes allocation from the main pool, and saves in the free pool.
	public void remove(long id) {
		long allocation = this.findPrevAlloc(id);

		if (allocation == -1L) {
			return;
		}

		this.allocationSum -= this.getSizeFromIndex(this.getIndexFromAlloc(allocation));

		this.freeCount++;
		this.freeEntries.add(allocation);
		this.setAllocationId(this.getIndexFromAlloc(allocation), -1);
	}
}
