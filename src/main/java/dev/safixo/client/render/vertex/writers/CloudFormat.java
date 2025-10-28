package dev.safixo.client.render.vertex.writers;

import com.google.common.collect.ImmutableList;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.memory.UnsafeUtil;
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

	public static void writeCloudVertex(long ptr, int x, int y, int z, int color) {
		long pos = (byte) x & 0xFFL;
		pos |= ((byte) y & 0xFFL) << 8;
		pos |= ((byte) z & 0xFFL) << 16;
		pos |= (color & 0xFF_FF_FF_FFL) << 24;

		UnsafeUtil.memPutLong(ptr + 0, pos);
		UnsafeUtil.memPutInt(ptr + 3, color);
	}
}
