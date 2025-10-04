package dev.safixo.client.render.vertex.format;

import com.google.common.collect.ImmutableList;
import dev.safixo.client.render.vertex.operations.VertexAttribute;

public class VertexFormat {
	private final ImmutableList<VertexAttribute> vertexProperties;
	private final int stride;

	public VertexFormat(ImmutableList<VertexAttribute> vertexProperties) {
		int stride = 0;

		for (int i = 0; i < vertexProperties.size(); i++) {
			VertexAttribute vertexProperty = vertexProperties.get(i);
			stride += vertexProperty.getTotalSize();
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
		int index = 0;

		for (VertexAttribute vertexProperty : this.vertexProperties) {
			vertexProperty.setupAttribute(index++, this.stride, offset);
			offset += vertexProperty.getTotalSize();
		}
	}

	public void cleanupBufferState() {
		int index = 0;

		for (VertexAttribute vertexProperty : this.vertexProperties) {
			vertexProperty.disableAttribute(index++);
		}
	}

	public int getStride() {
		return this.stride;
	}
}
