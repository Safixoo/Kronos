package dev.safixo.client.render.gfx.util;

import dev.safixo.client.render.gfx.vertex.GlVertexArrayObject;
import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.render.gfx.vertex.GlVertexFormat;

import java.nio.ByteBuffer;

// General purpose VBO with VAO.
public class RenderBuffer {
	private GlVertexBuffer vertexBuffer;
	private GlVertexArrayObject vertexArrayObject;

	public RenderBuffer(int size, int hint) {
		this.vertexBuffer = new GlVertexBuffer(size, hint);
		this.vertexArrayObject = new GlVertexArrayObject(DefaultVertexFormats.TERRAIN_FORMAT);
	}

	public RenderBuffer(GlVertexFormat vertexFormat, int size, int hint) {
		this.vertexBuffer = new GlVertexBuffer(size, hint);
		this.vertexArrayObject = new GlVertexArrayObject(vertexFormat);
	}

	public void bindBuffer(boolean bind) {
		if (bind) {
			this.vertexBuffer.bind();
		} else {
			this.vertexBuffer.unbind();
		}
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

	public void allocateSpace(int size, int hint) {
		this.vertexBuffer.setHint(hint);
		this.vertexBuffer.allocate(UnsafeUtil.NULL, size);
	}

	public void upload(ByteBuffer buffer, int offset, int size) {
		this.vertexBuffer.bufferSubData(buffer, offset, size);
	}

	public GlVertexBuffer getVertexBuffer() {
		return this.vertexBuffer;
	}

	public int getCapacity() {
		return this.vertexBuffer.getCapacity();
	}

	public void delete() {
		this.vertexArrayObject.delete();
		this.vertexBuffer.delete();

		this.vertexArrayObject = null;
		this.vertexBuffer = null;
	}
}
