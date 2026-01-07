package dev.safixo.core.hooks;

import dev.safixo.client.render.gfx.util.GpuFlags;
import org.lwjgl.opengl.EXTDirectStateAccess;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

// Functions that doesn't flush to GlStateManager
public class GLFunctions {
	public static void glLoadMatrix(FloatBuffer matrix) {
		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixLoadEXT(GlStateTracker.MAT_MODE, matrix);
		} else {
			GL11.glMultMatrix(matrix);
		}
	}

	public static void glMultMatrix(FloatBuffer matrix) {
		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixMultEXT(GlStateTracker.MAT_MODE, matrix);
		} else {
			GL11.glMultMatrix(matrix);
		}
	}

	public static void glPushMatrix() {
		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixPushEXT(GlStateTracker.MAT_MODE);
		} else {
			GL11.glPushMatrix();
		}
	}

	public static void glPopMatrix() {
		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixPopEXT(GlStateTracker.MAT_MODE);
		} else {
			GL11.glPopMatrix();
		}
	}

	public static void glCallList(int list) {
		GL11.glCallList(list);
	}

	public static void glTranslatef(float x, float y, float z) {
		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixTranslatefEXT(GlStateTracker.MAT_MODE, x, y, z);
		} else {
			GL11.glTranslatef(x, y, z);
		}
	}

	public static void glRotatef(float angle, float x, float y, float z) {
		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixRotatefEXT(GlStateTracker.MAT_MODE, angle, x, y, z);
		} else {
			GL11.glRotatef(angle, x, y, z);
		}
	}
}
