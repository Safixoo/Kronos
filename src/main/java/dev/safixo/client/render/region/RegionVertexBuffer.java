package dev.safixo.client.render.region;

import dev.safixo.client.render.util.memory.NativeBuffer;
import dev.safixo.client.render.SectionManager;
import dev.safixo.client.render.vertex.format.DefaultVertexFormats;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.nio.*;

import java.nio.ByteBuffer;

public class RegionVertexBuffer {
	private boolean createdVao;
	public final int vboId;
	public int vaoId;

	private long capacity;

	public RegionVertexBuffer(long size) {
		this.vboId = GL15.glGenBuffers();
		this.allocateSpace(size);
	}

	public RegionVertexBuffer(long size, int hint) {
		this.vboId = GL15.glGenBuffers();
		this.allocateSpace(size, hint);
	}

	public void allocateSpace(long space) {
		this.allocateSpace(space, GL15.GL_STREAM_DRAW);
	}

	public void allocateSpace(long space, int hint) {
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, space, hint);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

		SectionManager.getCurrentInstance().addMemory(space);

		this.capacity = space;
	}

	public void upload(long vertexData, long offset, int size) {
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);

		ByteBuffer vertexDataBuff = NativeBuffer.wrap(vertexData);
		((Buffer) vertexDataBuff).limit(size);

		GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, offset, vertexDataBuff);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
	}

	private void prepareVao() {
		this.vaoId = GL30.glGenVertexArrays();

		GL30.glBindVertexArray(this.vaoId);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);

		DefaultVertexFormats.TERRAIN_FORMAT.setupBufferState();

		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL30.glBindVertexArray(0);

		DefaultVertexFormats.TERRAIN_FORMAT.cleanupBufferState();

		this.createdVao = true;
	}

	public void bind() {
		if (!this.createdVao) {
			this.prepareVao();
		}

		GL30.glBindVertexArray(this.vaoId);
	}

	public void clear() {
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 0, GL15.GL_STATIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

		SectionManager.getCurrentInstance().removeMemory(this.capacity);
		this.capacity = 0;

		GL30.glDeleteVertexArrays(this.vaoId);
		GL15.glDeleteBuffers(this.vboId);
	}

	public void unbind() {
		GL30.glBindVertexArray(0);
	}

}
