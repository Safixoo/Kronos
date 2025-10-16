package dev.safixo.client.render.gfx.util;

import dev.safixo.client.render.gfx.vertex.GlVertexArrayObject;
import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.pipelines.terrain.SectionManager;
import dev.safixo.client.render.util.memory.UnsafeUtil;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.render.gfx.vertex.GlVertexFormat;

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

	public void upload(long data, int offset, int size) {
		this.vertexBuffer.upload(data, offset, size);
	}

	public GlVertexBuffer getVertexBuffer() {
		return this.vertexBuffer;
	}

	public int getCapacity() {
		return this.vertexBuffer.getCapacity();
	}

	public void delete() {
		this.clear();

		this.vertexBuffer.delete();
		this.vertexArrayObject.delete();

		this.vertexArrayObject = null;
		this.vertexBuffer = null;
	}
}
