package dev.safixo.client.render.vertex.writers;

import com.google.common.collect.ImmutableList;
import dev.safixo.client.render.gfx.vertex.GlVertexFormat;
import dev.safixo.client.render.gfx.vertex.attribute.GlVertexAttribute;
import dev.safixo.client.render.pipelines.terrain.meshing.data.Quad;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.memory.UnsafeUtil;

public class PositionColorFormat extends GlVertexFormat {
	public PositionColorFormat(ImmutableList<GlVertexAttribute> vertexProperties) {
		super(vertexProperties);
	}

	@Override
	public int writeQuad(Quad quad, long ptr) {
		return 0;
	}

	@Override
	public void writeVertex(VertexWriter writer, float x, float y, float z, long ptr) {
		writeVertex(ptr, x, y, z, writer.color);
	}

	public static void writeVertex(long ptr, float x, float y, float z, int color) {
		UnsafeUtil.memPutFloat(ptr + 0, x);
		UnsafeUtil.memPutFloat(ptr + 4, y);
		UnsafeUtil.memPutFloat(ptr + 8, z);
		UnsafeUtil.memPutInt(ptr + 12, color);
	}
}
