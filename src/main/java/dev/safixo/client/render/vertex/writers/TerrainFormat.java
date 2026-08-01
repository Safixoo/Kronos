package dev.safixo.client.render.vertex.writers;

import com.google.common.collect.ImmutableList;
import dev.safixo.client.render.pipelines.terrain.meshing.data.Quad;
import dev.safixo.client.render.pipelines.terrain.region.RegionConstants;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.client.render.gfx.vertex.GlVertexFormat;
import dev.safixo.client.render.gfx.vertex.attribute.GlVertexAttribute;

public class TerrainFormat extends GlVertexFormat {
	public static final int STRIDE = 16;

	public static final int POSITION_BITS = 21;
	static final int UV_BITS = 15;
	static final float UV_PRECISION = (1 << UV_BITS);

	// This assumes for now that all sides of the region have the same length.
	public static final float RADIUS = RegionConstants.DIAMETER_X >> 1;
	public static final float SCALE = (1 << POSITION_BITS) / (RegionConstants.DIAMETER_X + RADIUS * 2);

	public TerrainFormat(ImmutableList<GlVertexAttribute> vertexProperties) {
		super(vertexProperties);
	}

	@Override
	public int writeQuad(Quad quad, long ptr) {
		int offset = 0;

		for (int i = 0; i < 4; i++) {
			writeTerrainVertex(ptr + offset, quad.getX(i), quad.getY(i), quad.getZ(i), quad.getU(i), quad.getV(i), quad.getColor(i), quad.getLight(i));
			offset += this.getStride();
		}

		return offset;
	}

	private static int extractRadiusPos(float pos) {
		return (int) ((pos + RADIUS) * TerrainFormat.SCALE) & 0x1FFFFF;
	}

	private static int extractPos(float pos) {
		return (int) (pos * TerrainFormat.SCALE) & 0x1FFFFF;
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
		return (int) (a * UV_PRECISION);
	}

	private static long encodePosition(long x, long y, long z) {
		long topHalf = y | (z & ~0x7FFL) << 10L;
		long lowHalf = x | (z &  0x7FFL) << 21L;

		return topHalf << 32L | lowHalf;
	}

	public static long transformAddedPosition(int x, int y, int z) {
		int intX = extractPos(x);
		int intY = extractPos(y);
		int intZ = extractPos(z);

		return encodePosition(intX, intY, intZ);
	}

	public static long transformPosition(float x, float y, float z) {
		int intX = extractRadiusPos(x);
		int intY = extractRadiusPos(y);
		int intZ = extractRadiusPos(z);

		return encodePosition(intX, intY, intZ);
	}

	public static long transformPosition(int x, int y, int z) {
		int intX = extractRadiusPos(x);
		int intY = extractRadiusPos(y);
		int intZ = extractRadiusPos(z);

		return encodePosition(intX, intY, intZ);
	}

	public static void writeTerrainVertex(long ptr, float x, float y, float z, float u, float v, int color, int light) {
		long position = transformPosition(x, y, z);
		long uv = processUv(u, v);

		writeTerrainVertex(ptr, position, uv, color, light);
	}

	public static void writeTerrainVertex(long ptr, long position, int u, int v, int color, int light) {
		long uv = processUv(u, v);
		writeTerrainVertex(ptr, position, uv, color, light);
	}

	public static void writeTerrainVertex(long ptr, long position, long uv, int color, int light) {
		UnsafeUtil.memPutLong(ptr, position);
		UnsafeUtil.memPutLong(ptr + 8, uv | (color & 0xFFFFFFL) << 32 | (long) compressLightmap(light) << 56);
	}

	// skylight << 20 | blocklight << 4
	public static int compressLightmap(int lightmap) {
		int skyLight4 = (lightmap >>> 20) & 0xF;
		int blockLight4 = (lightmap & 0xF0);

		return skyLight4 | blockLight4;
	}
}
