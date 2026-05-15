package dev.safixo.client.render.gfx.state;

import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

@SuppressWarnings("unused")
public class GlFogTracker {
	public static int FOG_MODE;
	public static float FOG_START, FOG_END, FOG_DENSITY;
	public static float FOG_COLOR_R, FOG_COLOR_G, FOG_COLOR_B;

	public static void glFogi(int mode, int value) {
		if (mode == GL11.GL_FOG_MODE) {
			FOG_MODE = value;
		}

		GL11.glFogi(mode, value);
	}

	public static void glFogf(int mode, float value) {
		if (mode == GL11.GL_FOG_START) {
			FOG_START = value;
		} else if (mode == GL11.GL_FOG_END) {
			FOG_END = value;
		} else if (mode == GL11.GL_FOG_DENSITY) {
			FOG_DENSITY = value;
		}

		GL11.glFogf(mode, value);
	}

	public static void glFog(int mode, IntBuffer value) {
		GL11.glFog(mode, value);
	}

	public static void glFog(int mode, FloatBuffer value) {
		if (mode == GL11.GL_FOG_COLOR) {
			FOG_COLOR_R = value.get(0);
			FOG_COLOR_G = value.get(1);
			FOG_COLOR_B = value.get(2);
		}

		GL11.glFog(mode, value);
	}
}
