package dev.safixo.client.render.gfx.state;

import dev.safixo.client.util.memory.NativeBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

// Memoize the state of frequent raster states to reduce frequent gl calls and draw batch flushing,
// given that the game tends to change these states in a way that breaks the batching system pretty often and
// leads to many repeated gl calls.
@SuppressWarnings("unused")
public class GlDrawTracker {
	private static final FloatBuffer BUFFER = NativeBuffer.memAllocFloat(16);
	public static final boolean RASTER_CACHE = true;

	public static final int SMOOTH_MODEL_BIT = 0b100000;
	public static final int DEPTH_TEST_BIT   = 0b010000;
	public static final int DEPTH_MASK_BIT   = 0b001000;
	public static final int LIGHTING_BIT     = 0b000100;
	public static final int BLENDING_BIT     = 0b000010;
	public static final int CULLING_BIT      = 0b000001;

	// The awaiting to be bind "real" state.
	public static long TRACKED_STATE = DEPTH_MASK_BIT | DEPTH_TEST_BIT | SMOOTH_MODEL_BIT;

	// The bind state in the context.
	public static long CONTEXT_STATE = DEPTH_MASK_BIT | DEPTH_TEST_BIT | SMOOTH_MODEL_BIT;

	public static boolean[] TEXTURING = new boolean[GL13.GL_TEXTURE31 - GL13.GL_TEXTURE0 + 1];

	public static void setBlendFunc(int sFact, int dFact) {
		TRACKED_STATE &= 0xFFFF;
		TRACKED_STATE |= (sFact & 0xFFFFL) << 16 | (dFact & 0xFFFFL) << 32;
	}

	public static int getDFactor(long stateMask) {
		return (int) ((stateMask >>> 32L) & 0xFFFF);
	}

	public static int getSFactor(long stateMask) {
		return (int) ((stateMask >>> 16L) & 0xFFFF);
	}

	public static void drawArraysByState(long stateMask, int mode, int first, int count) {
		GlMatrixTracker.loadCurrentMatrix();
		GlDrawTracker.flushRasterState(stateMask);
		GL11.glDrawArrays(mode, first, count);
	}

	public static void flushRasterState() {
		flushRasterState(TRACKED_STATE);
	}

	// stateMask is the mask of state that wants to enable/disabled currently
	public static void flushRasterState(long stateMask) {
		if (GlStateTracker.SKIP_CACHE) {
			return;
		}

		if (stateMask != CONTEXT_STATE) {
			boolean blending = (stateMask & BLENDING_BIT) != 0;
			boolean culling = (stateMask & CULLING_BIT) != 0;
			boolean lighting = (stateMask & LIGHTING_BIT) != 0;
			boolean depthTest = (stateMask & DEPTH_TEST_BIT) != 0;
			boolean depthMask = (stateMask & DEPTH_MASK_BIT) != 0;
			boolean smoothShade = (stateMask & SMOOTH_MODEL_BIT) != 0;

			long diffMask = CONTEXT_STATE ^ stateMask;

			if ((diffMask & DEPTH_TEST_BIT) != 0) {
				setGlState(GL11.GL_DEPTH_TEST, depthTest);
			}
			if ((diffMask & LIGHTING_BIT) != 0) {
				setGlState(GL11.GL_LIGHTING, lighting);
			}
			if ((diffMask & BLENDING_BIT) != 0) {
				setGlState(GL11.GL_BLEND, blending);
			}
			if ((diffMask & CULLING_BIT) != 0) {
				setGlState(GL11.GL_CULL_FACE, culling);
			}
			if ((diffMask & DEPTH_MASK_BIT) != 0) {
				GL11.glDepthMask(depthMask);
			}
			if ((diffMask & SMOOTH_MODEL_BIT) != 0) {
				GL11.glShadeModel(smoothShade ? GL11.GL_SMOOTH : GL11.GL_FLAT);
			}
			if ((diffMask >>> 16) != 0) {
				GL11.glBlendFunc(getSFactor(stateMask), getDFactor(stateMask));
			}

			CONTEXT_STATE = stateMask;
		}

		GlTextureTracker.assertActiveTexture();
		GlDrawTracker.checkMismatchEnabledTexture(GlTextureTracker.ACTIVE_UNIT);
	}

	public static void setBitTracked(boolean state, int bit) {
		if (state) {
			TRACKED_STATE |= bit;
		} else {
			TRACKED_STATE &= ~bit;
		}
	}

	public static void checkMismatchEnabledTexture(int unit) {
		if (GlTextureTracker.ENABLED_TEXTURES[unit] != TEXTURING[unit]) {
			TEXTURING[unit] = GlTextureTracker.ENABLED_TEXTURES[unit];
			setGlState(GL11.GL_TEXTURE_2D, TEXTURING[unit]);
		}
	}

	public static void setGlState(int cap, boolean turn) {
		if (turn) {
			GL11.glEnable(cap);
		} else {
			GL11.glDisable(cap);
		}
	}

	public static void glDrawArrays(int mode, int first, int count) {
		GlMatrixTracker.loadCurrentMatrix();
		GlStateTracker.flushDrawState();
		GlDrawTracker.flushRasterState();

		GL11.glDrawArrays(mode, first, count);
	}

	public static void glMultiDrawArrays(int mode, IntBuffer piFirst, IntBuffer piCount) {
		GlMatrixTracker.loadCurrentMatrix();
		GlStateTracker.flushDrawState();
		GlDrawTracker.flushRasterState();

		GL14.glMultiDrawArrays(mode, piFirst, piCount);
	}

	public static void glMultiDrawArraysIndirect(int mode, ByteBuffer indirect, int primCount, int stride) {
		GlMatrixTracker.loadCurrentMatrix();
		GlStateTracker.flushDrawState();
		GlDrawTracker.flushRasterState();

		GL43.glMultiDrawArraysIndirect(mode, indirect, primCount, stride);
	}
}
