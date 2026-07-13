package dev.safixo.client.render.vertex;

import dev.safixo.client.render.gfx.vertex.GlVertexFormat;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.client.util.MeshDirection;

import java.nio.ByteBuffer;

public class VertexWriter {
	public static final VertexWriter GLOBAL = new VertexWriter();
	private static final int DEFAULT_CAPACITY = 4096;

	public static final VertexWriter[] SOLID = new VertexWriter[MeshDirection.COUNT];
	public static VertexWriter TRANSLUCENT = new VertexWriter();

	public float x, y, z;
	public float trasX, trasY, trasZ;
	public float u, v;
	public int color, lightMap, normal;
	public boolean disableColor;

	private long capacity;
	private long vertexPtr;
	private ByteBuffer vertexPtrNio;

	private GlVertexFormat vertexFormat;

	public int offset;
	public int vertices;

	private static final ThreadLocal<VertexWriter> threadWriter = new ThreadLocal<>();

	public VertexWriter() {
		this(DEFAULT_CAPACITY);
	}

	public VertexWriter(int capacity) {
		this.capacity = capacity;
		this.vertexPtr = NativeBuffer.nmemAlloc(capacity);
		this.vertexPtrNio = NativeBuffer.wrap(this.vertexPtr);
	}

	public static void startDefaults() {
		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			SOLID[dir] = new VertexWriter();
		}
		TRANSLUCENT = new VertexWriter();
	}

	public static VertexWriter getCurrentInstance() {
		return threadWriter.get();
	}

	public static void setCurrentInstance(VertexWriter manager) {
		threadWriter.set(manager);
	}

	public void startDrawing() {
		this.offset = 0;
		this.vertices = 0;
	}

	public VertexWriter copy() {
		VertexWriter writer = new VertexWriter(this.offset);

		UnsafeUtil.memCopy(this.vertexPtr, writer.vertexPtr, this.offset);
		writer.offset = this.offset;
		writer.vertices = this.vertices;
		writer.vertexFormat = this.vertexFormat;

		return writer;
	}

	public void ensureCapacity(int offset) {
		if (this.offset + offset >= this.capacity) {
			this.grow(this.offset + offset * 2L);
		}
	}

	public void addVertexCounter() {
		this.offset += this.vertexFormat.getStride();
		this.vertices++;
	}

	public void addVertexCounter(int stride) {
		this.offset += stride;
		this.vertices++;
	}

	public void addVertex() {
		int stride = this.vertexFormat.getStride();

		this.ensureCapacity(stride);
		this.vertexFormat.writeVertex(this, this.vertexPtr + this.offset, this.vertices);

		this.offset += stride;
		this.vertices++;
	}

	public void stopDrawing() {
		this.offset = 0;
		this.vertices = 0;
	}

	public void delete() {
		if (this.vertexPtr != UnsafeUtil.NULL){
			NativeBuffer.nmemFree(this.vertexPtr);
			this.vertexPtr = UnsafeUtil.NULL;
		}

		this.vertexPtrNio = null;
		this.offset = 0;
		this.vertices = 0;
		this.capacity = 0;
	}

	private void grow(long minSize) {
		long newCapacity = Math.max(this.capacity * 2, minSize);

		long newVertexPtr = NativeBuffer.nmemAlloc(newCapacity);
		UnsafeUtil.memCopy(this.vertexPtr, newVertexPtr, this.offset);
		NativeBuffer.nmemFree(this.vertexPtr);

		this.capacity = newCapacity;
		this.vertexPtr = newVertexPtr;
		this.vertexPtrNio = NativeBuffer.wrap(newVertexPtr);
	}

	public long getTotalOffset() {
		return this.vertexPtr + this.offset;
	}

	public int getOffset() {
		return this.offset;
	}

	public long getWriterPtr() {
		return this.vertexPtr;
	}

	public ByteBuffer getWriterNio() {
		return this.vertexPtrNio;
	}

	public int getVertices() {
		return this.vertices;
	}

	public void setVertexFormat(GlVertexFormat vertexFormat) {
		this.vertexFormat = vertexFormat;
	}
}
