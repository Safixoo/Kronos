package dev.safixo.client.render.gfx.state;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.Arrays;

@SuppressWarnings("unused")
public class GlBooleanTracker {
	static final byte[] CAP_BITS = new byte[32827];

	private static final byte DEFINED_DISABLED = 0;
	private static final byte UNDEFINED = 1;
	private static final byte DEFINED_ENABLED = 2;

	private static boolean DEPTH_MASK = true;

	static {
		Arrays.fill(CAP_BITS, UNDEFINED);
	}

	public static boolean isStateKnown(int cap) {
		return CAP_BITS[cap] != UNDEFINED;
	}

	public static boolean isEnabled(int cap) {
		if (cap == GL11.GL_TEXTURE_2D) {
			return GlTextureTracker.ENABLED_TEXTURES[GlTextureTracker.ACTIVE_UNIT];
		}

		return CAP_BITS[cap] == DEFINED_ENABLED;
	}

	public static boolean isDisabled(int cap) {
		return CAP_BITS[cap] == DEFINED_DISABLED;
	}

	protected static void setState(int cap, boolean state) {
		CAP_BITS[cap] = state ? DEFINED_ENABLED : DEFINED_DISABLED;
	}

	private static boolean isRasterState(int cap, boolean state) {
		if (GlStateTracker.SKIP_CACHE) {
			return false;
		}

		if (cap == GL11.GL_CULL_FACE || cap == GL11.GL_BLEND || cap == GL11.GL_LIGHTING) {
			if (GlBooleanTracker.isEnabled(cap) != state) {
				GlStateTracker.flushDrawState();
			}
			GlBooleanTracker.setState(cap, state);
			return true;
		} else if (cap == GL11.GL_TEXTURE_2D) {
			if (GlBooleanTracker.isEnabled(cap) != state) {
				GlStateTracker.flushDrawState();
			}
			GlTextureTracker.ENABLED_TEXTURES[GlTextureTracker.ACTIVE_UNIT] = state;
			return true;
		}

		return false;
	}

	public static void glEnable(int cap) {
		if (isRasterState(cap, true)) {
			return;
		}

		if (!isEnabled(cap) || GlStateTracker.SKIP_CACHE) {
			if (cap == GL12.GL_RESCALE_NORMAL && GlMatrixTracker.MAT_MODE == GL11.GL_MODELVIEW) {
				GlMatrixTracker.loadCurrentMatrix();
			}

			setState(cap, true);

			GlStateTracker.flushDrawState();
			GL11.glEnable(cap);
		}
	}

	public static void glDisable(int cap) {
		if (isRasterState(cap, false)) {
			return;
		}

		if (!isDisabled(cap) || GlStateTracker.SKIP_CACHE) {
			setState(cap, false);

			GlStateTracker.flushDrawState();
			GL11.glDisable(cap);
		}
	}

	public static void glDepthMask(boolean state) {
		if (DEPTH_MASK == state && !GlStateTracker.SKIP_CACHE) {
			return;
		}
		DEPTH_MASK = state;

		GlStateTracker.flushDrawState();
		GL11.glDepthMask(state);
	}
}
