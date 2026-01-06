package dev.safixo.client.render.vertex.writers;

import com.google.common.collect.ImmutableList;
import dev.safixo.client.render.gfx.vertex.GlVertexFormat;
import dev.safixo.client.render.gfx.vertex.attribute.GlVertexAttribute;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.memory.UnsafeUtil;

public class EntityFormat extends GlVertexFormat {
	public EntityFormat(ImmutableList<GlVertexAttribute> vertexProperties) {
		super(vertexProperties);
	}

	@Override
	public void writeVertex(long ptr, int offset) {
		VertexWriter writer = VertexWriter.getCurrentInstance();

		UnsafeUtil.memPutFloat(ptr, writer.x);
		UnsafeUtil.memPutFloat(ptr + 4, writer.y);
		UnsafeUtil.memPutFloat(ptr + 8, writer.z);

		UnsafeUtil.memPutFloat(ptr + 12, writer.u);
		UnsafeUtil.memPutFloat(ptr + 16, writer.v);

		UnsafeUtil.memPutInt(ptr + 20, writer.normal);
	}
}
