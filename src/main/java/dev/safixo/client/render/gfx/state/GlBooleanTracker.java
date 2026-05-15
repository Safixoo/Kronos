package dev.safixo.client.render.gfx.state;

import org.lwjgl.opengl.GL11;

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

	public static boolean isStateUnknown(int cap) {
		return CAP_BITS[cap] == UNDEFINED;
	}

	public static boolean isEnabled(int cap) {
		return CAP_BITS[cap] == DEFINED_ENABLED;
	}

	public static boolean isDisabled(int cap) {
		return CAP_BITS[cap] == DEFINED_DISABLED;
	}

	private static void setState(int cap, boolean state) {
		if (!GlStateTracker.SKIP_CACHE) {
			CAP_BITS[cap] = state ? DEFINED_ENABLED : DEFINED_DISABLED;
		}
	}

	public static void glEnable(int cap) {
		if (cap == GL11.GL_TEXTURE_2D) {
			GlTextureTracker.glTurnTexturing(true);
			return;
		}

		if (!isEnabled(cap) || GlStateTracker.SKIP_CACHE) {
			setState(cap, true);

			GlStateTracker.flushDrawState();
			GL11.glEnable(cap);
		}
	}

	public static void glDisable(int cap) {
		if (cap == GL11.GL_TEXTURE_2D) {
			GlTextureTracker.glTurnTexturing(false);
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
