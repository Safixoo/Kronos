package turniplabs.examplemod.client;

import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.vertex.writer.TerrainVertexWriter;

import static org.lwjgl.opengl.GL45.*;

public class CopyBufferWriter {
	private final int handle;

	private long ptr;
	private int capacity;
	private int offset;

	private int vertices;

	private static final int MIN_ALLOC = 1024 * 1024 * 16; //
	private static final int FLAGS = GL_MAP_READ_BIT | GL_MAP_WRITE_BIT; //

	public CopyBufferWriter() {
		this.handle = glGenBuffers();
		this.allocate(MIN_ALLOC);
	}

	public void allocate(int size) {
		glBindBuffer(GL_ARRAY_BUFFER, this.handle);
		glBufferData(GL_ARRAY_BUFFER, Math.max(MIN_ALLOC, size), GL_STREAM_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	public long start() {
		return this.ptr = nglMapBufferRange(GL_ARRAY_BUFFER, 0, this.capacity, FLAGS);
	}

	public int getVertices() {
		return this.vertices;
	}

	public void copyTo(int vboHandle, int offset) {
		glCopyNamedBufferSubData(this.handle, vboHandle, 0, offset, this.offset);
	}

	public void restart() {
		this.vertices = 0;
		this.offset = 0;
	}

	public void end() {
		glBindBuffer(GL_ARRAY_BUFFER, this.handle);
		glUnmapBuffer(GL_ARRAY_BUFFER);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		this.restart();
	}

	public void addTerrainVertex(float x, float y, float z, float u, float v, int color, int lightmap) {
		long ptrOff = this.ptr + this.offset;

		if (ptrOff >= this.capacity) {
			throw new RuntimeException("TODO: Implement resizing");
		}

		MemoryUtil.memPutFloat(ptrOff + 0, x);
		MemoryUtil.memPutFloat(ptrOff + 4, y);
		MemoryUtil.memPutFloat(ptrOff + 8, z);

		MemoryUtil.memPutFloat(ptrOff + 12, u);
		MemoryUtil.memPutFloat(ptrOff + 16, v);

		MemoryUtil.memPutFloat(ptrOff + 20, color);
		MemoryUtil.memPutFloat(ptrOff + 24, lightmap);

		this.offset += TerrainVertexWriter.STRIDE;
		this.vertices++;
	}

}
