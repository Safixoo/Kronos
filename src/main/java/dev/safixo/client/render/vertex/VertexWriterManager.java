package dev.safixo.client.render.vertex;

import dev.safixo.client.render.util.UnsafeUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import dev.safixo.client.render.util.MeshDirection;
import dev.safixo.client.render.vertex.format.VertexFormat;

import java.util.Arrays;

public class VertexWriterManager {
	private static final VertexWriterManager DEFAULT_INSTANCE = new VertexWriterManager(false);
	private static final int DEFAULT_CAPACITY = (1 << 16);

	public static final ObjectArrayList<VertexWriterManager> VERTEX_WRITERS = new ObjectArrayList<>();
	public static final VertexWriterManager[] SOLID = new VertexWriterManager[MeshDirection.COUNT];
	public static VertexWriterManager TRANSLUCENT = new VertexWriterManager();

	public double x, y, z;
	public double trasX, trasY, trasZ;
	public double u, v;
	public int color, lightMap, normal;

	private long capacity;
	private long vertexPtr;

	private VertexFormat vertexFormat;

	private int offset, vertices;
	public boolean isDrawing = false;

	private static VertexWriterManager CURRENT_INSTANCE;

	public VertexWriterManager(int capacity) {
		this(capacity, true);
	}

	public VertexWriterManager(boolean tracked) {
		this(DEFAULT_CAPACITY, tracked);
	}

	public VertexWriterManager(int capacity, boolean tracked) {
		if (tracked) {
			VERTEX_WRITERS.add(this);
		}

		this.capacity = capacity;
		this.vertexPtr = MemoryUtil.nmemAlloc(capacity);
	}

	public VertexWriterManager() {
		this(DEFAULT_CAPACITY);
	}

	public static VertexWriterManager getCurrentInstance() {
		if (CURRENT_INSTANCE != null) {
			return CURRENT_INSTANCE;
		}

		return DEFAULT_INSTANCE;
	}

	public static void startDefaults() {
		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			SOLID[dir] = new VertexWriterManager();
		}
		TRANSLUCENT = new VertexWriterManager();
	}

	public static void setCurrentInstance(VertexWriterManager manager) {
		CURRENT_INSTANCE = manager;
	}

	public void startDrawing() {
		this.offset = 0;
		this.vertices = 0;
		this.isDrawing = true;
	}

	public void ensureCapacity(int offset) {
		if ((this.offset + offset * 2L) >= this.capacity) {
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
	}

	public void clear() {
		UnsafeUtil.nmemFree(this.vertexPtr);

		this.vertexPtr = UnsafeUtil.NULL;
		this.offset = 0;
		this.vertices = 0;
	}

	public static void clearBuffers() {
		for (VertexWriterManager manager : VERTEX_WRITERS) {
			manager.clear();
		}

		VERTEX_WRITERS.clear();

		Arrays.fill(VertexWriterManager.SOLID, null);
		VertexWriterManager.TRANSLUCENT = null;
	}

	private void grow(long minSize) {
		long newCapacity = Math.max((this.capacity * 3) >> 1, minSize);

		long newVertexPtr = UnsafeUtil.nmemAlloc(newCapacity);
		UnsafeUtil.memCopy(this.vertexPtr, newVertexPtr, this.offset);
		UnsafeUtil.nmemFree(this.vertexPtr);

		this.capacity = newCapacity;
		this.vertexPtr = newVertexPtr;
	}

	public long getTotalOffset() {
		return this.vertexPtr + this.offset;
	}

	public long getVertexData() {
		return this.vertexPtr;
	}

	public int getVertices() {
		return this.vertices;
	}

	public void setVertexFormat(VertexFormat vertexFormat) {
		this.vertexFormat = vertexFormat;
	}
}
