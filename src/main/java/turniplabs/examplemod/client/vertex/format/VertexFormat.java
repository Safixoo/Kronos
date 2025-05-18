package turniplabs.examplemod.client.vertex.format;

import com.google.common.collect.ImmutableList;
import turniplabs.examplemod.client.util.interfaces.IVertexWriter;

import java.nio.ByteBuffer;

public class VertexFormat {
	private final ImmutableList<VertexAttribute> vertexProperties;
	private final int stride;
	private final IVertexWriter writer;

	public VertexFormat(ImmutableList<VertexAttribute> vertexProperties, IVertexWriter writer) {
		int stride = 0;

		for (int i = 0, size = vertexProperties.size(); i < size; i++) {
			VertexAttribute vertexProperty = vertexProperties.get(i);
			stride += vertexProperty.getAmount() * vertexProperty.getSize();
		}

		// align to 4-bytes.
		if ((stride & 3) > 0) {
			stride += 4 - (stride & 3);
		}

		this.stride = stride;
		this.writer = writer;
		this.vertexProperties = vertexProperties;
	}

	public void writeVertex(ByteBuffer data, int index) {
		this.writer.writeVertex(data, index);
	}

	public void writeVertex(long buffer, long offset) {
		this.writer.writeVertex(buffer, offset);
	}

	public void setupBufferState() {
		int offset = 0;

		for (VertexAttribute vertexProperty : this.vertexProperties) {
			vertexProperty.setupAttribute(vertexProperty.getAmount(), vertexProperty.getType(), this.stride, offset);

			offset += vertexProperty.attributeStride();
		}
	}

	public void cleanupBufferState() {
		for (VertexAttribute vertexProperty : this.vertexProperties) {
			vertexProperty.getOperation().cleanupBufferState();
		}
	}

	public int getStride() {
		return this.stride;
	}
}
