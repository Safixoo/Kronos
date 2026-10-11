package dev.safixo.client.render.gfx.buffer;

import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class GlVertexBuffer implements GlBuffer {
	private int id;
	private int hint = GL15.GL_STATIC_DRAW;
	private int capacity;

	public GlVertexBuffer(int hint) {
		this.id = GL15.glGenBuffers();
		this.setHint(hint);
	}

	public GlVertexBuffer(int size, int hint) {
		this(hint);
		this.bufferData(UnsafeUtil.NULL, 0, size);
	}

	@Override
	public int getHandle() {
		return this.id;
	}

	@Override
	public int getBufferTarget() {
		return GL15.GL_ARRAY_BUFFER;
	}

	@Override
	public void bufferData(long ptr, int offset, int size) {
		if (ptr == UnsafeUtil.NULL) {
			this.bufferData(null, offset, size);
			return;
		}

		this.bufferData(NativeBuffer.wrap(ptr), offset, size);
	}

	@Override
	public void bufferSubData(long ptr, int offset, int writingOffset, int size) {
		if (ptr == UnsafeUtil.NULL) {
			this.bufferSubData(null, 0, 0, size);
			return;
		}

		this.bufferSubData(NativeBuffer.wrap(ptr), offset, writingOffset, size);
	}

	@Override
	public void bufferData(ByteBuffer buffer, int position, int size) {
		this.bind();

		if (buffer == null) {
			GL15.glBufferData(this.getBufferTarget(), size, this.hint);
		} else {
			((Buffer)buffer).position(position);
			((Buffer)buffer).limit(position + size);
			GL15.glBufferData(this.getBufferTarget(), buffer, this.hint);
		}

		this.capacity = size;
	}

	@Override
	public void bufferSubData(ByteBuffer buffer, int offset, int writingOffset, int size) {
		this.bind();

		if (buffer != null) {
			((Buffer) buffer).position(offset);
			((Buffer) buffer).limit(offset + size);
			GL15.glBufferSubData(this.getBufferTarget(), writingOffset, buffer);
		}
	}

	@Override
	public int getCapacity() {
		return this.capacity;
	}

	public void draw(int vertices, int first) {
		GL11.glDrawArrays(GL11.GL_QUADS, first, vertices);
	}

	public void draw(int drawMode, int vertices, int first) {
		GL11.glDrawArrays(drawMode, first, vertices);
	}

	public void setHint(int hint) {
		this.hint = hint;
	}

	public void clear() {
		this.bind();
		this.bufferData(UnsafeUtil.NULL, 0, 0);
		this.unbind();
	}

	@Override
	public void delete() {
		this.clear();

		GL15.glDeleteBuffers(this.id);
		this.id = -1;
	}

	@Override
	public BufferType getBufferType() {
		return null;
	}

	@Override
	public void bind() {
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.id);
	}

	@Override
	public void unbind() {
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
	}
}
