package dev.safixo.client.render.vertex.writers;

import com.google.common.collect.ImmutableList;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.render.gfx.vertex.GlVertexFormat;
import dev.safixo.client.render.gfx.vertex.attribute.GlVertexAttribute;

import static dev.safixo.client.render.pipelines.terrain.region.RegionRender.*;

public class TerrainFormat extends GlVertexFormat {
	public static final int STRIDE = 16;

	public static final int POSITION_BITS = 21;
	static final int UV_BITS = 16;
	static final float UV_PRECISION = (1 << UV_BITS);

	public static final float RADIUS = 0.5f;
	static final float DIAMETER = RADIUS * 2.0f;

	public static final float SCALE = (1 << POSITION_BITS) / (DIAMETER_X + DIAMETER);

	public TerrainFormat(ImmutableList<GlVertexAttribute> vertexProperties) {
		super(vertexProperties);
	}

	@Override
	public void writeVertex(long ptr, int offset) {
		VertexWriter man = VertexWriter.getCurrentInstance();

		float posX = man.x + man.trasX;
		float posY = man.y + man.trasY;
		float posZ = man.z + man.trasZ;

		writeTerrainVertex(ptr, posX, posY, posZ, man.u, man.v, man.color, man.lightMap);
	}

	private static int extractPos(double pos, double scale) {
		return (int) ((pos + RADIUS) * scale) & 0x1FFFFF;
	}

	private static long processUv(double u, double v) {
		int roundU = (int) (u * UV_PRECISION);
		int roundV = (int) (v * UV_PRECISION);

		roundU -= (roundU & 0x10000) >>> 16;
		roundV -= (roundV & 0x10000) >>> 16;

		long intU = roundU & 0xFFFFL;
		long intV = roundV & 0xFFFFL;

		return (intU | intV << 16);
	}

	// Uses the same encoding as Sodium 20-bit vertex positions.
	private static long processPosition(long x, long y, long z) {
		long topHalf = y | (z >> 11L) << 21L;
		long lowHalf = x | (z & 0x7FFL) << 21L;

		return topHalf << 32L | lowHalf;
	}

	public static void writeTerrainVertex(long ptr, float x, float y, float z, float u, float v, int color, int lightMap) {
		int intX = extractPos(x, SCALE);
		int intY = extractPos(y, SCALE);
		int intZ = extractPos(z, SCALE);

		long position = processPosition(intX, intY, intZ);

		UnsafeUtil.memPutLong(ptr, position);
		UnsafeUtil.memPutLong(ptr + 8, processUv(u, v) | (long) color << 32 | (long) compressLightmap(lightMap) << 56);
	}

	// skylight << 20 | blocklight << 4
	private static int compressLightmap(int lightmap) {
		int skyLight4 = lightmap >>> 20;
		int blockLight4 = lightmap & 0xF0;

		return skyLight4 | blockLight4;
	}

	@Override
	public int getStride() {
		return STRIDE;
	}
}
