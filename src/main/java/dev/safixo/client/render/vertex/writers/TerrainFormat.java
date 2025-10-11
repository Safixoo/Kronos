package dev.safixo.client.render.vertex.writers;

import com.google.common.collect.ImmutableList;
import dev.safixo.client.render.util.MathExt;
import dev.safixo.client.render.util.memory.UnsafeUtil;
import dev.safixo.client.render.vertex.VertexWriterManager;
import dev.safixo.client.render.vertex.format.VertexFormat;
import dev.safixo.client.render.vertex.operations.VertexAttribute;

import static dev.safixo.client.render.region.RegionRender.*;

public class TerrainFormat extends VertexFormat {
	public static final int STRIDE = 16;

	static final int POSITION_BITS = 20;
	static final int UV_BITS = 16;
	static final double UV_PRECISION = (1 << UV_BITS);

	public static final double RADIUS = 0.1d;
	static final double DIAMETER = RADIUS * 2.0d;

	static final double FACT_X = (1 << POSITION_BITS) / (DIAMETER_X + DIAMETER);
	static final double FACT_Y = (1 << POSITION_BITS) / (DIAMETER_Y + DIAMETER);
	static final double FACT_Z = (1 << POSITION_BITS) / (DIAMETER_Z + DIAMETER);

	public TerrainFormat(ImmutableList<VertexAttribute> vertexProperties) {
		super(vertexProperties);
	}

	@Override
	public void writeVertex(long ptr, int offset) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		double posX = MathExt.clamp(man.x + man.trasX, 0.0f, RADIUS_X << 1);
		double posY = MathExt.clamp(man.y + man.trasY, 0.0f, RADIUS_Y << 1);
		double posZ = MathExt.clamp(man.z + man.trasZ, 0.0f, RADIUS_Z << 1);

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

		lowHalf |= (x >>> 0 & 0x3FF) << 00;
		lowHalf |= (y >>> 0 & 0x3FF) << 10;
		lowHalf |= (z >>> 0 & 0x3FF) << 20;

		int topHalf = 0;

		topHalf |= (x >>> 10 & 0x3FF) << 00;
		topHalf |= (y >>> 10 & 0x3FF) << 10;
		topHalf |= (z >>> 10 & 0x3FF) << 20;

		return lowHalf | ((long) topHalf << 32L);
	}

	public static void writeTerrainVertex(long ptr, double x, double y, double z, double u, double v, int color, int lightMap) {
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
		int skyLight4 = MathExt.clamp((lightmap >>> 20) & 0xF, 0, 0xF);
		int blockLight4 = MathExt.clamp((lightmap >>> 4) & 0xF, 0, 0xF);

		return skyLight4 | blockLight4 << 4;
	}

	@Override
	public int getStride() {
		return STRIDE;
	}
}
