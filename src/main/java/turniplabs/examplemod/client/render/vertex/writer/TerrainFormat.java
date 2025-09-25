package turniplabs.examplemod.client.render.vertex.writer;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.util.helper.MathHelper;
import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.render.region.RegionRender;
import turniplabs.examplemod.client.render.vertex.VertexWriterManager;
import turniplabs.examplemod.client.render.vertex.format.VertexFormat;
import turniplabs.examplemod.client.render.vertex.operations.VertexAttribute;

import static turniplabs.examplemod.client.render.region.RegionRender.*;

// In difference with modern Minecraft, beta seems to not use a lighting
// attribute (???), it simply uses lighting baked in color attribute.
// Which makes the difference in whereas not using lighting baked in a
// texture it doesn't produces as smoothly chunk transitions as it has
// to redo the vertex data to change lighting, which makes the rare transitions
// at the morning/afternoon of the game.
public class TerrainFormat extends VertexFormat {
	public static final int STRIDE = 16;

	static final int POSITION_BITS = 20;
	static final double UV_PRECISION = (1 << 16) - 1;

	static final double FACT_X = (1 << (POSITION_BITS - BLOCK_SHIFT_X)) - 1;
	static final double FACT_Y = (1 << (POSITION_BITS - BLOCK_SHIFT_Y)) - 1;
	static final double FACT_Z = (1 << (POSITION_BITS - BLOCK_SHIFT_Z)) - 1;

	public TerrainFormat(ImmutableList<VertexAttribute> vertexProperties) {
		super(vertexProperties);
	}

	@Override
	public void writeVertex(long ptr, int offset) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		float posX = MathHelper.clamp(man.x + man.trasX, 0.0f, RADIUS_X << 1);
		float posY = MathHelper.clamp(man.y + man.trasY, 0.0f, RADIUS_Y << 1);
		float posZ = MathHelper.clamp(man.z + man.trasZ, 0.0f, RADIUS_Z << 1);

		writeTerrainVertex(ptr, posX, posY, posZ, man.u, man.v, man.color);
	}

	private static int extractPos(double pos, double scale) {
		return (int) (pos * scale) & 0xFFFFF;
	}

	private static int processUv(double u, double v) {
		int intU = (int) Math.round(u * UV_PRECISION) & 0xFFFF;
		int intV = (int) Math.round(v * UV_PRECISION) & 0xFFFF;

		return intU | intV << 16;
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

	public static void writeTerrainVertex(long ptr, double x, double y, double z, float u, float v, int color) {
		int intX = extractPos(x, FACT_X);
		int intY = extractPos(y, FACT_Y);
		int intZ = extractPos(z, FACT_Z);

		long position = processPosition(intX, intY, intZ);

		MemoryUtil.memPutLong(ptr + 0, position);
		MemoryUtil.memPutInt(ptr + 8, processUv(u, v));
		MemoryUtil.memPutInt(ptr + 12, color);
	}

	@Override
	public int getStride() {
		return STRIDE;
	}
}
