package dev.safixo.client.render.gfx.state;

import dev.safixo.client.util.memory.NativeBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

// Memoize the state of frequent raster states to gl calls and draw-flushing so often,
// given that the game tends to change these states in a way that breaks batching pretty often.

// The states: DepthMask, Blending, GlCullFace, GlTexture2D, GlLighting
@SuppressWarnings("unused")
public class GlDrawTracker {
	public static boolean CULLING;
	public static boolean BLENDING;
	public static boolean LIGHTING;
	public static boolean[] TEXTURING = new boolean[GL13.GL_TEXTURE31 - GL13.GL_TEXTURE0 + 1];

	public static int S_FACTOR;
	public static int D_FACTOR;

	static {
		Arrays.fill(TEXTURING, true);
	}

	public static void glTurnCulling(boolean state) {
		CULLING = state;
	}

	public static void glTurnBlending(boolean state) {
		BLENDING = state;
	}

	public static void glTurnLighting(boolean state) {
		LIGHTING = state;
	}

	public static void flushRasterState() {
		if (GlBooleanTracker.isEnabled(GL11.GL_LIGHTING) != LIGHTING) {
			setState(GL11.GL_LIGHTING, LIGHTING);
		}
		if (GlBooleanTracker.isEnabled(GL11.GL_BLEND) != BLENDING) {
			if (BLENDING) {
				if (GlLightColorTracker.S_FACTOR != S_FACTOR || GlLightColorTracker.D_FACTOR != D_FACTOR) {
					S_FACTOR = GlLightColorTracker.S_FACTOR;
					D_FACTOR = GlLightColorTracker.D_FACTOR;
					GL11.glBlendFunc(S_FACTOR, D_FACTOR);
				}
			} else {
				S_FACTOR = 0;
				D_FACTOR = 0;
			}

			setState(GL11.GL_BLEND, BLENDING);
		}
		if (GlBooleanTracker.isEnabled(GL11.GL_CULL_FACE) != CULLING) {
			setState(GL11.GL_CULL_FACE, CULLING);
		}

		int activeUnit = GlTextureTracker.ACTIVE_UNIT;
		boolean textureState = activeUnit == -1 || GlTextureTracker.ENABLED_TEXTURES[activeUnit];

		if (activeUnit == -1 || textureState != TEXTURING[activeUnit]) {
			GlTextureTracker.assertActiveTexture();

			if (activeUnit != -1) {
				TEXTURING[activeUnit] = textureState;
			}
			setState(GL11.GL_TEXTURE_2D, textureState);
		}
	}

	public static void setState(int cap, boolean turn) {
		if (turn) {
			GL11.glEnable(cap);
		} else {
			GL11.glDisable(cap);
		}
		GlBooleanTracker.setState(cap, turn);
	}

	public static void glBegin(int mode) {
		GlStateTracker.flushDrawState();
		GL11.glBegin(mode);
	}

	public static void glEnd() {
		GlStateTracker.flushDrawState();
		GlMatrixTracker.loadCurrentMatrix();
		flushRasterState();
		GL11.glEnd();
	}

	public static void glEndList() {
		GlStateTracker.flushDrawState();
		GL11.glEndList();
	}

	public static void glCallList(int list) {
		GlStateTracker.flushDrawState();
		GlMatrixTracker.loadCurrentMatrix();
		flushRasterState();
		GL11.glCallList(list);
	}

	public static void glNewList(int list, int drawMode) {
		GlStateTracker.flushDrawState();
		GL11.glNewList(list, drawMode);
	}

	private static final FloatBuffer BUFFER = NativeBuffer.memAllocFloat(16);

	public static void glDrawArrays(int mode, int first, int count) {
		GlMatrixTracker.loadCurrentMatrix();
		flushRasterState();

		GL11.glDrawArrays(mode, first, count);
	}

	public static void glMultiDrawArrays(int mode, IntBuffer piFirst, IntBuffer piCount) {
		GlMatrixTracker.loadCurrentMatrix();
		flushRasterState();

		GL14.glMultiDrawArrays(mode, piFirst, piCount);
	}

	public static void glMultiDrawArraysIndirect(int mode, ByteBuffer indirect, int primCount, int stride) {
		GlMatrixTracker.loadCurrentMatrix();
		flushRasterState();

		GL43.glMultiDrawArraysIndirect(mode, indirect, primCount, stride);
	}
}
