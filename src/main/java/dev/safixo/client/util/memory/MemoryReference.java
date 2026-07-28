package dev.safixo.client.util.memory;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class MemoryReference {
	public final long ptr;
	public final ByteBuffer ptrNio;
	public final int size;

	public MemoryReference(ByteBuffer ptrNio, long ptr, int offset, int size) {
		Buffer buffer = ptrNio.duplicate();
		buffer.limit(size + offset);
		buffer.position(offset);

		this.ptrNio = (ByteBuffer) buffer;
		this.ptr = ptr + offset;
		this.size = size;
	}
}
