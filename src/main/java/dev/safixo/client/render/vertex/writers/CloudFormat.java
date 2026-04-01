package dev.safixo.client.render.vertex.writers;

import com.google.common.collect.ImmutableList;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.render.gfx.vertex.GlVertexFormat;
import dev.safixo.client.render.gfx.vertex.attribute.GlVertexAttribute;
import scala.concurrent.util.Unsafe;

public class CloudFormat extends GlVertexFormat {
	public CloudFormat(ImmutableList<GlVertexAttribute> vertexProperties) {
		super(vertexProperties);
	}

	@Override
	public void writeVertex(long ptr, int offset) {
	}

	public static void writeCloudVertex(long ptr, int x, int y, int z, int color) {
		UnsafeUtil.memPutLong(ptr, formatPosition(x, y, z) | formatColor(color));
	}

	public static void writeCloudVertex(long ptr, long vertex) {
		UnsafeUtil.memPutLong(ptr, vertex);
	}

	public static long formatPosition(long x, long y, long z) {
		return x | (y << 8) | (z << 16);
	}

	public static long formatColor(int color) {
		return (long) color << 24L;
	}
}
