package dev.safixo.client.render.gfx.state;

import dev.safixo.client.util.ColorBGRManager;
import dev.safixo.client.util.MathExt;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

@SuppressWarnings("unused")
public class GlLightColorTracker {
	public static long LAST_COLOR_MATERIAL = -31;
	public static int LAST_CLEAR_COLOR = 0;
	public static int LAST_COLOR = -1;

	private static final int MAX_LIGHTS = 4 + 1;
	private static float R, G, B, A;

	private static final Int2ReferenceOpenHashMap<float[]> LIGHT_MODEL_DATA = new Int2ReferenceOpenHashMap<>();
	private static final Int2ReferenceOpenHashMap<float[]>[] LIGHT_DATA = new Int2ReferenceOpenHashMap[MAX_LIGHTS];

	static {
		for (int i = 0; i < MAX_LIGHTS; i++) {
			LIGHT_DATA[i] = new Int2ReferenceOpenHashMap<>();
		}
	}

	public static void glShadeModel(int mode) {
		if (GlStateTracker.SKIP_CACHE || !GlDrawTracker.RASTER_CACHE) {
			GL11.glShadeModel(mode);
		}

		GlDrawTracker.setBitTracked(mode == GL11.GL_SMOOTH, GlDrawTracker.SMOOTH_MODEL_BIT);
	}

	public static void glClear(int mask) {
		GlStateTracker.flushDrawState();
		GL11.glClear(mask);
	}

	public static void glClearDepth(double depth) {
		GlStateTracker.flushDrawState();
		GL11.glClearDepth(depth);
	}

	public static void glBlendFunc(int sFactor, int dFactor) {
		if (GlStateTracker.SKIP_CACHE || !GlDrawTracker.RASTER_CACHE) {
			GL11.glBlendFunc(sFactor, dFactor);
			return;
		}

		if (sFactor > 0xFFFF || dFactor > 0xFFFF) {
			throw new IllegalArgumentException();
		}

		GlDrawTracker.setBlendFunc(sFactor, dFactor);
	}

	public static void glColor3f(float red, float green, float blue) {
		if (R != red || G != green || B != blue || GlStateTracker.SKIP_CACHE) {
			GlStateTracker.flushDrawState();
			R = red;
			G = green;
			B = blue;
			A = 1.0F;
			GL11.glColor3f(red, green, blue);
		}
	}

	public static void glColor4f(float red, float green, float blue, float alpha) {
		if (alpha == 1.0F) {
			glColor3f(red, green, blue); return;
		}

		if (R != red || G != green || B != blue || A != alpha || GlStateTracker.SKIP_CACHE) {
			GlStateTracker.flushDrawState();
			R = red;
			G = green;
			B = blue;
			A = alpha;
			GL11.glColor4f(red, green, blue, alpha);
		}
	}

	public static void glClearColor(float red, float green, float blue, float alpha) {
		int clearColor = ColorBGRManager.packColor(red, green, blue) | (int) (alpha * 255.0f) << 24;

		if (LAST_CLEAR_COLOR != clearColor || GlStateTracker.SKIP_CACHE) {
			LAST_CLEAR_COLOR = clearColor;
			GL11.glClearColor(red, green, blue, alpha);
		}
	}

	public static void glColorMaterial(int face, int mode) {
		long mask = (face & 0xFFFFFFFFL) << 32L | (mode & 0xFFFFFFFFL);

		if (LAST_COLOR_MATERIAL != mask || GlStateTracker.SKIP_CACHE) {
			GlStateTracker.flushDrawState();
			GL11.glColorMaterial(face, mode);
			LAST_COLOR_MATERIAL = mask;
		}
	}

