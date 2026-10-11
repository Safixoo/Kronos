package dev.safixo.client.render.gfx.util;

public class RingBuffer {
	private final int capacity;
	private int min, max;

	// To avoid issues by having to maintain make sure all code return refs in the
	// correct order, simply make all refs save the order and sort them back just in case.
	private int currentRefId;

	public RingBuffer(int size) {
		this.capacity = size;

		this.min = 0;
		this.max = 0;
	}

	public PoolAllocation allocate(int size) {
		if (size > this.capacity) {
			return null;
		}

		// if the current front can't allocate enough memory, wrap the pointer around.
		if (this.max + size > this.capacity) {
			// can't even fit in the buffer.
			this.max = 0;
		}

		// if the max is wrapped around before the min, check that we don't overflow over max.
		if (this.max < this.min && this.max + size > this.min) {
			return null;
		}

		int offset = this.max;
		this.max += size;

		return new PoolAllocation(this.currentRefId++, offset, size);
	}

	public void returnAllocations(PoolAllocation[] allocs) {
		PoolAllocation lastAlloc = allocs[0];
		long id = allocs[0].id;

		for (PoolAllocation alloc : allocs) {
			if (alloc.id > id) {
				id = alloc.id;
				lastAlloc = alloc;
			}
		}

		this.min = lastAlloc.offset + lastAlloc.size;
	}

	public void reset() {
		this.min = 0;
		this.max = 0;
	}

	public static class PoolAllocation {
		public final int offset;
		public final int size;
		public final long id;

		public PoolAllocation(long id, int offset, int size) {
			this.id = id;
			this.offset = offset;
			this.size = size;
		}
	}
}
