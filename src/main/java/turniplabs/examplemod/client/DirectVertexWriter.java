package turniplabs.examplemod.client;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.vertex.format.VertexFormat;

// Simple performant "allocator manager" for multi-thread vertex allocations.
public class DirectVertexWriter {
	public VertexFormat format;
	private long offset;
	private long pointer;

	private static final ThreadLocal<DirectVertexWriter> localWriter = new ThreadLocal<>();

	public DirectVertexWriter(int size) {
		this.allocate(size);
	}

	public DirectVertexWriter() {
		this(24 * 96);
	}

	public void sumVertex() {
		this.offset += this.format.getStride();
	}

	public void start(VertexFormat format) {
		this.format = format;
	}

	public static DirectVertexWriter getWriter() {
		DirectVertexWriter writer = localWriter.get();

		if (writer == null) {
			localWriter.set(new DirectVertexWriter());
		}

		return writer;
	}

	public static void stopWriter() {
		DirectVertexWriter writer = localWriter.get();

		if (writer == null) {
			return;
		}

		writer.clear();

		localWriter.remove();
	}

	public void clear() {
		MemoryUtil.nmemFree(this.pointer);
	}

	public long getOffset() {
		return this.pointer + this.offset;
	}

	public void allocate(int size) {
		this.pointer = MemoryUtil.nmemAlloc(size);
	}
}
