package dev.safixo.client.render.vertex.writers;

import com.google.common.collect.ImmutableList;
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

	private static int extractPos(float pos) {
		return (int) ((pos + RADIUS) * TerrainFormat.SCALE) & 0x1FFFFF;
	}

	private static long processUv(float u, float v) {
		int ui = deNormalizeTexCoordinate(u);
		int vi = deNormalizeTexCoordinate(v);
		return (ui | (long) vi << 16);
	}

	private static long processUv(int ui, int vi) {
		return (ui | (long) vi << 16);
	}

	public static int deNormalizeTexCoordinate(float a) {
		int round = (int) (a * UV_PRECISION);
		round -= (round & 0x10000) >>> 16;

		return round;
	}

	private static long processPosition(long x, long y, long z) {
		long topHalf = y | (z & ~0x7FFL) << 10L;
		long lowHalf = x | (z &  0x7FFL) << 21L;

		return topHalf << 32L | lowHalf;
	}

	public static void writeTerrainVertex(long ptr, float x, float y, float z, float u, float v, int color, int lightMap) {
		int intX = extractPos(x);
		int intY = extractPos(y);
		int intZ = extractPos(z);

		long position = processPosition(intX, intY, intZ);
		long uv = processUv(u, v);

		UnsafeUtil.memPutLong(ptr, position);
		UnsafeUtil.memPutLong(ptr + 8, uv | (long) color << 32 | (long) compressLightmap(lightMap) << 56);
	}

	public static void writeTerrainVertex(long ptr, float x, float y, float z, int u, int v, int color, int lightMap) {
		int intX = extractPos(x);
		int intY = extractPos(y);
		int intZ = extractPos(z);

		long position = processPosition(intX, intY, intZ);
		long uv = processUv(u, v);

		UnsafeUtil.memPutLong(ptr, position);
		UnsafeUtil.memPutLong(ptr + 8, uv | (long) color << 32 | (long) compressLightmap(lightMap) << 56);
	}

	// skylight << 20 | blocklight << 4
	private static int compressLightmap(int lightmap) {
		int skyLight4 = (lightmap >>> 20) & 0xF;
		int blockLight4 = (lightmap & 0xF0);

		return skyLight4 | blockLight4;
	}

	@Override
	public int getStride() {
		return STRIDE;
	}
}
