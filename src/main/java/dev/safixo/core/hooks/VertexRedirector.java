package dev.safixo.core.hooks;

import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.render.pipelines.terrain.meshing.model.NormalUtil;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.MeshDirection;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;


@SuppressWarnings("unused")
// 0b  : X
// 4b  : Y
// 8b  : Z
// 12b : texU
// 16b : texV
// 20b : color
// 24b : brightness
public class VertexRedirector {
	public static final long VERTEX_SIZE = 32;
	public static final long PTR_QUAD = NativeBuffer.nmemAlloc(VERTEX_SIZE * 4);
	public static int VERT_INDEX;

	public static final long X      = 0;
	public static final long Y      = 4;
	public static final long Z 	    = 8;
	public static final long U      = 12;
	public static final long V 	    = 16;
	public static final long COLOR  = 20;
	public static final long LIGHT  = 24;
	public static final long NORMAL = 28;

	public static boolean ORGANIZE_NORMALS;

	public static void setTextureUV(double u, double v) {
		long ptr = PTR_QUAD + VERTEX_SIZE * VERT_INDEX;
		UnsafeUtil.memPutFloat(ptr + U, (float) u);
		UnsafeUtil.memPutFloat(ptr + V, (float) v);
	}

	public static void addVertexWithUV(double x, double y, double z, double u, double v) {
		setTextureUV(u, v);
		addVertex(x, y, z);
	}

	public static void addVertex(double x, double y, double z) {
		long ptr = PTR_QUAD + VERTEX_SIZE * VERT_INDEX;
		ImprovedTessellator tes = ImprovedTessellator.INSTANCE;

		UnsafeUtil.memPutFloat(ptr + X, (float) (x + tes.xOff));
		UnsafeUtil.memPutFloat(ptr + Y, (float) (y + tes.yOff));
		UnsafeUtil.memPutFloat(ptr + Z, (float) (z + tes.zOff));

		UnsafeUtil.memPutInt(ptr + COLOR, tes.color);
		UnsafeUtil.memPutInt(ptr + LIGHT, tes.light);
		UnsafeUtil.memPutInt(ptr + NORMAL, tes.normal);

		VERT_INDEX++;

		if (VERT_INDEX == 4) {
			bufferQuad();
			VERT_INDEX = 0;
		}
	}

	private static void bufferQuad() {
		VertexWriter instance = VertexWriter.getCurrentInstance();

		if (!ORGANIZE_NORMALS || instance != VertexWriter.SOLID[MeshDirection.GENERIC]) {
			for (int i = 0; i < 4; i++) {
				bufferVertex(instance, PTR_QUAD + i * VERTEX_SIZE);
			}
		} else {
			assignNormalAndBuffer();
		}
	}

	private static void assignNormalAndBuffer() {
		long ptr = PTR_QUAD;

		float x0 = UnsafeUtil.memGetFloat(ptr + X);
		float y0 = UnsafeUtil.memGetFloat(ptr + Y);
		float z0 = UnsafeUtil.memGetFloat(ptr + Z);
		ptr += VERTEX_SIZE;

		float x1 = UnsafeUtil.memGetFloat(ptr + X);
		float y1 = UnsafeUtil.memGetFloat(ptr + Y);
		float z1 = UnsafeUtil.memGetFloat(ptr + Z);
		ptr += VERTEX_SIZE;

		float x2 = UnsafeUtil.memGetFloat(ptr + X);
		float y2 = UnsafeUtil.memGetFloat(ptr + Y);
		float z2 = UnsafeUtil.memGetFloat(ptr + Z);

		int normalDir = NormalUtil.assignNormalBitset(x0, y0, z0, x1, y1, z1, x2, y2, z2);
		VertexWriter writer = VertexWriter.SOLID[normalDir];

		for (int i = 0; i < 4; i++) {
			bufferVertex(writer, PTR_QUAD + i * VERTEX_SIZE);
		}
	}

	private static void bufferVertex(VertexWriter writer, long ptr) {
		writer.x = UnsafeUtil.memGetFloat(ptr + X);
		writer.y = UnsafeUtil.memGetFloat(ptr + Y);
		writer.z = UnsafeUtil.memGetFloat(ptr + Z);

		writer.u = UnsafeUtil.memGetFloat(ptr + U);
		writer.v = UnsafeUtil.memGetFloat(ptr + V);

		writer.color = UnsafeUtil.memGetInt(ptr + COLOR);
		writer.lightMap = UnsafeUtil.memGetInt(ptr + LIGHT);
		writer.normal = UnsafeUtil.memGetInt(ptr + NORMAL);

		writer.addVertex();
	}
}
