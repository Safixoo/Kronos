package dev.safixo.client.render.vertex;

import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.render.gfx.vertex.GlVertexFormat;
import dev.safixo.client.render.pipelines.terrain.meshing.data.Quad;
import dev.safixo.client.util.memory.MemoryReference;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.client.util.MeshDirection;

import java.nio.ByteBuffer;

public class VertexWriter {
	public static final VertexWriter GLOBAL = new VertexWriter();
	private static final int DEFAULT_CAPACITY = 4096;

	public static final VertexWriter[] SOLID = new VertexWriter[MeshDirection.COUNT];
	public static VertexWriter TRANSLUCENT = new VertexWriter();

	public double dx, dy, dz;
	public float u, v;
	public int color, light, normal;

	private long ptr, capacity;
	private ByteBuffer vertexPtrNio;

	private final Quad currentQuad;

	private IQuadReceiver quadReceiver;

	public int vertices, offset;
	private boolean fromPool;

	private static final ThreadLocal<VertexWriter> WRITER_PER_THREAD = new ThreadLocal<>();

	public VertexWriter(MemoryReference reference) {
		this.fromPool = true;

		this.ptr = reference.ptr;
		this.capacity = reference.size;
		this.vertexPtrNio = reference.ptrNio;
		this.currentQuad = null;
	}

	public VertexWriter() {
		this(DEFAULT_CAPACITY);
	}

	public VertexWriter(int capacity) {
		this.capacity = capacity;
		this.ptr = NativeBuffer.nmemAlloc(capacity);
		this.vertexPtrNio = NativeBuffer.wrap(this.ptr);
		this.currentQuad = new Quad();
	}

	public static void startDefaults() {
		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			SOLID[dir] = new VertexWriter();
		}
		TRANSLUCENT = new VertexWriter();
	}

	public static VertexWriter getCurrentInstance() {
		return WRITER_PER_THREAD.get();
	}

	public static void setCurrentInstance(VertexWriter manager) {
		WRITER_PER_THREAD.set(manager);
		ImprovedTessellator.getTessellator().setCurrentVertexWriter(manager);
	}

	public void reset() {
		this.offset = 0;
		this.vertices = 0;
	}

	public VertexWriter copy() {
		VertexWriter writer = new VertexWriter(this.offset);

		UnsafeUtil.memCopy(this.ptr, writer.ptr, this.offset);
		writer.offset = this.offset;
		writer.vertices = this.vertices;
		writer.quadReceiver = this.quadReceiver;

		return writer;
	}

	public VertexWriter copy(MemoryReference reference) {
		VertexWriter writer = new VertexWriter(reference);

		UnsafeUtil.memCopy(this.ptr, writer.ptr, this.offset);
		writer.vertices = this.vertices;
		writer.offset = this.offset;
		writer.quadReceiver = this.quadReceiver;

		return writer;
	}

	public void ensureCapacity(int offset) {
		if (this.fromPool) {
			throw new RuntimeException("Overflowed buffer!");
		}

		if (this.offset + offset > this.capacity) {
			this.grow(this.offset + offset * 2L);
		}
	}

	public void addVertex(float x, float y, float z, boolean immediateWrite) {
		if (this.quadReceiver == null) {
			return;
		}

		int vertIndex = this.vertices & 3;

		this.currentQuad.setX(vertIndex, (float) (x + this.dx));
		this.currentQuad.setY(vertIndex, (float) (y + this.dy));
		this.currentQuad.setZ(vertIndex, (float) (z + this.dz));

		this.currentQuad.setU(vertIndex, this.u);
		this.currentQuad.setV(vertIndex, this.v);

		this.currentQuad.setColor(vertIndex, this.color);
		this.currentQuad.setLight(vertIndex, this.light);
		this.currentQuad.setNormal(vertIndex, this.normal);

		this.vertices++;

		if ((this.vertices & 3) == 0) {
			this.currentQuad.computeNormal();
			if (immediateWrite) {
				this.bufferQuad();
			}
		}
	}

	public void bufferQuad() {
		if ((this.vertices & 3) == 0) {
			this.writeQuad(this.currentQuad);
			this.currentQuad.reset();
		}
	}

	public Quad getCurrentQuad() {
		return this.currentQuad;
	}

	public void writeQuad(Quad quad) {
		int stride = this.quadReceiver.getStride();
		this.ensureCapacity(stride * 4);

		int offset = this.quadReceiver.writeQuad(quad, this.ptr + this.offset);

		if (offset == 0) {
			this.vertices -= 4;
		} else {
			this.offset += offset;
		}
	}

	public void delete() {
		if (!this.fromPool && this.ptr != UnsafeUtil.NULL){
			NativeBuffer.nmemFree(this.ptr);
			this.ptr = UnsafeUtil.NULL;
		}
		if (this.currentQuad != null) {
			this.currentQuad.delete();
		}

		this.vertexPtrNio = null;
		this.offset = 0;
		this.vertices = 0;
		this.capacity = 0;
	}

	private void grow(long minSize) {
		long newCapacity = Math.max(this.capacity * 2, minSize);

		long newVertexPtr = NativeBuffer.nmemAlloc(newCapacity);
		UnsafeUtil.memCopy(this.ptr, newVertexPtr, this.offset);
		NativeBuffer.nmemFree(this.ptr);

		this.capacity = newCapacity;
		this.ptr = newVertexPtr;
		this.vertexPtrNio = NativeBuffer.wrap(newVertexPtr);
	}

	public int getOffset() {
		return this.offset;
	}

	public long getPtr() {
		return this.ptr;
	}

	public ByteBuffer getNioPtr() {
		return this.vertexPtrNio;
	}

	public int getVertices() {
		return this.vertices;
	}

	public void setQuadReceiver(IQuadReceiver quadReceiver) {
		this.quadReceiver = quadReceiver;
	}
}
