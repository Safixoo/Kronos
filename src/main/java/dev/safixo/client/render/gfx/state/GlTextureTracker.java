package dev.safixo.client.render.gfx.state;

import org.lwjgl.BufferChecks;
import org.lwjgl.MemoryUtil;
import org.lwjgl.opengl.*;

import java.nio.*;
import java.util.Arrays;

@SuppressWarnings("unused")
public class GlTextureTracker {
	private static final boolean SKIP_CACHE = true;

	public static int ACTIVE_UNIT = 0;
	public static int BINDED_UNIT = 0;

	public static final int[] TEXTURE_PER_UNIT = new int[GL13.GL_TEXTURE31 - GL13.GL_TEXTURE0 + 1];
	public static final boolean[] ENABLED_TEXTURES = new boolean[GL13.GL_TEXTURE31 - GL13.GL_TEXTURE0 + 1];

	private static int LAST_VIEWPORT_WH = -1;
	public static int LAST_TARGET = -1;
	public static float MU, MV;

	static {
		Arrays.fill(TEXTURE_PER_UNIT, Integer.MIN_VALUE);
	}

	public static void glBindFramebuffer(int target, int frameBuffer) {
		GlStateTracker.flushDrawState();
		GL30.glBindFramebuffer(target, frameBuffer);
	}

	public static void glViewport(int x, int y, int width, int height) {
		int mask = width | height << 16;

		if (mask != LAST_VIEWPORT_WH || SKIP_CACHE) {
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
		GlTextureTracker.assertActiveTexture();

		if (true || SKIP_CACHE || LAST_TARGET != target || MV != v || MU != u) {
			GL13.glMultiTexCoord2f(target, u, v);
		}

		LAST_TARGET = target;
		MU = u;
		MV = v;
	}

	// Only change the active texture when there is an operation that
	// uses the active texture.
	public static void glActiveTexture(int target) {
		ACTIVE_UNIT = target - GL13.GL_TEXTURE0;
		if (SKIP_CACHE) {
			GL13.glActiveTexture(target);
		}
	}

	public static void assertActiveTexture() {
		if (ACTIVE_UNIT != BINDED_UNIT) {
			GlDrawTracker.checkMismatchEnabledTexture(BINDED_UNIT);

			BINDED_UNIT = ACTIVE_UNIT;
			GL13.glActiveTexture(BINDED_UNIT + GL13.GL_TEXTURE0);
		}
	}

	public static void glBindTexture(int target, int texture) {
		if (SKIP_CACHE || texture != TEXTURE_PER_UNIT[ACTIVE_UNIT]) {
			TEXTURE_PER_UNIT[ACTIVE_UNIT] = texture;
			GlStateTracker.flushDrawState();

			// First check if the texture in the current bind slot is in the correct state,
			// and then change the texture in the state active slot.
			GlTextureTracker.assertActiveTexture();

			GL11.glBindTexture(target, texture);
		}
	}

	public static void glActiveTextureARB(int texture) {
		glActiveTexture(texture);
	}

	public static int glGetTexLevelParameteri(int target, int level, int pname) {
		return GL11.glGetTexLevelParameteri(target, level, pname);
	}

	public static void glTexGen(int coord, int pname, FloatBuffer params) {
		GL11.glTexGen(coord, pname, params);
	}

	public static void glTexGen(int coord, int pname, DoubleBuffer params) {
		GL11.glTexGen(coord, pname, params);
	}

	public static void glTexGen(int coord, int pname, IntBuffer params) {
		GL11.glTexGen(coord, pname, params);
	}

	public static void glTexGeni(int coord, int pname, int params) {
		GL11.glTexGeni(coord, pname, params);
	}

	public static void glAlphaFunc(int func, float ref) {
		GL11.glAlphaFunc(func, ref);
	}

	public static void glTexParameteri(int target, int pname, int param) {
		GL11.glTexParameteri(target, pname, param);
	}

	public static void glTexParameterf(int target, int pname, float param) {
		GL11.glTexParameterf(target, pname, param);
	}

	public static void glMultiTexCoord2fARB(int target, float u, float v) {
		glMultiTexCoord2f(target, u, v);
	}

	public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
		GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
	}

	public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, DoubleBuffer pixels) {
		GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
	}

	public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, FloatBuffer pixels) {
		GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
	}

	public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
		GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
	}

	public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ShortBuffer pixels) {
		GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
	}

	public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, long pixels_buffer_offset) {
		GL11.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
	}

	public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
		GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
	}

	public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, DoubleBuffer pixels) {
		GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
	}

	public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, FloatBuffer pixels) {
		GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
	}

	public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
		GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
	}

	public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ShortBuffer pixels) {
		GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
	}

	public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, long pixels_buffer_offset) {
		GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels_buffer_offset);
	}
}
