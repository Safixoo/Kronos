package dev.safixo.core.hooks;

import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.render.gfx.util.GpuFlags;
import dev.safixo.client.util.ColorBGRManager;
import dev.safixo.client.util.Matrix4Stack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.Project;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

@SuppressWarnings("unused")
public class GlStateTracker {
	public static final boolean SKIP_CACHE = true;
	private static final byte[] CAP_BITS = new byte[32827];

	private static final byte UNDEFINED = 0b01;
	private static final byte DEFINED_DISABLED = 0b00;
	private static final byte DEFINED_ENABLED = 0b11;

	public static long LAST_COLOR_MATERIAL = -31;
	public static int LAST_VIEWPORT_WH = -1;
	public static int LAST_UNIT = -1;
	public static int LAST_COLOR = -1;
	public static int LAST_DEPTH_FUNC = -1;

	public static int CURRENT_UNIT = 0;
	private static final int[] TEXTURE_PER_UNIT = new int[10];

	public static int FOG_MODE;
	public static float FOG_START, FOG_END, FOG_DENSITY;
	public static float FOG_COLOR_R, FOG_COLOR_G, FOG_COLOR_B;

	private static final Matrix4Stack MODEL_VIEW_STACK = new Matrix4Stack(256);
	private static final Matrix4Stack PROJECTION_STACK = new Matrix4Stack(256);
	private static final Matrix4Stack TEXTURE_STACK = new Matrix4Stack(8);
	private static final Matrix4Stack NULL_STACK = new Matrix4Stack(256);

	public static Matrix4Stack CURRENT_STACK = PROJECTION_STACK;
	public static String VENDOR;
	public static int MAT_MODE = GL11.GL_PROJECTION_MATRIX;

	private static final Matrix4f MATRIX = new Matrix4f();

	static {
		Arrays.fill(CAP_BITS, UNDEFINED);
	}

	public static void glEnable(int cap) {
		// Skips GL_TEXTURE_2D caching as it depends on the current tex unit,
		// and it isn't worth the trouble.
		if (CAP_BITS[cap] <= UNDEFINED || cap == GL11.GL_TEXTURE_2D) {
			if (!SKIP_CACHE) {
				CAP_BITS[cap] = DEFINED_ENABLED;
			}
			flushDrawState();
			GL11.glEnable(cap);
		}
	}

