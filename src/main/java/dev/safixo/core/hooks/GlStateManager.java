package dev.safixo.core.hooks;

import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.render.gfx.util.GpuFlags;
import dev.safixo.client.render.gfx.vertex.GlVertexArrayObject;
import dev.safixo.client.util.ColorBGRManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.*;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

public class GlStateManager {
	public static boolean SKIP_CACHE = false;

	private static final byte[] CAP_BITS = new byte[32827];

	private static final byte UNDEFINED = 0b00;
	private static final byte DEFINED_DISABLED = 0b01;
	private static final byte DEFINED_ENABLED = 0b11;

	public static long LAST_COLOR_MATERIAL = -1;
	public static int LAST_VIEWPORT_WH = -1;
	public static int LAST_ACTIVE_TEXTURE = -1;
	public static int LAST_TEXTURE = -1;
	public static int LAST_COLOR = -1;
	public static int MAT_MODE = GL11.GL_PROJECTION;

	public static void glEnable(int cap) {
		if (SKIP_CACHE) {
			flushDrawState();
			GL11.glEnable(cap);
			return;
		}

		if (CAP_BITS[cap] <= DEFINED_DISABLED) {
			CAP_BITS[cap] = DEFINED_ENABLED;
			flushDrawState();
			GL11.glEnable(cap);
		}
	}

	public static void glDisable(int cap) {
		if (SKIP_CACHE) {
			flushDrawState();
			GL11.glDisable(cap);
			return;
		}

		if (CAP_BITS[cap] == UNDEFINED || CAP_BITS[cap] == DEFINED_ENABLED) {
			CAP_BITS[cap] = DEFINED_DISABLED;
			flushDrawState();
			GL11.glDisable(cap);
		}
	}

	public static void glDepthFunc(int mask) {
		flushDrawState();

		GL11.glDepthFunc(mask);
	}

	public static void glColor3f(float red, float green, float blue) {
		int color = ColorBGRManager.packColor(red, green, blue);

		if (color != LAST_COLOR) {
			LAST_COLOR = color;

			flushDrawState();
			GL11.glColor3f(red, green, blue);
		}
	}

	public static void glColor4f(float red, float green, float blue, float alpha) {
		int color = ColorBGRManager.packColor(red, green, blue);

		if (color != LAST_COLOR) {
			LAST_COLOR = color;

			flushDrawState();
			GL11.glColor4f(red, green, blue, alpha);
		}
	}

	public static void glBindTexture(int target, int texture) {
		flushDrawState();

		if (texture != LAST_TEXTURE) {
			GL11.glBindTexture(target, texture);
			LAST_TEXTURE = texture;
		}
	}

	public static int LAST_VBO_ID = -1;

	public static void glBindBuffer(int target, int id) {
		flushDrawState();

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
		flushDrawState();
		Arrays.fill(CAP_BITS, (byte) 0);

		MAT_MODE = GL11.GL_PROJECTION;
		LAST_COLOR = -1;
		LAST_ACTIVE_TEXTURE = -1;
		LAST_COLOR_MATERIAL = -1;
		LAST_TEXTURE = -1;
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

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixPushEXT(MAT_MODE);
		} else {
			GL11.glPushMatrix();
		}
	}

	public static void glPopMatrix() {
		flushDrawState();

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixPopEXT(MAT_MODE);
		} else {
			GL11.glPopMatrix();
		}
	}

	public static void glFrustum(double left, double right, double bottom, double top, double zNear, double zFar) {
		if (GpuFlags.EXT_DSA) {

		} else {
			GL11.glFrustum(left, right, bottom, top, zNear, zFar);
		}
	}

	public static void glMatrixMode(int mode) {
		flushDrawState();
		MAT_MODE = mode;

		GL11.glMatrixMode(mode);
	}

	public static float glGetFloat(int name) {
		return GL11.glGetFloat(name);
	}

	public static void glBindFramebuffer(int target, int frameBuffer) {
		flushDrawState();
		GL30.glBindFramebuffer(target, frameBuffer);

		CAP_BITS[GL11.GL_DEPTH_TEST] = UNDEFINED;
		CAP_BITS[GL11.GL_BLEND] = UNDEFINED;
		CAP_BITS[GL11.GL_ALPHA_TEST] = UNDEFINED;
		LAST_TEXTURE = -1;
	}

	public static void glGetFloat(int name, FloatBuffer params) {
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
		flushDrawState();
		if (activeTex != LAST_ACTIVE_TEXTURE) {
			GL13.glActiveTexture(activeTex);
			LAST_ACTIVE_TEXTURE = activeTex;
		}
	}

	public static void glGetInteger(int name, IntBuffer buffer) {
		if (name == GL11.GL_VIEWPORT) {
			Minecraft mc = Minecraft.getMinecraft();
			int position = buffer.position();

			buffer.put(mc.displayWidth);
			buffer.put(mc.displayHeight);

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

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixLoadIdentityEXT(MAT_MODE);
		} else {
			GL11.glLoadIdentity();
		}
	}

	public static void glLoadMatrix(FloatBuffer matrix) {
		flushDrawState();

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixLoadEXT(MAT_MODE, matrix);
		} else {
			GL11.glMultMatrix(matrix);
		}
	}

	public static void glColorMaterial(int face, int mode) {
		flushDrawState();
		long mask = (face & 0xFFFFFFFFL) << 32L | (mode & 0xFFFFFFFFL);

		if (LAST_COLOR_MATERIAL != mask) {
			GL11.glColorMaterial(face, mode);
			LAST_COLOR_MATERIAL = mask;
		}
	}

	public static void glMultMatrix(FloatBuffer matrix) {
		flushDrawState();

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixMultEXT(MAT_MODE, matrix);
		} else {
			GL11.glMultMatrix(matrix);
		}
	}

	public static void glScalef(float x, float y, float z) {
		flushDrawState();

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixScalefEXT(MAT_MODE, x, y, z);
		} else {
			GL11.glScalef(x, y, z);
		}
	}

	public static void glScaled(double x, double y, double z) {
		glScalef((float) x, (float) y, (float) z);
	}

	public static void glRotated(double angle, double x, double y, double z) {
		glRotatef((float) angle, (float) x, (float) y, (float) z);
	}

	public static void glRotatef(float angle, float x, float y, float z) {
		flushDrawState();

		if (GpuFlags.EXT_DSA) {
			EXTDirectStateAccess.glMatrixRotatefEXT(MAT_MODE, angle, x, y, z);
		} else {
			GL11.glRotatef(angle, x, y, z);
		}
	}

	public static void glTranslatef(float x, float y, float z) {
		flushDrawState();

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

	public static void flushDrawState() {
		if (Tessellator.instance.getClass() == ImprovedTessellator.class) {
			ImprovedTessellator tes = (ImprovedTessellator) Tessellator.instance;
			tes.flushState();
		}
	}
}
