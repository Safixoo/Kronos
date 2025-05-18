package turniplabs.examplemod.client;

import net.minecraft.client.GLAllocation;
import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.vertex.format.VertexFormat;

import java.nio.ByteBuffer;

// Remplazar toda la clase con una implementacion de MemoryUtil.
public class VertexWriterManager {
	public float x;
	public float y;
	public float z;
	public float transX, transY, transZ;
	public float u, v;
	public int color;
	public int lightMap;
	public int normal;

	private int capacity;

	private ByteBuffer vertexData;
	private VertexFormat vertexFormat;

	private int vertices = 0;
	private boolean isDrawing = false;
	private int offset = 0;

	private static final VertexWriterManager DEFAULT_INSTANCE = new VertexWriterManager();

	public static final VertexWriterManager SOLID = new VertexWriterManager();
	public static final VertexWriterManager TRANSLUCENT = new VertexWriterManager();

	private static VertexWriterManager CURRENT_INSTANCE;

	public VertexWriterManager(int capacity) {
		this.capacity = capacity;
		this.vertexData = GLAllocation.createDirectByteBuffer(this.capacity);
		this.vertexData.limit(this.capacity);
	}

	public VertexWriterManager() {
		this(65536);
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
		this.vertices = 0;
		this.offset = 0;
		this.isDrawing = true;
	}

	public void ensureCapacity(int offset) {
		if (this.offset + offset >= this.capacity) {
			this.grow();
		}
	}

	public void addVertexUnsafe() {
		this.vertexFormat.writeVertex(this.vertexData, this.offset);
		this.vertices++;
		this.offset += this.vertexFormat.getStride();
	}

	public void addVertex() {
		this.ensureCapacity(this.vertexFormat.getStride());

		this.vertexFormat.writeVertex(this.vertexData, this.offset);
		this.vertices++;
		this.offset += this.vertexFormat.getStride();
	}

	public void stopDrawing() {
		this.vertices = 0;
		this.offset = 0;
		this.isDrawing = false;
		this.vertexData.clear();
	}

	public void grow() {
		this.vertexData.position(0);
		int newCapacity = this.capacity * 2;
		ByteBuffer newBuffer = GLAllocation.createDirectByteBuffer(newCapacity);
		newBuffer.put(this.vertexData);
		newBuffer.limit(newCapacity);
		this.vertexData = newBuffer;
		this.capacity = newCapacity;
	}

	// <------------------------->
	// Getters and setters below
	// <------------------------->

	// flag to know for what when pick up data from the tessellator.
	public boolean isDrawing() {
		return this.isDrawing;
	}

	public ByteBuffer getVertexData() {
		this.vertexData.position(0);
		return this.vertexData;
	}

	public int getCapacity() {
		return this.capacity;
	}

	public int getVertices() {
		return this.vertices;
	}

	public void setPos(double x, double y, double z) {
		this.x = (float) x;
		this.y = (float) y;
		this.z = (float) z;
	}

	public void setPos(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public void setTranslation(float transX, float transY, float transZ) {
		this.transX = transX;
		this.transY = transY;
		this.transZ = transZ;
	}

	public void setUv(float u, float v) {
		this.u = u;
		this.v = v;
	}

	public void setUv(double U, double V) {
		this.u = (float) U;
		this.v = (float) V;
	}

	public void setColor(int color) {
		this.color = color;
	}

	public void setNormal(int normal) {
		this.normal = normal;
	}

	public void setLightMap(int lightMap) {
		this.lightMap = lightMap;
	}

	public void setVertexFormat(VertexFormat vertexFormat) {
		this.vertexFormat = vertexFormat;
	}
}
