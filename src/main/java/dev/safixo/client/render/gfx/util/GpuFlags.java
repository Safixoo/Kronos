package dev.safixo.client.render.gfx.util;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

public class GpuFlags {
	private static boolean PROCESSED_FLAGS = false;

	public static boolean EXT_DSA;
	public static int OPENGL_VERSION;

	public static void processFlags() {
		ContextCapabilities capabilities = GLContext.getCapabilities();

		// The extension has worked in my Nvidia' GPU, but in an AMD APU seems to fail bad.
		EXT_DSA = capabilities.GL_EXT_direct_state_access && GL11.glGetString(GL11.GL_VENDOR).contains("Nvidia");

		if (capabilities.OpenGL43) {
			OPENGL_VERSION = 43;
		} else if (capabilities.OpenGL42) {
			OPENGL_VERSION = 42;
		} else if (capabilities.OpenGL41) {
			OPENGL_VERSION = 41;
		} else if (capabilities.OpenGL40) {
			OPENGL_VERSION = 40;
		} else if (capabilities.OpenGL33) {
			OPENGL_VERSION = 33;
		} else if (capabilities.OpenGL31) {
			OPENGL_VERSION = 31;
		}

		PROCESSED_FLAGS = true;
	}

	public static void checkModSupport() {
		if (!PROCESSED_FLAGS) {
			processFlags();
		}

		if (OPENGL_VERSION < 33) {
			throw new RuntimeException(
				"Available OpenGL version doesn't support all the mod capabilities " + "\n" +
				"if that's the case sumbit a issue with your some hardware info and I will try" + "\n" +
				"to give a alternative in the near future"
			);
		}

	}
}