	public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
		GlStateTracker.flushDrawState();
		GL11.glColorMask(red, green, blue, alpha);
	}

	public static void glLight(int light, int pname, IntBuffer params) {
		float[] bufferData = LIGHT_DATA[light - GL11.GL_LIGHT0].get(pname);

		if (bufferData == null) {
			LIGHT_DATA[light - GL11.GL_LIGHT0].put(pname, bufferData = new float[4]);
			Arrays.fill(bufferData, Float.NaN);
		}

		boolean different = false;

		for (int i = 0; i < params.remaining(); i++) {
			if (bufferData[i] != params.get(params.position() + i)) {
				bufferData[i] = params.get(params.position() + i);
				different = true;
			}
		}

		if (different || GlStateTracker.SKIP_CACHE) {
			GL11.glLight(light, pname, params);
		}
	}

	public static void glLight(int light, int pname, FloatBuffer params) {
		float[] bufferData = LIGHT_DATA[light - GL11.GL_LIGHT0].get(pname);

		if (bufferData == null) {
			LIGHT_DATA[light - GL11.GL_LIGHT0].put(pname, bufferData = new float[4]);
			Arrays.fill(bufferData, Float.NaN);
		}

		float[] paramsArr = new float[4];
		for (int i = 0; i < params.remaining(); i++) {
			paramsArr[i] = params.get(params.position() + i);
		}

		// Saves the transformed vertex by the modelView but not use it for
		// the glLight.
		if (pname == GL11.GL_POSITION) {
			Vector4f vec = new Vector4f();
			vec.set(params);
			vec.mul(GlMatrixTracker.MODEL_VIEW_STACK.top());

			for (int i = 0; i < 4; i++) {
				paramsArr[i] = vec.get(i);
			}
		}

		boolean different = false;

		for (int i = 0; i < params.remaining(); i++) {
			if (!MathExt.equals(bufferData[i], paramsArr[i])) {
				bufferData[i] = paramsArr[i];
				different = true;
			}
		}

		if (different || GlStateTracker.SKIP_CACHE) {
			GL11.glLight(light, pname, params);
		}
	}

	public static void glLightModel(int pname, IntBuffer params) {
		float[] bufferData = LIGHT_MODEL_DATA.get(pname);

		if (bufferData == null) {
			LIGHT_MODEL_DATA.put(pname, bufferData = new float[4]);
			Arrays.fill(bufferData, Float.NaN);
		}

		boolean different = false;

		for (int i = 0; i < params.remaining(); i++) {
			if (bufferData[i] != params.get(params.position() + i)) {
				bufferData[i] = params.get(params.position() + i);
				different = true;
			}
		}

		if (different || GlStateTracker.SKIP_CACHE) {
			GL11.glLightModel(pname, params);
		}
	}

	public static void glLightModel(int pname, FloatBuffer params) {
		float[] bufferData = LIGHT_MODEL_DATA.get(pname);

		if (bufferData == null) {
			LIGHT_MODEL_DATA.put(pname, bufferData = new float[4]);
			Arrays.fill(bufferData, Float.NaN);
		}

		boolean different = false;

		for (int i = 0; i < params.remaining(); i++) {
			if (bufferData[i] != params.get(params.position() + i)) {
				bufferData[i] = params.get(params.position() + i);
				different = true;
			}
		}

		if (different || GlStateTracker.SKIP_CACHE) {
			GL11.glLightModel(pname, params);
		}
	}

	public static void glLighti(int light, int pname, int params) {
		Int2ReferenceOpenHashMap<float[]> lightData = LIGHT_DATA[light - GL11.GL_LIGHT0];
		float[] value = lightData.get(pname);

		if (value == null) {
			lightData.put(pname, value = new float[4]);
			Arrays.fill(value, Float.NaN);
		}

		if (value[0] != params || GlStateTracker.SKIP_CACHE) {
			GL11.glLighti(light, pname, params);
			value[0] = params;
		}
	}

	public static void glLightf(int light, int pname, float params) {
		Int2ReferenceOpenHashMap<float[]> lightData = LIGHT_DATA[light - GL11.GL_LIGHT0];
		float[] value = lightData.get(pname);

		if (value == null) {
			lightData.put(pname, value = new float[4]);
			Arrays.fill(value, Float.NaN);
		}

		if (value[0] != params || GlStateTracker.SKIP_CACHE) {
			GL11.glLightf(light, pname, params);
			value[0] = params;
		}
	}

	public static void glLightModeli(int pname, int params) {
		Int2ReferenceOpenHashMap<float[]> lightData = LIGHT_MODEL_DATA;
		float[] value = lightData.get(pname);

		if (value == null) {
			lightData.put(pname, value = new float[4]);
			Arrays.fill(value, Float.NaN);
		}

		if (value[0] != params || GlStateTracker.SKIP_CACHE) {
			GL11.glLightModeli(pname, params);
			value[0] = params;
		}
	}

	public static void glLightModelf(int pname, float params) {
		Int2ReferenceOpenHashMap<float[]> lightData = LIGHT_MODEL_DATA;
		float[] value = lightData.get(pname);

		if (value == null) {
			lightData.put(pname, value = new float[4]);
			Arrays.fill(value, Float.NaN);
		}

		if (value[0] != params || GlStateTracker.SKIP_CACHE) {
			GL11.glLightModelf(pname, params);
			value[0] = params;
		}
	}
}
