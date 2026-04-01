package dev.safixo.client.render.gfx.vertex;

import com.google.common.collect.ImmutableList;
import dev.safixo.client.render.gfx.vertex.attribute.GlVertexAttribute;
import dev.safixo.client.render.vertex.DefaultVertexFormats;

public abstract class GlVertexFormat {
	private final ImmutableList<GlVertexAttribute> vertexProperties;
	private final int stride;

	public GlVertexFormat(ImmutableList<GlVertexAttribute> vertexProperties) {
		int stride = 0;

		for (int i = 0; i < vertexProperties.size(); i++) {
			GlVertexAttribute vertexProperty = vertexProperties.get(i);
			stride += vertexProperty.getTotalSize();
		}

		// align to 4-bytes.
		if ((stride & 3) > 0) {
			stride += 4 - (stride & 3);
		}

		this.stride = stride;
		this.vertexProperties = vertexProperties;
	}

	public abstract void writeVertex(long ptr, int index);

	public void setupBufferState() {
		int offset = 0;
		int index = 0;

		for (GlVertexAttribute vertexProperty : this.vertexProperties) {
			vertexProperty.setupAttribute(index++, this.stride, offset);
			offset += vertexProperty.getTotalSize();
		}
	}

	public void cleanupBufferState() {
		int index = 0;

		for (GlVertexAttribute vertexProperty : this.vertexProperties) {
			vertexProperty.disableAttribute(index++);
		}
	}

	public int getStride() {
		return this.stride;
	}
}
