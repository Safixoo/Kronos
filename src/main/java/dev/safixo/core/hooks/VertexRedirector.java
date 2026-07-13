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
	public final long ptrQuad = NativeBuffer.nmemAlloc(VERTEX_SIZE * 4);
	public int vertIndex;

	public static final long X      = 0;
	public static final long Y      = 4;
	public static final long Z 	    = 8;
	public static final long U      = 12;
	public static final long V 	    = 16;
	public static final long COLOR  = 20;
	public static final long LIGHT  = 24;
	public static final long NORMAL = 28;

	public static boolean ORGANIZE_NORMALS;

	public void setTextureUV(double u, double v) {
		long ptr = ptrQuad + VERTEX_SIZE * vertIndex;
		UnsafeUtil.memPutFloat(ptr + U, (float) u);
		UnsafeUtil.memPutFloat(ptr + V, (float) v);
	}

	public void addVertexWithUV(double x, double y, double z, double u, double v) {
		this.setTextureUV(u, v);
		this.addVertex(x, y, z);
	}

	public void addVertex(double x, double y, double z) {
		long ptr = ptrQuad + VERTEX_SIZE * vertIndex;
		ImprovedTessellator tes = ImprovedTessellator.getTessellator();

		UnsafeUtil.memPutFloat(ptr + X, (float) (x + tes.xOff));
		UnsafeUtil.memPutFloat(ptr + Y, (float) (y + tes.yOff));
		UnsafeUtil.memPutFloat(ptr + Z, (float) (z + tes.zOff));

		UnsafeUtil.memPutInt(ptr + COLOR, tes.color);
		UnsafeUtil.memPutInt(ptr + LIGHT, tes.light);
		UnsafeUtil.memPutInt(ptr + NORMAL, tes.normal);

		this.vertIndex++;

		if (this.vertIndex == 4) {
			this.bufferQuad();
			this.vertIndex = 0;
		}
	}

	private void bufferQuad() {
		VertexWriter instance = VertexWriter.getCurrentInstance();

		for (int i = 0; i < 4; i++) {
			this.bufferVertex(instance, this.ptrQuad + i * VERTEX_SIZE);
		}
	}

	private void assignNormalAndBuffer() {
		long ptr = this.ptrQuad;

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
			bufferVertex(writer, this.ptrQuad + i * VERTEX_SIZE);
		}
	}

	private void bufferVertex(VertexWriter writer, long ptr) {
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
