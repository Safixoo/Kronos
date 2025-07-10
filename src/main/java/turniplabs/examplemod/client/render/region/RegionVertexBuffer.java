package turniplabs.examplemod.client.render.region;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.render.SectionManager;
import turniplabs.examplemod.client.vertex.format.DefaultVertexFormats;
import java.nio.ByteBuffer;

public class RegionVertexBuffer {
	private boolean createdVao;
	public final int vboId;
	public int vaoId;

	private long capacity;

	public RegionVertexBuffer(int size) {
		this.vboId = GL15.glGenBuffers();
		this.allocateSpace(size);
	}

	public void allocateSpace(long space) {
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, space, GL15.GL_STATIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

		SectionManager.getCurrentInstance().addMemory(space);

		this.capacity = space;
	}

	public void upload(ByteBuffer vertexData, long offset, int size) {
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);

		GL20.nglBufferSubData(GL15.GL_ARRAY_BUFFER, offset, size, MemoryUtil.memAddress(vertexData));
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
		GL30.glBufferData(GL15.GL_ARRAY_BUFFER, 0, GL15.GL_STREAM_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

		SectionManager.getCurrentInstance().removeMemory(this.capacity);

		GL30.glDeleteBuffers(this.vboId);
		GL30.glDeleteBuffers(this.vaoId);
	}

	public void unbind() {
		GL30.glBindVertexArray(0);
	}

}
