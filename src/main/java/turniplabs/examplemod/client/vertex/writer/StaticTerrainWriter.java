package turniplabs.examplemod.client.vertex.writer;

import org.lwjgl.system.MemoryUtil;

public class StaticTerrainWriter {
	public static void addVertex(long ptr, float x, float y, float z, float u, float v, int color, int lightmap) {
		MemoryUtil.memPutFloat(ptr + 0L, x);
		MemoryUtil.memPutFloat(ptr + 4L, y);
		MemoryUtil.memPutFloat(ptr + 8L, z);

		MemoryUtil.memPutFloat(ptr + 12, u);
		MemoryUtil.memPutFloat(ptr + 16, v);

		MemoryUtil.memPutInt(ptr + 20, color);
		MemoryUtil.memPutInt(ptr + 24, lightmap);
	}
}