	public static void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
		CURRENT_STACK.top().mul(MATRIX.setPerspective((float) Math.toRadians(fovy), aspect, zNear, zFar));
		Project.gluPerspective(fovy, aspect, zNear, zFar);
	}

	public static void update(boolean processMessages) {
		flushDrawState();
		Display.update(processMessages);
	}

	public static void update() {
		flushDrawState();
		Display.update();
	}

	public static void glDisable(int cap) {
		// Skips GL_TEXTURE_2D caching as it depends on the current tex unit,
		// and it isn't worth the trouble.
		if (CAP_BITS[cap] >= UNDEFINED || cap == GL11.GL_TEXTURE_2D) {
			if (!SKIP_CACHE) {
				CAP_BITS[cap] = DEFINED_DISABLED;
			}
			flushDrawState();
			GL11.glDisable(cap);
		}
	}

	public static void glDepthFunc(int mask) {
		if (mask != LAST_DEPTH_FUNC) {
			LAST_DEPTH_FUNC = mask;
			flushDrawState();
			GL11.glDepthFunc(mask);
		}
	}

	public static void glColor3f(float red, float green, float blue) {
		int color = ColorBGRManager.packColor(red, green, blue) | LAST_COLOR & 0xFF_000000;

		if (color != LAST_COLOR) {
			LAST_COLOR = color;

			flushDrawState();
			GL11.glColor3f(red, green, blue);
		}
	}

	public static void glColor4f(float red, float green, float blue, float alpha) {
		int color = ColorBGRManager.packColor(red, green, blue) | ((int) (alpha * 255.0f) << 24);

		if (color != LAST_COLOR) {
			LAST_COLOR = color;
			flushDrawState();
			GL11.glColor4f(red, green, blue, alpha);
		}
	}

	public static void glBindTexture(int target, int texture) {
		flushDrawState();

		if (CURRENT_UNIT == -1) {
			GL11.glBindTexture(target, texture);
		} else if (texture != TEXTURE_PER_UNIT[CURRENT_UNIT]) {
			GL11.glBindTexture(target, texture);
			TEXTURE_PER_UNIT[CURRENT_UNIT] = texture;
		}
	}

	public static int LAST_VBO_ID = -1;

	public static void glBindBuffer(int target, int id) {
		if (target == GL15.GL_ARRAY_BUFFER) {
			if (id == LAST_VBO_ID) {
				return;
			}

			LAST_VBO_ID = id;
		}

		GL15.glBindBuffer(target, id);
	}

	public static void glPopAttrib() {
		reset();
		GL11.glPopAttrib();
	}

	public static void glPushAttrib(int mask) {
		reset();
		GL11.glPushAttrib(mask);
	}

	public static void glClear(int mask) {
		flushDrawState();
		GL11.glClear(mask);
	}

	public static void reset() {
		Arrays.fill(CAP_BITS, UNDEFINED);

		MAT_MODE = GL11.GL_MODELVIEW;
		LAST_COLOR = -1;
		LAST_DEPTH_FUNC = -1;
		CURRENT_UNIT = -1;
		LAST_COLOR_MATERIAL = -1;
	}

	public static void glDisableClientStateDirect(int cap) {
		GL11.glDisableClientState(cap);
	}

	public static void glEnableClientStateDirect(int cap) {
		GL11.glEnableClientState(cap);
	}

	public static void glDisableClientState(int cap) {
		GL11.glDisableClientState(cap);
	}

	public static void glEnableClientState(int cap) {
		GL11.glEnableClientState(cap);
	}

	public static void glPushMatrix() {
		flushDrawState();
		CURRENT_STACK.push();

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixPushEXT(MAT_MODE);
		} else {
			GL11.glPushMatrix();
		}
	}

	public static void glPopMatrix() {
		flushDrawState();
		CURRENT_STACK.pop();

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixPopEXT(MAT_MODE);
		} else {
			GL11.glPopMatrix();
		}
	}

	public static void glOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
		flushDrawState();
		CURRENT_STACK.top().ortho((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);
		GL11.glOrtho(left, right, bottom, top, zNear, zFar);
	}

	public static void glFrustum(double left, double right, double bottom, double top, double zNear, double zFar) {
		flushDrawState();
		CURRENT_STACK.top().frustum((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);
		GL11.glFrustum(left, right, bottom, top, zNear, zFar);
	}

	public static void glMatrixMode(int mode) {
		if (MAT_MODE != mode) {
			flushDrawState();

			if (mode == GL11.GL_PROJECTION) {
				CURRENT_STACK = PROJECTION_STACK;
			}
			else if (mode == GL11.GL_MODELVIEW) {
				CURRENT_STACK = MODEL_VIEW_STACK;
			}
			else if (mode == GL11.GL_TEXTURE) {
				CURRENT_STACK = TEXTURE_STACK;
			}
			else {
				CURRENT_STACK = NULL_STACK;
			}

			MAT_MODE = mode;
			GL11.glMatrixMode(mode);
		}
	}

	public static float glGetFloat(int name) {
		return GL11.glGetFloat(name);
	}

	public static void glBindFramebuffer(int target, int frameBuffer) {
		flushDrawState();
		GL30.glBindFramebuffer(target, frameBuffer);
	}

	public static void glGetFloat(int name, FloatBuffer params) {
		if (VENDOR == null) {
			VENDOR = GL11.glGetString(GL11.GL_VENDOR);
		}

		if (VENDOR != null && VENDOR.contains("Nvidia")) {
			if (name == GL11.GL_MODELVIEW_MATRIX) {
				MODEL_VIEW_STACK.top().get(params);
				return;
			}
			else if (name == GL11.GL_PROJECTION_MATRIX) {
				PROJECTION_STACK.top().get(params);
				return;
			}
		}

		GL11.glGetFloat(name, params);
	}

	public static void glViewport(int x, int y, int width, int height) {
		int mask = width | height << 16;

		if (mask != LAST_VIEWPORT_WH) {
			LAST_VIEWPORT_WH = mask;
			GL11.glViewport(x, y, width, height);
		}
	}

	public static int glGetInteger(int name) {
		return GL11.glGetInteger(name);
	}

	public static void glActiveTexture(int activeTex) {
		if (activeTex != CURRENT_UNIT) {
			flushDrawState();
			GL13.glActiveTexture(activeTex);
			CURRENT_UNIT = activeTex;
		}
	}

	public static void glGetInteger(int name, IntBuffer buffer) {
		if (name == GL11.GL_VIEWPORT) {
			int position = buffer.position();
			buffer.put(0);
			buffer.put(0);
			buffer.put(LAST_VIEWPORT_WH & 0xFFFF);
			buffer.put(LAST_VIEWPORT_WH >>> 16);
			((Buffer) buffer).position(position);
			return;
		}

		GL11.glGetInteger(name, buffer);
	}

	public static void glFlush() {
		// NO-OP
	}

	public static void glDepthMask(boolean mask) {
		flushDrawState();
		GL11.glDepthMask(mask);
	}

	public static void glBegin(int mode) {
		flushDrawState();
		GL11.glBegin(mode);
	}

	public static void glEndList() {
		flushDrawState();
		GL11.glEndList();
	}

	public static void glLoadIdentity() {
		flushDrawState();
		CURRENT_STACK.top().identity();

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixLoadIdentityEXT(MAT_MODE);
		} else {
			GL11.glLoadIdentity();
		}
	}

	public static void glLoadMatrix(FloatBuffer matrix) {
		flushDrawState();
		CURRENT_STACK.top().set(matrix);

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixLoadEXT(MAT_MODE, matrix);
		} else {
			GL11.glMultMatrix(matrix);
		}
	}

	public static void glColorMaterial(int face, int mode) {
		long mask = (face & 0xFFFFFFFFL) << 32L | (mode & 0xFFFFFFFFL);

		if (LAST_COLOR_MATERIAL != mask) {
			flushDrawState();
			GL11.glColorMaterial(face, mode);
			LAST_COLOR_MATERIAL = mask;
		}
	}

	public static void glMultMatrix(FloatBuffer matrix) {
		flushDrawState();
		CURRENT_STACK.top().mul(MATRIX.set(matrix));

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixMultEXT(MAT_MODE, matrix);
		} else {
			GL11.glMultMatrix(matrix);
		}
	}

	public static void glScalef(float x, float y, float z) {
		if (CAP_BITS[GL12.GL_RESCALE_NORMAL] == DEFINED_ENABLED) {
			return;
		}

		flushDrawState();
		CURRENT_STACK.top().scale(x, y, z);

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixScalefEXT(MAT_MODE, x, y, z);
		} else {
			GL11.glScalef(x, y, z);
		}
	}

	public static void glShadeModel(int mode) {
		flushDrawState();
		GL11.glShadeModel(mode);
	}

	public static void glScaled(double x, double y, double z) {
		glScalef((float) x, (float) y, (float) z);
	}

	public static void glRotated(double angle, double x, double y, double z) {
		glRotatef((float) angle, (float) x, (float) y, (float) z);
	}

	public static void glRotatef(float angle, float x, float y, float z) {
		flushDrawState();

		// Normalize the vector for JOML.
		if (Math.abs((x + y + z) - 1.0f) > 0.0005f) { // dirty check.
			float norm = 1.0f / (float) Math.sqrt(x * x + y * y + z * z);
			x *= norm;
			y *= norm;
			z *= norm;
		}

		// the angle passed in glRotatef is in degrees but JOML accepts in radians.
		CURRENT_STACK.top().rotate(angle * 3.14159265358979f / 180.0f, x, y, z);

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixRotatefEXT(MAT_MODE, angle, x, y, z);
		} else {
			GL11.glRotatef(angle, x, y, z);
		}
	}

	public static void glTranslatef(float x, float y, float z) {
		flushDrawState();

		CURRENT_STACK.top().translate(x, y, z);

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixTranslatefEXT(MAT_MODE, x, y, z);
		} else {
			GL11.glTranslatef(x, y, z);
		}
	}

	public static void glCallList(int list) {
		flushDrawState();
		GL11.glCallList(list);
	}

	public static void glNewList(int list, int drawMode) {
		flushDrawState();
		GL11.glNewList(list, drawMode);
	}

	public static void glColorMask(boolean a, boolean b, boolean c, boolean d) {
		flushDrawState();
		GL11.glColorMask(a, b, c, d);
	}

	public static void glBlendFunc(int a, int b) {
		flushDrawState();
		GL11.glBlendFunc(a, b);
	}

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

	@SuppressWarnings("ConstantValue")
	public static void flushDrawState() {
		if (ImprovedTessellator.TESSELLATOR != null) {
			ImprovedTessellator.TESSELLATOR.flushState();
		}
	}
}
