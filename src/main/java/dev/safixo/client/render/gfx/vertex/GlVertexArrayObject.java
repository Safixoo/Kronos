package dev.safixo.client.render.gfx.vertex;

import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import org.lwjgl.opengl.GL30;

public class GlVertexArrayObject {
	private final GlVertexFormat vertexFormat;
	private int id = 0x80000000;

	public GlVertexArrayObject(GlVertexFormat format) {
		this.vertexFormat = format;
	}

	private void saveStateInVao(GlVertexBuffer vbo) {
		this.id = GL30.glGenVertexArrays();

		this.bind(vbo);
		vbo.bind();

		this.vertexFormat.setupBufferState();

		vbo.unbind();
		this.unbind();

		this.vertexFormat.cleanupBufferState();
	}

	public GlVertexFormat getVertexFormat() {
		return this.vertexFormat;
	}

	public void delete() {
		if (this.id == 0x80000000) {
			return;
		}

		GL30.glDeleteVertexArrays(this.id);
	}

	public void generateHandle() {
		this.id = GL30.glGenVertexArrays();
	}

	public int getHandle() {
		return this.id;
	}

	public void bind(GlVertexBuffer vbo) {
		if (this.id == 0x80000000) {
			this.saveStateInVao(vbo);
		}

		GL30.glBindVertexArray(this.id);
	}

	public void unbind() {
		GL30.glBindVertexArray(0);
	}
}
