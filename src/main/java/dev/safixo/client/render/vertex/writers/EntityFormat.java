package dev.safixo.client.render.vertex.writers;

import com.google.common.collect.ImmutableList;
import dev.safixo.client.render.gfx.vertex.GlVertexFormat;
import dev.safixo.client.render.gfx.vertex.attribute.GlVertexAttribute;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.memory.UnsafeUtil;

public class EntityFormat extends GlVertexFormat {
	public EntityFormat(ImmutableList<GlVertexAttribute> vertexProperties) {
		super(vertexProperties);
	}

	@Override
	public void writeVertex(long ptr, int offset) {
		VertexWriter writer = VertexWriter.getCurrentInstance();

		UnsafeUtil.memPutFloat(ptr + 0, (float) writer.x);
		UnsafeUtil.memPutFloat(ptr + 4, (float) writer.y);
		UnsafeUtil.memPutFloat(ptr + 8, (float) writer.z);
		UnsafeUtil.memPutFloat(ptr + 12, (float) writer.u);
		UnsafeUtil.memPutFloat(ptr + 16, (float) writer.v);
		UnsafeUtil.memPutInt(ptr + 20, writer.normal);
	}
}
