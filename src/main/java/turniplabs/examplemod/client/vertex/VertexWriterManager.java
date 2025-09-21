package turniplabs.examplemod.client.vertex;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.vertex.format.VertexAttribute;
import turniplabs.examplemod.client.vertex.format.VertexFormat;

// Remplazar toda la clase con una implementacion de MemoryUtil.
public class VertexWriterManager {
	private static final VertexWriterManager DEFAULT_INSTANCE = new VertexWriterManager();

	public static final ObjectArrayList<VertexWriterManager> VERTEX_WRITERS = new ObjectArrayList<>();
	public static final VertexWriterManager[] SOLID = new VertexWriterManager[Direction.COUNT + 1];
	public static final VertexWriterManager TRANSLUCENT = new VertexWriterManager();

	public float x, y, z;
	public float trasX, trasY, trasZ;
	public float u, v;
	public int color, lightMap, normal;

	private long capacity;
	private long vertexPtr;

	private VertexFormat vertexFormat;

	private int offset, vertices;
	public boolean isDrawing = false;

	static {
		for (int dir = 0; dir < Direction.COUNT + 1; dir++) {
			SOLID[dir] = new VertexWriterManager();
		}
	}

	private static VertexWriterManager CURRENT_INSTANCE;

	public VertexWriterManager(int capacity) {
//		VERTEX_WRITERS.add(this);

		this.capacity = capacity;
		this.vertexPtr = MemoryUtil.nmemAlloc(capacity);
	}

	public VertexWriterManager() {
		this(65536 << 1);
	}

	public static VertexWriterManager getCurrentInstance() {
		if (CURRENT_INSTANCE != null) {
			return CURRENT_INSTANCE;
		}

		return DEFAULT_INSTANCE;
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
		MemoryUtil.nmemFree(this.vertexPtr);

		this.vertexPtr = MemoryUtil.NULL;
		this.offset = 0;
		this.vertices = 0;
	}

	public static void clearBuffers() {
		for (VertexWriterManager manager : VERTEX_WRITERS) {
			manager.clear();
		}

		VERTEX_WRITERS.clear();
	}

	private void grow(long minSize) {
		long newCapacity = Math.max((this.capacity * 3) >> 1, minSize);

		long newVertexPtr = MemoryUtil.nmemAlloc(newCapacity);
		MemoryUtil.memCopy(this.vertexPtr, newVertexPtr, this.offset);
		MemoryUtil.nmemFree(this.vertexPtr);

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
