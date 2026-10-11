package dev.safixo.client.util.memory;

import dev.safixo.client.render.gfx.util.RingBuffer;

import java.nio.ByteBuffer;
import java.util.*;

public class MemoryPool {
	private final long ptr;
	private final ByteBuffer ptrNio;
	private final RingBuffer ringBuffer;

	private final ArrayList<RingBuffer.PoolAllocation> allocHolder = new ArrayList<>();

	public MemoryPool(int size) {
		this.ringBuffer = new RingBuffer(size);
		this.ptr = NativeBuffer.nmemAlloc(size);
		this.ptrNio = NativeBuffer.wrap(this.ptr);
	}

	public MemoryReference allocate(int size) {
		RingBuffer.PoolAllocation alloc = this.ringBuffer.allocate(size);
		return new MemoryReference(alloc, this.ptrNio, this.ptr, alloc.offset, size);
	}

	public void returnRefs(MemoryReference[] refs) {
		if (refs.length == 0) {
			return;
		}

		for (int i = 0; i < refs.length; i++) {
			this.allocHolder.add(refs[i].poolAllocation);
		}

		this.ringBuffer.returnAllocations(this.allocHolder.toArray(new RingBuffer.PoolAllocation[0]));
		this.allocHolder.clear();
	}
}
