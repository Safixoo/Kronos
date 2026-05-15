package dev.safixo.client.render.gfx.state;

import org.lwjgl.opengl.GL11;

// Memoize the state of frequent raster states to gl calls and draw-flushing so often,
// given that the game tends to change these states in a way that breaks batching pretty often.

// The states: DepthMask, Blending, GlCullFace, GlTexture2D, GlLighting
@SuppressWarnings("unused")
public class GlDrawTracker {
	public static void glBegin(int mode) {
		GlStateTracker.flushDrawState();
		GL11.glBegin(mode);
	}

	public static void glEnd() {
		GlStateTracker.flushDrawState();
		GL11.glEnd();
	}

	public static void glEndList() {
		GlStateTracker.flushDrawState();
		GL11.glEndList();
	}

	public static void glCallList(int list) {
		GlStateTracker.flushDrawState();
		GL11.glCallList(list);
	}

	public static void glNewList(int list, int drawMode) {
		GlStateTracker.flushDrawState();
		GL11.glNewList(list, drawMode);
	}

	public static void glDrawArrays(int mode, int first, int count) {
		GL11.glDrawArrays(mode, first, count);
	}
}
