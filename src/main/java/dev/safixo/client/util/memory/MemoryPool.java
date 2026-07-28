package dev.safixo.client.util.memory;

import java.nio.ByteBuffer;

public class MemoryPool {
	private final int ticksUntilReset;
	private final int capacity;

	private int offset;
	private int ticks;

	private final long ptr;
	private final ByteBuffer ptrNio;

	public MemoryPool(int size, int ticksUntilReset) {
		this.ticksUntilReset = Math.max(1, ticksUntilReset);
		this.capacity = size;

		this.ptr = NativeBuffer.nmemAlloc(size);
		this.ptrNio = NativeBuffer.wrap(this.ptr);
	}

	public MemoryReference allocate(int size) {
		if (this.offset + size >= this.capacity) {
			return null;
		}

		int offset = this.offset;
		this.offset += size;

		return new MemoryReference(this.ptrNio, this.ptr, offset, size);
	}

	public void tick() {
		this.ticks++;

		if (this.ticks == this.ticksUntilReset) {
			this.offset = 0;
			this.ticks = 0;
		}
	}
}
