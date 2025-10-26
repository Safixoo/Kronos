package dev.safixo.core.hooks;

import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.BlocksFlags;
import dev.safixo.client.util.memory.UnsafeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;

public class MinecraftHook {
	private static final int ITEM_STRIDE = 24;

	public static void checkGLError(Minecraft minecraft, String str) {
		if (!BlocksFlags.DETECTED) {
			BlocksFlags.processDevInfo();
		}

		GlStateManager.reset();
		// NO-OP
	}

	private static final int TOP_NORMAL = MathExt.packedNormal(0.0F, 1.0F, 0.0F);
	private static final int BOTTOM_NORMAL = MathExt.packedNormal(0.0F, -1.0F, 0.0F);

	private static final int FRONT_NORMAL = MathExt.packedNormal(0.0F, 0.0F, 1.0F);
	private static final int BEHIND_NORMAL = MathExt.packedNormal(0.0F, 0.0F, -1.0F);

	private static final int RIGHT_NORMAL = MathExt.packedNormal(1.0F, 0.0F, 0.0F);
	private static final int LEFT_NORMAL = MathExt.packedNormal(-1.0F, 0.0F, 0.0F);

	private static float MIN_U, MIN_V, MAX_U, MAX_V;
	private static int WIDTH, HEIGHT;
	private static float SCALE;

	private static int DRAW_IND;

	public static void renderItemIn2D(Tessellator tes, float minU, float maxV, float maxU, float minV, int width, int height, float scale) {
		ImprovedTessellator ver = (ImprovedTessellator) tes;

		if (isStateSame(ver, minU, maxV, width, height, scale)) {
			ver.drawWithoutUpload();
			return;
		}

		ver.startDrawingQuads();
		ver.formatFlag = ITEM_STRIDE;

		long ptr = ver.vertexPtr + ver.offset;

		ver.offset = (int) (ptr - ver.vertexPtr);
		if (ver.offset + ITEM_STRIDE * 8 >= ver.capacity) {
			ver.resize();
		}

		// Front quad.
		ptr = addVertex(ptr, 0.0F, 0.0F, 0.0F, minU, minV, FRONT_NORMAL);
		ptr = addVertex(ptr, 1.0F, 0.0F, 0.0F, maxU, minV, FRONT_NORMAL);
		ptr = addVertex(ptr, 1.0F, 1.0F, 0.0F, maxU, maxV, FRONT_NORMAL);
		ptr = addVertex(ptr, 0.0F, 1.0F, 0.0F, minU, maxV, FRONT_NORMAL);

		// Behind quad.
		ver.setNormal(0.0F, 0.0F, -1.0F);
		ptr = addVertex(ptr, 0.0F, 1.0F, -scale, minU, maxV, BEHIND_NORMAL);
		ptr = addVertex(ptr, 1.0F, 1.0F, -scale, maxU, maxV, BEHIND_NORMAL);
		ptr = addVertex(ptr, 1.0F, 0.0F, -scale, maxU, minV, BEHIND_NORMAL);
		ptr = addVertex(ptr, 0.0F, 0.0F, -scale, minU, minV, BEHIND_NORMAL);

		float invWidth = 1.0f / width;
		float invHeight = 1.0f / height;

		float halfTexelU = 0.5F * (minU - maxU) * invWidth;
		float haltTexelV = 0.5F * (minV - maxV) * invHeight;

		ver.offset = (int) (ptr - ver.vertexPtr);
		if (ver.offset + ITEM_STRIDE * 8L * width >= ver.capacity) {
			ver.resize();
		}

		for (int i = 0; i < width; i++) {
			float relW = i * invWidth;
			float nextRelW = relW + invWidth;
			float u = minU + (maxU - minU) * relW - halfTexelU;
			// -X
			ptr = addVertex(ptr, relW, 0.0F, -scale, u, minV, LEFT_NORMAL);
			ptr = addVertex(ptr, relW, 0.0F, 0.0F, u, minV, LEFT_NORMAL);
			ptr = addVertex(ptr, relW, 1.0F, 0.0F, u, maxV, LEFT_NORMAL);
			ptr = addVertex(ptr, relW, 1.0F, -scale, u, maxV, LEFT_NORMAL);

			// +X
			ptr = addVertex(ptr, nextRelW, 1.0F, -scale, u, maxV, RIGHT_NORMAL);
			ptr = addVertex(ptr, nextRelW, 1.0F, 0.0F, u, maxV, RIGHT_NORMAL);
			ptr = addVertex(ptr, nextRelW, 0.0F, 0.0F, u, minV, RIGHT_NORMAL);
			ptr = addVertex(ptr, nextRelW, 0.0F, -scale, u, minV, RIGHT_NORMAL);
		}

		ver.offset = (int) (ptr - ver.vertexPtr);
		if (ver.offset + ITEM_STRIDE * 8L * height >= ver.capacity) {
			ver.resize();
		}

		for (int i = 0; i < height; i++) {
			float relW = i * invHeight;
			float nextRelW = relW + invHeight;
			float v = minV + (maxV - minV) * relW - haltTexelV;
			// +Y
			ptr = addVertex(ptr, 0.0F, nextRelW, 0.0F, minU, v, TOP_NORMAL);
			ptr = addVertex(ptr, 1.0F, nextRelW, 0.0F, maxU, v, TOP_NORMAL);
			ptr = addVertex(ptr, 1.0F, nextRelW, -scale, maxU, v, TOP_NORMAL);
			ptr = addVertex(ptr, 0.0F, nextRelW, -scale, minU, v, TOP_NORMAL);

			// -Y
			ptr = addVertex(ptr, 1.0F, relW, 0.0F, maxU, v, BOTTOM_NORMAL);
			ptr = addVertex(ptr, 0.0F, relW, 0.0F, minU, v, BOTTOM_NORMAL);
			ptr = addVertex(ptr, 0.0F, relW, -scale, minU, v, BOTTOM_NORMAL);
			ptr = addVertex(ptr, 1.0F, relW, -scale, maxU, v, BOTTOM_NORMAL);
		}

		ver.offset = (int) (ptr - ver.vertexPtr);
		ver.vertices = ver.offset / ITEM_STRIDE;
		ver.flags = ImprovedTessellator.VERTEX_UV | ImprovedTessellator.VERTEX_NORMAL;
		ver.draw();

		saveState(minU, maxV, width, height, scale, ver.drawInd);
	}

	private static void saveState(float minU, float minV, int width, int height, float scale, int drawInd) {
		MIN_U = minU;
		MIN_V = minV;

		WIDTH = width;
		HEIGHT = height;

		SCALE = scale;
		DRAW_IND = drawInd;
	}

	private static boolean isStateSame(ImprovedTessellator tes, float minU, float minV, int width, int height, float scale) {
		return DRAW_IND == tes.drawInd && minU == MIN_U && minV == MIN_V && width == WIDTH && height == HEIGHT && scale == SCALE;
	}

	private static long addVertex(long ptr, float x, float y, float z, float u, float v, int normal) {
		UnsafeUtil.memPutFloat(ptr + 0, x);
		UnsafeUtil.memPutFloat(ptr + 4, y);
		UnsafeUtil.memPutFloat(ptr + 8, z);

		UnsafeUtil.memPutFloat(ptr + 12, u);
		UnsafeUtil.memPutFloat(ptr + 16, v);

		UnsafeUtil.memPutInt(ptr + 20, normal);

		return ptr + ITEM_STRIDE;
	}
}
