package dev.safixo.client.render.vertex;

import dev.safixo.client.render.gfx.vertex.GlVertexFormat;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import dev.safixo.client.util.MeshDirection;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class VertexWriter {
	public static final VertexWriter DEFAULT_INSTANCE = new VertexWriter(false);
	private static final int DEFAULT_CAPACITY = (1 << 16);

	public static final ObjectArrayList<VertexWriter> VERTEX_WRITERS = new ObjectArrayList<>();
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

	private int offset, vertices;
	public boolean isDrawing = false;

	private static VertexWriter CURRENT_INSTANCE = DEFAULT_INSTANCE;

	public VertexWriter(int capacity) {
		this(capacity, true);
	}

	public VertexWriter(boolean tracked) {
		this(DEFAULT_CAPACITY, tracked);
	}

	public VertexWriter(int capacity, boolean tracked) {
		if (tracked) {
			VERTEX_WRITERS.add(this);
		}

		this.capacity = capacity;
		this.vertexPtr = NativeBuffer.nmemAlloc(capacity);
		this.vertexPtrNio = NativeBuffer.wrap(this.vertexPtr);
	}

	public VertexWriter() {
		this(DEFAULT_CAPACITY);
	}

	public static VertexWriter getCurrentInstance() {
		return CURRENT_INSTANCE;
	}

	public static boolean isCurrentDrawing() {
		return getCurrentInstance().isDrawing;
	}

	public static void startDefaults() {
		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			SOLID[dir] = new VertexWriter();
		}
		TRANSLUCENT = new VertexWriter();
	}

	public static void setCurrentInstance(VertexWriter manager) {
		CURRENT_INSTANCE = manager;
	}

	public void startDrawing() {
		this.offset = 0;
		this.vertices = 0;
		this.isDrawing = true;
		this.disableColor = false;
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
		this.vertexFormat.writeVertex(this.vertexPtr + this.offset, this.vertices);

		this.offset += stride;
		this.vertices++;
	}

	public void stopDrawing() {
		this.offset = 0;
		this.vertices = 0;
		this.isDrawing = false;
		this.disableColor = false;
	}

	public void clear() {
		NativeBuffer.nmemFree(this.vertexPtr);

		this.vertexPtr = UnsafeUtil.NULL;
		this.vertexPtrNio = null;
		this.offset = 0;
		this.vertices = 0;
	}

	public static void clearBuffers() {
		for (VertexWriter manager : VERTEX_WRITERS) {
			manager.clear();
		}

		VERTEX_WRITERS.clear();

		Arrays.fill(VertexWriter.SOLID, null);
		VertexWriter.TRANSLUCENT = null;
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

	public long getVertexData() {
		return this.vertexPtr;
	}

	public ByteBuffer getVertexDataNio() {
		return this.vertexPtrNio;
	}

	public int getVertices() {
		return this.vertices;
	}

	public void setVertexFormat(GlVertexFormat vertexFormat) {
		this.vertexFormat = vertexFormat;
	}
}
