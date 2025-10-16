package dev.safixo.client.render.gfx.buffer;

import dev.safixo.client.render.util.memory.NativeBuffer;
import dev.safixo.client.render.util.memory.UnsafeUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class GlVertexBuffer {
	private int id;
	private int hint = GL15.GL_STATIC_DRAW;
	private int capacity;

	public GlVertexBuffer(int hint) {
		this.id = GL15.glGenBuffers();
	}

	public GlVertexBuffer(int size, int hint) {
		this.id = GL15.glGenBuffers();

		this.setHint(hint);
		this.allocate(UnsafeUtil.NULL, size);
	}

	public int getHandle() {
		return this.id;
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

	public void upload(long vertexData, int offset, int size) {
		this.bind();

		ByteBuffer vertexDataBuffer = NativeBuffer.wrap(vertexData);
		((Buffer) vertexDataBuffer).limit(size);

		GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, offset, vertexDataBuffer);

		this.unbind();
	}

	public void draw(int vertices, int first) {
		GL11.glDrawArrays(GL11.GL_QUADS, first, vertices);
	}

	public void setHint(int hint) {
		this.hint = hint;
	}

	public int getCapacity() {
		return this.capacity;
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
