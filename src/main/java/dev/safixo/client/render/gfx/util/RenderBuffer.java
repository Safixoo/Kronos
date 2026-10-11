package dev.safixo.client.render.gfx.util;

import dev.safixo.client.render.gfx.buffer.BufferType;
import dev.safixo.client.render.gfx.buffer.GlBuffer;
import dev.safixo.client.render.gfx.buffer.GlIndexBuffer;
import dev.safixo.client.render.gfx.vertex.GlVertexArrayObject;
import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.render.gfx.vertex.GlVertexFormat;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

// General purpose VBO with VAO.
public class RenderBuffer implements GlBuffer {
	private GlVertexBuffer vertexBuffer;
	private GlIndexBuffer indexBuffer;

	private GlVertexArrayObject vertexArrayObject;

	public RenderBuffer(int size, int hint) {
		this.vertexBuffer = new GlVertexBuffer(size, hint);
		this.vertexArrayObject = new GlVertexArrayObject(DefaultVertexFormats.TERRAIN_FORMAT);
	}

	public RenderBuffer(GlVertexFormat vertexFormat, int size, int hint) {
		this.vertexBuffer = new GlVertexBuffer(size, hint);
		this.vertexArrayObject = new GlVertexArrayObject(vertexFormat);
	}

	public RenderBuffer(GlVertexFormat vertexFormat, int hint) {
		this.vertexBuffer = new GlVertexBuffer(hint);
		this.vertexArrayObject = new GlVertexArrayObject(vertexFormat);
	}

	public void bindState(boolean bind) {
		if (bind) {
			this.vertexArrayObject.bind(this.vertexBuffer);
		} else {
			this.vertexArrayObject.unbind();
		}
	}

	public void clear() {
		this.vertexBuffer.clear();
	}

	public GlVertexBuffer getVertexBuffer() {
		return this.vertexBuffer;
	}

	public int getCapacity() {
		return this.vertexBuffer.getCapacity();
	}

	@Override
	public int getHandle() {
		return this.vertexBuffer.getHandle();
	}

	@Override
	public int getBufferTarget() {
		return GL15.GL_ARRAY_BUFFER;
	}

	@Override
	public void bufferData(long ptr, int offset, int size) {
		this.vertexBuffer.bufferData(ptr, offset, size);
	}

	@Override
	public void bufferSubData(long ptr, int offset, int writingOffset, int size) {
		this.vertexBuffer.bufferSubData(ptr, offset, writingOffset, size);
	}

	@Override
	public void bufferData(ByteBuffer buffer, int offset, int size) {
		this.vertexBuffer.bufferData(buffer, offset, size);
	}

	@Override
	public void bufferSubData(ByteBuffer buffer, int offset, int writingOffset, int size) {
		this.vertexBuffer.bufferSubData(buffer, offset, writingOffset, size);
	}

	@Override
	public void bind() {
		this.bindState(true);
	}

	public void bind(GlIndexBuffer ibo) {
		this.vertexArrayObject.bind(this.vertexBuffer, ibo);
	}

	@Override
	public void unbind() {
		this.vertexArrayObject.unbind();
	}

	@Override
	public void delete() {
		this.vertexArrayObject.delete();
		this.vertexBuffer.delete();

		this.vertexArrayObject = null;
		this.vertexBuffer = null;
	}

	@Override
	public BufferType getBufferType() {
		return null;
	}
}
