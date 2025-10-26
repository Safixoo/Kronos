package dev.safixo.core.hooks;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

public class GlStateManager {
	public static boolean SKIP_CACHE = false;

	private static final byte[] CAP_BITS = new byte[32827];
	private static final boolean[] SKIPPED_STATES = new boolean[32827];

	static {
		SKIPPED_STATES[GL11.GL_TEXTURE_2D] = true;
		SKIPPED_STATES[GL11.GL_BLEND] = true;
		SKIPPED_STATES[GL11.GL_LIGHTING] = true;
		SKIPPED_STATES[GL11.GL_STENCIL_TEST] = true;
		SKIPPED_STATES[GL11.GL_SCISSOR_TEST] = true;
	}

	private static final byte UNDEFINED = 0b00;
	private static final byte DEFINED_DISABLED = 0b01;
	private static final byte DEFINED_ENABLED = 0b11;

	public static final Matrix4fStack MODEL_VIEW_STACK = new Matrix4fStack(32);
	public static final Matrix4fStack PROJECTION_STACK = new Matrix4fStack(8);
	public static final Matrix4fStack TEXTURE_STACK = new Matrix4fStack(8);

	public static Matrix4fStack MATRIX_STACK = MODEL_VIEW_STACK;
	public static IntStack MATRIX_MODE_STACK = new IntArrayList(32);

	private static final Matrix4f TEMP_MATRIX = new Matrix4f();
	public static int CUR_MAT_MODE;

	public static void glEnable(int cap) {
		if (SKIP_CACHE || SKIPPED_STATES[cap]) {
			GL11.glEnable(cap);
			return;
		}

		if (CAP_BITS[cap] <= DEFINED_DISABLED) {
			CAP_BITS[cap] = DEFINED_ENABLED;
			GL11.glEnable(cap);
		}
	}

	public static void glDisable(int cap) {
		if (SKIP_CACHE || SKIPPED_STATES[cap]) {
			GL11.glDisable(cap);
			return;
		}

		if (CAP_BITS[cap] == UNDEFINED || CAP_BITS[cap] == DEFINED_ENABLED) {
			CAP_BITS[cap] = DEFINED_DISABLED;
			GL11.glDisable(cap);
		}
	}

	public static void glPopAttrib() {
		reset();
		GL11.glPopAttrib();
	}

	public static void glPushAttrib(int mask) {
		reset();
		GL11.glPushAttrib(mask);
	}

	public static void reset() {
		MODEL_VIEW_STACK.clear();
		TEXTURE_STACK.clear();
		PROJECTION_STACK.clear();

		MATRIX_STACK = MODEL_VIEW_STACK;
	}

	public static void glPushMatrix() {
		MATRIX_MODE_STACK.push(CUR_MAT_MODE);

		MATRIX_STACK.pushMatrix();
		GL11.glPushMatrix();
	}

	public static void glPopMatrix() {
		MATRIX_STACK.popMatrix();
		GL11.glPopMatrix();

		int matMode = MATRIX_MODE_STACK.popInt();

		if (matMode == GL11.GL_PROJECTION) {
			MATRIX_STACK = PROJECTION_STACK;
		} else if (matMode == GL11.GL_MODELVIEW) {
			MATRIX_STACK = MODEL_VIEW_STACK;
		} else if (matMode == GL11.GL_TEXTURE) {
			MATRIX_STACK = TEXTURE_STACK;
		}
	}

	public static void glMatrixMode(int mode) {
		CUR_MAT_MODE = mode;

		if (mode == GL11.GL_PROJECTION) {
			MATRIX_STACK = PROJECTION_STACK;
		} else if (mode == GL11.GL_MODELVIEW) {
			MATRIX_STACK = MODEL_VIEW_STACK;
		} else if (mode == GL11.GL_TEXTURE) {
			MATRIX_STACK = TEXTURE_STACK;
		}

		GL11.glMatrixMode(mode);
	}

	public static float glGetFloat(int name) {
		return GL11.glGetFloat(name);
	}

	public static void glBindFramebuffer(int target, int frameBuffer) {
		GL30.glBindFramebuffer(target, frameBuffer);

		CAP_BITS[GL11.GL_DEPTH_TEST] = UNDEFINED;
		CAP_BITS[GL11.GL_BLEND] = UNDEFINED;
		CAP_BITS[GL11.GL_ALPHA_TEST] = UNDEFINED;
	}

	public static void glGetFloat(int name, FloatBuffer params) {
		if (name == GL11.GL_PROJECTION_MATRIX) {
			PROJECTION_STACK.get(params);
			return;
		} else if (name == GL11.GL_MODELVIEW_MATRIX) {
			MODEL_VIEW_STACK.get(params);
			return;
		} else if (name == GL11.GL_TEXTURE_MATRIX) {
			TEXTURE_STACK.get(params);
			return;
		}

		GL11.glGetFloat(name, params);
	}

	public static void glFlush() {

	}

	public static void glLoadIdentity() {
		MATRIX_STACK.identity();
		GL11.glLoadIdentity();
	}

	public static void glLoadMatrix(FloatBuffer matrix) {
		MATRIX_STACK.set(matrix);
		GL11.glLoadMatrix(matrix);
	}

	public static void glMultMatrix(FloatBuffer matrix) {
		MATRIX_STACK.mul(TEMP_MATRIX.set(matrix));
		GL11.glMultMatrix(matrix);
	}

	public static void glScalef(float x, float y, float z) {
		MATRIX_STACK.scale(x, y, z);
		GL11.glScalef(x, y, z);
	}

	public static void glScaled(double x, double y, double z) {
		glScalef((float) x, (float) y, (float) z);
	}

	public static void glRotated(double angle, double x, double y, double z) {
		glRotatef((float) angle, (float) x, (float) y, (float) z);
	}

	public static void glRotatef(float angle, float x, float y, float z) {
		MATRIX_STACK.rotate(angle, x, y, z);
		GL11.glRotatef(angle, x, y, z);
	}

	public static void glTranslatef(float x, float y, float z) {
		MATRIX_STACK.translate(x, y, z);
		GL11.glTranslatef(x, y, z);
	}
}
