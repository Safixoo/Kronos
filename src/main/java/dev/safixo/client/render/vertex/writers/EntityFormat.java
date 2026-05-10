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
	public void writeVertex(VertexWriter writer, long ptr, int offset) {
		writeVertex(ptr, writer.x, writer.y, writer.z, writer.u, writer.v, writer.normal);
	}

	public static void writeVertex(long ptr, float x, float y, float z, float u, float v, int normal) {
		UnsafeUtil.memPutFloat(ptr + 0, x);
		UnsafeUtil.memPutFloat(ptr + 4, y);
		UnsafeUtil.memPutFloat(ptr + 8, z);

		UnsafeUtil.memPutFloat(ptr + 12, u);
		UnsafeUtil.memPutFloat(ptr + 16, v);

		UnsafeUtil.memPutInt(ptr + 20, normal);
	}
}
