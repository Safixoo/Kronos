package dev.safixo.client.util.memory;

import dev.safixo.client.render.gfx.util.RingBuffer;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class MemoryReference {
	public final long ptr;
	public final ByteBuffer ptrNio;
	public final int size;

	public RingBuffer.PoolAllocation poolAllocation;

	protected MemoryReference(RingBuffer.PoolAllocation poolAllocation, ByteBuffer ptrNio, long ptr, int offset, int size) {
		Buffer buffer = ptrNio.duplicate();
		buffer.limit(size + offset);
		buffer.position(offset);

		this.ptrNio = (ByteBuffer) buffer;
		this.ptr = ptr + offset;
		this.size = size;
		this.poolAllocation = poolAllocation;
	}
}
