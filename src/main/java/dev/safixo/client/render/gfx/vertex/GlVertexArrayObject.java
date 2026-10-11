package dev.safixo.client.render.gfx.vertex;

import dev.safixo.client.render.gfx.buffer.GlBuffer;
import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

public class GlVertexArrayObject {
	private final GlVertexFormat vertexFormat;
	private int id = 0x80000000;

	public GlVertexArrayObject(GlVertexFormat format) {
		this.vertexFormat = format;
	}

	private void saveStateInVao(GlBuffer vbo, GlBuffer ibo) {
		this.id = GL30.glGenVertexArrays();

		GL30.glBindVertexArray(this.id);
		if (vbo != null) GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo.getHandle());
		if (ibo != null) GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo.getHandle());

		this.vertexFormat.setupBufferState();

		GL30.glBindVertexArray(0);
		if (vbo != null) GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		if (ibo != null) GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

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

	public int getHandle() {
		return this.id;
	}


	public void bind(GlBuffer vbo) {
		this.bind(vbo, null);
	}

	public void bind(GlBuffer vbo, GlBuffer ibo) {
		if ((vbo == null || this.vertexFormat == null) && this.id == 0x80000000) {
			this.id = GL30.glGenVertexArrays();
		}

		if (this.id == 0x80000000) {
			this.saveStateInVao(vbo, ibo);
		}

		GL30.glBindVertexArray(this.id);
	}

	public void unbind() {
		GL30.glBindVertexArray(0);
	}
}
