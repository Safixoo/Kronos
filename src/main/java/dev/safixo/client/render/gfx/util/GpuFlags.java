package dev.safixo.client.render.gfx.util;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

public class GpuFlags {
	private static boolean PROCESSED_FLAGS = false;
	public static boolean EXT_DSA;

	public static void processFlags() {
		ContextCapabilities capabilities = GLContext.getCapabilities();

		// The extension did work in my Nvidia GPU, but in an AMD APU it seemed to fail bad.
		EXT_DSA = capabilities.GL_EXT_direct_state_access && GL11.glGetString(GL11.GL_VENDOR).contains("Nvidia");

		PROCESSED_FLAGS = true;
	}

	public static void checkModSupport() {
		if (!PROCESSED_FLAGS) {
			processFlags();
		}
		ContextCapabilities capabilities = GLContext.getCapabilities();

		if (!capabilities.OpenGL33) {
			throw new RuntimeException(
				"Available OpenGL version doesn't support all the mod capabilities " + "\n" +
				"if that's the case sumbit a issue with your some hardware info and I will try" + "\n" +
				"to give a alternative in the near future"
			);
		}

	}
}
