package dev.safixo.client.render.gfx.state;

import dev.safixo.client.util.memory.NativeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

// Memoize the state of frequent raster states to reduce frequent gl calls and draw batch flushing,
// given that the game tends to change these states in a way that breaks the batching system pretty often.

// The states: DepthMask, Blending, GlCullFace, GlTexture2D, GlLighting
@SuppressWarnings("unused")
public class GlDrawTracker {
	public static boolean CULLING;
	public static boolean BLENDING;
	public static boolean LIGHTING;
	public static boolean[] TEXTURING = new boolean[GL13.GL_TEXTURE31 - GL13.GL_TEXTURE0 + 1];

	public static int S_FACTOR;
	public static int D_FACTOR;

	public static int LAST_TARGET = -1;
	public static float MU, MV;

	public static void flushRasterState() {
		if (GlStateTracker.SKIP_CACHE) {
			return;
		}

		if (GlBooleanTracker.isEnabled(GL11.GL_LIGHTING) != LIGHTING && GlBooleanTracker.isStateKnown(GL11.GL_LIGHTING)) {
			LIGHTING = GlBooleanTracker.isEnabled(GL11.GL_LIGHTING);
			setGlState(GL11.GL_LIGHTING, LIGHTING);
		}
		if (GlBooleanTracker.isEnabled(GL11.GL_BLEND) != BLENDING && GlBooleanTracker.isStateKnown(GL11.GL_BLEND)) {
			BLENDING = GlBooleanTracker.isEnabled(GL11.GL_BLEND);
			setGlState(GL11.GL_BLEND, BLENDING);
		}
		if (GlBooleanTracker.isEnabled(GL11.GL_CULL_FACE) != CULLING && GlBooleanTracker.isStateKnown(GL11.GL_CULL_FACE)) {
			CULLING = GlBooleanTracker.isEnabled(GL11.GL_CULL_FACE);
			setGlState(GL11.GL_CULL_FACE, CULLING);
		}

		GlTextureTracker.assertActiveTexture();
		checkMismatchEnabledTexture(GlTextureTracker.ACTIVE_UNIT);

		if (MU != GlTextureTracker.MU || MV != GlTextureTracker.MV || LAST_TARGET != GlTextureTracker.LAST_TARGET) {
			MU = GlTextureTracker.MU;
			MV = GlTextureTracker.MV;
			LAST_TARGET = GlTextureTracker.LAST_TARGET;
			GL13.glMultiTexCoord2f(LAST_TARGET, MU, MV);
		}

		GlClientStateTracker.invalidateAllCachedClientState();
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
		GlStateTracker.flushDrawState();

		GlMatrixTracker.loadCurrentMatrix();
		GlDrawTracker.flushRasterState();

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
