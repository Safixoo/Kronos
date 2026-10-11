package dev.safixo.client.render.gfx.buffer;

import java.nio.ByteBuffer;

public interface GlBuffer {
	int getCapacity();
	int getHandle();
	int getBufferTarget();

	void bufferData(long ptr, int offset, int size);
	void bufferSubData(long ptr, int offset, int writingOffset, int size);

	void bufferData(ByteBuffer buffer, int offset, int size);
	void bufferSubData(ByteBuffer buffer, int offset, int writingOffset, int size);

	void bind();
	void unbind();

	void delete();

	BufferType getBufferType();
}
