package dev.safixo.client.render.gfx.state;

import dev.safixo.client.util.collection.Matrix4Stack;
import dev.safixo.client.util.memory.NativeBuffer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

import java.nio.FloatBuffer;

@SuppressWarnings("unused")
public class GlMatrixTracker {
	public static final Matrix4Stack MODEL_VIEW_STACK = new Matrix4Stack(256);
	public static final Matrix4Stack PROJECTION_STACK = new Matrix4Stack(256);
	public static final Matrix4Stack TEXTURE_STACK = new Matrix4Stack(8);
	public static final Matrix4Stack NULL_STACK = new Matrix4Stack(256);

	public static Matrix4Stack CURRENT_STACK = PROJECTION_STACK;
	public static int MAT_MODE = GL11.GL_PROJECTION_MATRIX;

	private static final Matrix4f TEMP_MATRIX = new Matrix4f();

	public static final boolean EMULATE_STACK = false;
	private static boolean STACK_CHANGED = false;

	public static void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
		CURRENT_STACK.top().mul(TEMP_MATRIX.setPerspective((float) Math.toRadians(fovy), aspect, zNear, zFar));
		STACK_CHANGED = true;

		if (!EMULATE_STACK) {
			Project.gluPerspective(fovy, aspect, zNear, zFar);
		}
	}

	public static void glOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
		GlStateTracker.flushDrawState();
		CURRENT_STACK.top().ortho((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);
		STACK_CHANGED = true;

		if (!EMULATE_STACK) {
			GL11.glOrtho(left, right, bottom, top, zNear, zFar);
		}
	}

	public static void glFrustum(double left, double right, double bottom, double top, double zNear, double zFar) {
		GlStateTracker.flushDrawState();
		CURRENT_STACK.top().frustum((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);
		STACK_CHANGED = true;

		if (!EMULATE_STACK) {
			GL11.glFrustum(left, right, bottom, top, zNear, zFar);
		}
	}

	public static void glPushMatrix() {
		GlStateTracker.flushDrawState();
		CURRENT_STACK.push();
		STACK_CHANGED = true;

		if (!EMULATE_STACK) {
			GL11.glPushMatrix();
		}
	}

	public static void glPopMatrix() {
		GlStateTracker.flushDrawState();
		CURRENT_STACK.pop();
		STACK_CHANGED = true;

		if (!EMULATE_STACK) {
			GL11.glPopMatrix();
		}
	}

	private static final FloatBuffer BUFFER = NativeBuffer.memAllocFloat(16);

	public static void loadCurrentMatrix() {
		if (EMULATE_STACK && STACK_CHANGED) {
			STACK_CHANGED = false;

			if (CURRENT_STACK == TEXTURE_STACK) {
				GlTextureTracker.assertActiveTexture();

				if (GlTextureTracker.ACTIVE_UNIT == 0) {
					return;
				}
			}

			GL11.glLoadMatrix(GlMatrixTracker.CURRENT_STACK.top().get(BUFFER));
		}
	}

	private static boolean isMatrixIdentity(Matrix4f matrix) {
		return (matrix.properties() & Matrix4f.PROPERTY_IDENTITY) != 0;
	}

	public static void glMatrixMode(int mode) {
		if (MAT_MODE != mode || GlStateTracker.SKIP_CACHE) {
			loadCurrentMatrix();
			GlStateTracker.flushDrawState();

			if (mode == GL11.GL_PROJECTION) {
				CURRENT_STACK = PROJECTION_STACK;
			} else if (mode == GL11.GL_MODELVIEW) {
				CURRENT_STACK = MODEL_VIEW_STACK;
			} else if (mode == GL11.GL_TEXTURE) {
				CURRENT_STACK = TEXTURE_STACK;
			} else {
				CURRENT_STACK = NULL_STACK;
			}

			STACK_CHANGED = true;
			MAT_MODE = mode;
			GL11.glMatrixMode(mode);
		}
	}

	public static void glLoadIdentity() {
		GlStateTracker.flushDrawState();
		CURRENT_STACK.top().identity();
		STACK_CHANGED = true;

		if (!EMULATE_STACK) {
			GL11.glLoadIdentity();
		}
	}

	public static void glLoadMatrix(FloatBuffer matrix) {
		GlStateTracker.flushDrawState();
		CURRENT_STACK.top().set(matrix);
		STACK_CHANGED = true;

		if (!EMULATE_STACK) {
			GL11.glLoadMatrix(matrix);
		}
	}

	public static void glMultMatrix(FloatBuffer matrix) {
		GlStateTracker.flushDrawState();
		CURRENT_STACK.top().mul(TEMP_MATRIX.set(matrix));
		STACK_CHANGED = true;

		if (!EMULATE_STACK) {
			GL11.glMultMatrix(matrix);
		}
	}

	public static void glScaled(double x, double y, double z) {
		glScalef((float) x, (float) y, (float) z);
	}

	public static void glRotated(double angle, double x, double y, double z) {
		glRotatef((float) angle, (float) x, (float) y, (float) z);
	}

	public static void glScalef(float x, float y, float z) {
		GlStateTracker.flushDrawState();
		CURRENT_STACK.top().scale(x, y, z);
		STACK_CHANGED = true;

		if (!EMULATE_STACK) {
			GL11.glScalef(x, y, z);
		}
	}

	public static void glRotatef(float angle, float x, float y, float z) {
		GlStateTracker.flushDrawState();

		// Normalize the vector for JOML.
		if (Math.abs((x + y + z) - 1.0f) > 0.0005f) { // dirty check.
			float norm = 1.0f / (float) Math.sqrt(x * x + y * y + z * z);
			x *= norm;
			y *= norm;
			z *= norm;
		}

		// the angle passed in glRotatef is in degrees but JOML accepts in radians.
		CURRENT_STACK.top().rotate(angle * 3.14159265358979f / 180.0f, x, y, z);
		STACK_CHANGED = true;

		if (!EMULATE_STACK) {
			GL11.glRotatef(angle, x, y, z);
		}
	}

	public static void glTranslatef(float x, float y, float z) {
		GlStateTracker.flushDrawState();
		CURRENT_STACK.top().translate(x, y, z);
		STACK_CHANGED = true;

		if (!EMULATE_STACK) {
			GL11.glTranslatef(x, y, z);
		}
	}

}
