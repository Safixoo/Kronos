package dev.safixo.client.render.vertex.writers;

import com.google.common.collect.ImmutableList;
import dev.safixo.client.render.util.memory.UnsafeUtil;
import dev.safixo.client.render.vertex.VertexWriterManager;
import dev.safixo.client.render.gfx.vertex.GlVertexFormat;
import dev.safixo.client.render.gfx.vertex.attribute.GlVertexAttribute;

public class CloudFormat extends GlVertexFormat {
	public CloudFormat(ImmutableList<GlVertexAttribute> vertexProperties) {
		super(vertexProperties);
	}

	@Override
	public void writeVertex(long ptr, int offset) {
		VertexWriterManager manager = VertexWriterManager.getCurrentInstance();

		UnsafeUtil.memPutFloat(ptr + 0, (float) manager.x);
		UnsafeUtil.memPutFloat(ptr + 4, (float) manager.y);
		UnsafeUtil.memPutFloat(ptr + 8, (float) manager.z);

		UnsafeUtil.memPutFloat(ptr + 12, (float) manager.u);
		UnsafeUtil.memPutFloat(ptr + 16, (float) manager.v);

		UnsafeUtil.memPutInt(ptr + 20, manager.color);
	}
}
