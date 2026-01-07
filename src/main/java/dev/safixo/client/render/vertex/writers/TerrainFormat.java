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

	static final int POSITION_BITS = 20;
	static final int UV_BITS = 16;
	static final float UV_PRECISION = (1 << UV_BITS);

	public static final float RADIUS = 0.2f;
	static final float DIAMETER = RADIUS * 2.0f;

	static final float FACT_X = (1 << POSITION_BITS) / (DIAMETER_X + DIAMETER);
	static final float FACT_Y = (1 << POSITION_BITS) / (DIAMETER_Y + DIAMETER);
	static final float FACT_Z = (1 << POSITION_BITS) / (DIAMETER_Z + DIAMETER);

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
		return (int) ((pos + RADIUS) * scale) & 0xFFFFF;
	}

	private static int processUv(double u, double v) {
		int roundU = (int) (u * UV_PRECISION);
		int roundV = (int) (v * UV_PRECISION);

		roundU -= (roundU & 0x10000) >>> 16;
		roundV -= (roundV & 0x10000) >>> 16;

		int intU = roundU & 0xFFFF;
		int intV = roundV & 0xFFFF;

		return (intU | intV << 16);
	}

	// It does use a similar idea to Sodium 20-bit position.
	private static long processPosition(int x, int y, int z) {
		int lowHalf = 0;

		lowHalf |= (x & 0x3FF);
		lowHalf |= (y & 0x3FF) << 10;
		lowHalf |= (z & 0x3FF) << 20;

		int topHalf = 0;

		topHalf |= (x >>> 10 & 0x3FF);
		topHalf |= (y >>> 10 & 0x3FF) << 10;
		topHalf |= (z >>> 10 & 0x3FF) << 20;

		return lowHalf | ((long) topHalf << 32L);
	}

	public static void writeTerrainVertex(long ptr, float x, float y, float z, float u, float v, int color, int lightMap) {
		int intX = extractPos(x, FACT_X);
		int intY = extractPos(y, FACT_Y);
		int intZ = extractPos(z, FACT_Z);

		long position = processPosition(intX, intY, intZ);

		UnsafeUtil.memPutLong(ptr, position);
		UnsafeUtil.memPutInt(ptr + 8, processUv(u, v));
		UnsafeUtil.memPutInt(ptr + 12, color);
		UnsafeUtil.memPutInt(ptr + 15, compressLightmap(lightMap));
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
