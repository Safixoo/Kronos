package dev.safixo.client.render.gfx.buffer;

import dev.safixo.client.render.gfx.util.GpuFlags;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import org.lwjgl.opengl.EXTDirectStateAccess;
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
		this.allocate(UnsafeUtil.NULL, size);
	}

	@Override
	public int getHandle() {
		return this.id;
	}

	@Override
	public int getCapacity() {
		return this.capacity;
	}

	public void allocate(long vertexData, int size) {
		this.bind();

		if (vertexData != UnsafeUtil.NULL) {
			ByteBuffer vertexDataBuffer = NativeBuffer.wrap(vertexData);
			((Buffer) vertexDataBuffer).limit(size);

			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexDataBuffer, this.hint);
		} else {
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, size, this.hint);
		}

		this.capacity = size;
		this.unbind();
	}

	public void bufferSubData(long vertexData, int offset, int size) {
		ByteBuffer vertexDataBuffer = NativeBuffer.wrap(vertexData);
		this.bufferSubData(vertexDataBuffer, offset, size);
	}

	public void bufferData(ByteBuffer buffer, int size) {
		((Buffer) buffer).limit(size);

		this.bind();
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, this.hint);
	}

	public void bufferSubData(ByteBuffer buffer, int offset, int size) {
		((Buffer) buffer).limit(((Buffer) buffer).position() + size);

		this.bind();
		GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, offset, buffer);
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
		this.allocate(UnsafeUtil.NULL, 0);
		this.unbind();
	}

	public void delete() {
		this.clear();

		GL15.glDeleteBuffers(this.id);
		this.id = -1;
	}

	public void bind() {
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.id);
	}

	public void unbind() {
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
	}
}
