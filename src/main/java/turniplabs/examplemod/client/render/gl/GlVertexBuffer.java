package turniplabs.examplemod.client.render.gl;

import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.vertex.format.VertexFormat;

import java.nio.ByteBuffer;

public class GlVertexBuffer {
	private final VertexFormat vertexFormat;

	private int vbo;
	private int vao;
	public int vertexCount;
	private boolean buffersReady = true;
	private boolean hasData = false;
	private boolean vaoSetup = false;
	private int lastBufferSize = 0;

	public GlVertexBuffer(VertexFormat vertexFormat) {
		this.vbo = GL15.glGenBuffers();
		this.vao = GL30.glGenVertexArrays();
		this.vertexFormat = vertexFormat;
	}

	public void draw() {
		GL11.glDrawArrays(GL11.GL_QUADS, 0, this.vertexCount);
	}

	public void upload(ByteBuffer buffer, int vertexCount) {
		if (!this.buffersReady)  {
			this.prepareBuffers();
		}

		if (!this.vaoSetup) {
			this.prepareVAO();
		}

		this.vertexCount = vertexCount;

		this.uploadBufferToDevice(buffer);
	}

	private void uploadBufferToDevice(ByteBuffer buffer) {
		this.bind();

		int dataSize = this.vertexCount * this.vertexFormat.getStride();

		GL45.nglNamedBufferData(this.vbo, dataSize, MemoryUtil.memAddress(buffer), GL15.GL_STREAM_DRAW);

		this.lastBufferSize = dataSize;

		this.unbind();
		this.hasData = true;
	}

	public void clearVertexData() {
		GL45.nglNamedBufferData(this.vbo, 0, 0, GL15.GL_STREAM_DRAW);
	}

	public void clear() {
		if (this.vbo >= 0) GL15.glDeleteBuffers(this.vbo);
		if (this.vao >= 0) GL30.glDeleteVertexArrays(this.vao);
		this.hasData = false;
		this.buffersReady = false;
	}

	public void prepareBuffers() {
		this.vbo = GL15.glGenBuffers();
		this.vao = GL30.glGenVertexArrays();
		this.buffersReady = true;
	}

	public boolean buffersPrepared() {
		return this.buffersReady;
	}

	public boolean hasData() {
		return this.hasData;
	}

	private void prepareVAO() {
		this.bindVAO();
		this.bind();

//		GL13.glVertexPointer(3, GL11.GL_FLOAT, 24, 0);
//		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
//
//		GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
//		GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 24, 12);
//		GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
//
//		GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 24, 20);
//		GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);

		this.vertexFormat.setupBufferState();

		this.unbind();
		this.unbindVAO();

//		GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
//		GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
//		GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);

		this.vertexFormat.cleanupBufferState();

		this.vaoSetup = true;
	}

	public void renderVAO() {
		this.bindVAO();
		this.draw();
		this.unbindVAO();
	}

	public void render() {
		this.bind();
		this.vertexFormat.setupBufferState();
		this.draw();
		this.vertexFormat.cleanupBufferState();
		this.unbind();
	}

	public void bind() {
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vbo);
	}

	public void unbind() {
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
	}

	public void bindVAO() {
		GL30.glBindVertexArray(this.vao);
	}

	public void unbindVAO() {
		GL30.glBindVertexArray(0);
	}

	public void uploadAt(ByteBuffer data, int offset) {
		GL45.nglNamedBufferData(this.vbo, data.remaining(), MemoryUtil.memAddress(data) + offset, GL15.GL_STREAM_DRAW);
	}
}
