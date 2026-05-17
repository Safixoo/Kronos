package dev.safixo.client.render.gfx.state;

import dev.safixo.client.util.ColorBGRManager;
import org.lwjgl.opengl.GL11;

@SuppressWarnings("unused")
public class GlLightColorTracker {
	public static long LAST_COLOR_MATERIAL = -31;
	public static int LAST_CLEAR_COLOR = 0;
	public static int LAST_COLOR = -1;

	public static int S_FACTOR;
	public static int D_FACTOR;

	public static void glShadeModel(int mode) {
		GlStateTracker.flushDrawState();
		GL11.glShadeModel(mode);
	}

	public static void glClear(int mask) {
		GlStateTracker.flushDrawState();
		GL11.glClear(mask);
	}

	public static void glBlendFunc(int sFactor, int dFactor) {
		if (sFactor == S_FACTOR && dFactor == D_FACTOR) {
			return;
		}

		GlStateTracker.flushDrawState();
		S_FACTOR = sFactor;
		D_FACTOR = dFactor;
	}

	public static void glColor3f(float red, float green, float blue) {
		int color = ColorBGRManager.packColor(red, green, blue) | LAST_COLOR & 0xFF_000000;

		if (color != LAST_COLOR || GlStateTracker.SKIP_CACHE) {
			LAST_COLOR = color | LAST_COLOR & 0xFF_000000;
			GlStateTracker.flushDrawState();
			GL11.glColor3f(red, green, blue);
		}
	}

	public static void glColor4f(float red, float green, float blue, float alpha) {
		int color = ColorBGRManager.packColor(red, green, blue) | ((int) (alpha * 255.0f) << 24);

		if (color != LAST_COLOR || GlStateTracker.SKIP_CACHE) {
			LAST_COLOR = color;
			GlStateTracker.flushDrawState();
			GL11.glColor4f(red, green, blue, alpha);
		}
	}

	public static void glClearColor(float red, float green, float blue, float alpha) {
		int clearColor = ColorBGRManager.packColor(red, green, blue) | (int) (alpha * 255.0f) << 24;

		if (LAST_CLEAR_COLOR != clearColor || GlStateTracker.SKIP_CACHE) {
			LAST_CLEAR_COLOR = clearColor;
			GL11.glClearColor(red, green, blue, alpha);
		}
	}

	public static void glColorMaterial(int face, int mode) {
		long mask = (face & 0xFFFFFFFFL) << 32L | (mode & 0xFFFFFFFFL);

		if (LAST_COLOR_MATERIAL != mask || GlStateTracker.SKIP_CACHE) {
			GlStateTracker.flushDrawState();
			GL11.glColorMaterial(face, mode);
			LAST_COLOR_MATERIAL = mask;
		}
	}

	public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
		GlStateTracker.flushDrawState();
		GL11.glColorMask(red, green, blue, alpha);
	}
}
