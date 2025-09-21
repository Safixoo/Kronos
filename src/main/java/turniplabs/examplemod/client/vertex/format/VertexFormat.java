package turniplabs.examplemod.client.vertex.format;

import com.google.common.collect.ImmutableList;

public class VertexFormat {
	private final ImmutableList<VertexAttribute> vertexProperties;
	private final int stride;

	public VertexFormat(ImmutableList<VertexAttribute> vertexProperties) {
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
		this.vertexProperties = vertexProperties;
	}

	public void writeVertex(long ptr, int index) {
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
