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

	static final double FACT_X = (1 << (POSITION_BITS - BLOCK_SHIFT_X));
	static final double FACT_Y = (1 << (POSITION_BITS - BLOCK_SHIFT_Y));
	static final double FACT_Z = (1 << (POSITION_BITS - BLOCK_SHIFT_Z));

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
		return (int) (pos * scale) & 0x1FFFFF;
	}

	private static int processUv(float u, float v) {
		int intU = (int) (u * UV_PRECISION) & 0xFFFF;
		int intV = (int) (v * UV_PRECISION) & 0xFFFF;

		return intU | intV << 16;
	}

	public static void writeTerrainVertex(long ptr, double x, double y, double z, float u, float v, int color) {
		long position = extractPos(x, FACT_X) | (long) extractPos(y, FACT_Y) << 21 | (long) extractPos(z, FACT_Z) << 42;

		MemoryUtil.memPutLong(ptr + 0, position);
		MemoryUtil.memPutInt(ptr + 8, processUv(u, v));
		MemoryUtil.memPutInt(ptr + 12, color);
	}

	@Override
	public int getStride() {
		return STRIDE;
	}
}
