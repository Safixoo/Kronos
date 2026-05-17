package dev.safixo.client.render.gfx.state;

import dev.safixo.core.hooks.MinecraftHook;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.Arrays;

@SuppressWarnings("unused")
public class GlTextureTracker {
	public static int ACTIVE_UNIT = -1;
	public static int BINDED_UNIT = -1;

	public static final int[] TEXTURE_PER_UNIT = new int[GL13.GL_TEXTURE31 - GL13.GL_TEXTURE0 + 1];
	public static final boolean[] ENABLED_TEXTURES = new boolean[GL13.GL_TEXTURE31 - GL13.GL_TEXTURE0 + 1];

	private static int LAST_VIEWPORT_WH = -1;
	private static int LAST_TARGET = -1;
	public static float MU, MV;

	public static void glBindFramebuffer(int target, int frameBuffer) {
		GlStateTracker.flushDrawState();
		GL30.glBindFramebuffer(target, frameBuffer);
	}

	public static void glViewport(int x, int y, int width, int height) {
		int mask = width | height << 16;

		if (mask != LAST_VIEWPORT_WH || GlStateTracker.SKIP_CACHE) {
			LAST_VIEWPORT_WH = mask;
			GL11.glViewport(x, y, width, height);
		}
	}

	public static void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
		GlStateTracker.flushDrawState();
		GL11.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
	}

	public static int getViewportX() {
		return LAST_VIEWPORT_WH & 0xFFFF;
	}

	public static int getViewportY() {
		return LAST_VIEWPORT_WH >>> 16;
	}

	public static void glMultiTexCoord2f(int target, float u, float v) {
		if ((LAST_TARGET != target || MU != u || MV != v) && !MinecraftHook.FAST_ENTITY_PATH) {
			LAST_TARGET = target;
			MU = u;
			MV = v;

			GL13.glMultiTexCoord2f(target, u, v);
		}
	}

	static {
		Arrays.fill(ENABLED_TEXTURES, true);
	}

	public static void glTurnTexturing(boolean state) {
		if (GlStateTracker.SKIP_CACHE || ACTIVE_UNIT == -1 || ENABLED_TEXTURES[ACTIVE_UNIT] != state) {
			GlStateTracker.flushDrawState();
			assertActiveTexture();

			if (ACTIVE_UNIT != -1) {
				ENABLED_TEXTURES[ACTIVE_UNIT] = state;
			}
		}
	}

	// Only change the active texture when there is an operation that
	// uses the active texture.
	public static void glActiveTexture(int texture) {
		ACTIVE_UNIT = texture - GL13.GL_TEXTURE0;
	}

	public static void assertActiveTexture() {
		if (ACTIVE_UNIT != BINDED_UNIT) {
			BINDED_UNIT = ACTIVE_UNIT;
			GL13.glActiveTexture(BINDED_UNIT + GL13.GL_TEXTURE0);
		}
	}

	public static void glBindTexture(int target, int texture) {
		if (GlStateTracker.SKIP_CACHE || ACTIVE_UNIT == -1) {
			assertActiveTexture();
			GlStateTracker.flushDrawState();
			GL11.glBindTexture(target, texture);
		} else if (texture != TEXTURE_PER_UNIT[ACTIVE_UNIT]) {
			TEXTURE_PER_UNIT[ACTIVE_UNIT] = texture;

			assertActiveTexture();
			GlStateTracker.flushDrawState();
			GL11.glBindTexture(target, texture);
		}
	}
}
