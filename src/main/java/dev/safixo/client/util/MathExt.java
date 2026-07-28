package dev.safixo.client.util;

import dev.safixo.client.render.pipelines.terrain.region.RegionConstants;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;
import dev.safixo.core.HookUtils;
import dev.safixo.core.hooks.RenderGlobalHook;
import net.minecraft.client.settings.GameSettings;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.lang.reflect.Field;

public class MathExt {
	private static final Field OF_DISTANCE = HookUtils.getField(GameSettings.class, "ofRenderDistanceFine", "ofRenderDistanceFine");

	public static int getCanonicalRenderDistance(GameSettings gameSettings) {
		int realRenderDistance;

		if (!RenderGlobalHook.OPTIFINE_ACTIVE) {
			// This is more or less the real metric for chunk distance that the game uses.
			// 0 - Far, 1 - Normal, 2 - Short, 3 - Tiny.
			realRenderDistance = Math.min(400, (64 << (3 - gameSettings.renderDistance))) >> 5;
		} else {
			realRenderDistance = (Integer) HookUtils.getFieldValue(OF_DISTANCE, gameSettings) >> 4;
		}

		return realRenderDistance;
	}

	public static boolean equals(float a, float b) {
		return Math.abs(a - b) < (1.0f / 8192.0f);
	}

	public static boolean equals(double a, double b) {
		return Math.abs(a - b) < (1.0f / 8192.0f);
	}

	public static int nextPOT(int a) {
		a--;

		a |= a >> 1;
		a |= a >> 2;
		a |= a >> 4;
		a |= a >> 8;
		a |= a >> 16;

		a++;
		return a;
	}

	public static int getLightmapCoord(int skyLight, int blockLight) {
		return (skyLight & 0xF) << 20 | (blockLight & 0xF) << 4;
	}

	public static int getSkylight(int light) {
		return light >>> 20 & 0xF;
	}

	public static int getBlocklight(int light) {
		return light >>> 4 & 0xF;
	}

	public static float fma(float a, float b, float c) {
		return a * b + c;
	}

	public static float euclideanDistance(RegionRender region, CameraData cameraData) {
		float distX = (region.blockX() - cameraData.intX) - cameraData.fractX;
		float distZ = (region.blockZ() - cameraData.intZ) - cameraData.fractZ;

		if (region.blockX() < cameraData.intX - RegionConstants.RADIUS_X) {
			distX += RegionConstants.DIAMETER_X;
		}

		if (region.blockZ() < cameraData.intZ - RegionConstants.RADIUS_Z) {
			distZ += RegionConstants.DIAMETER_Z;
		}

		return MathExt.square(distX) + MathExt.square(distZ);
	}

	private static final int X_BITS = 22;
	private static final int Z_BITS = 22;
	private static final int Y_BITS = 12;

	public static int decodeX(long pos) {
		return (int) (pos << (64 - Y_BITS - Z_BITS - X_BITS) >> (64 - X_BITS));
	}

	public static int decodeZ(long pos) {
		return (int) (pos << (64 - Y_BITS - Z_BITS) >> (64 - Z_BITS));
	}

	public static int decodeY(long pos) {
		return (int) (pos << (64 - Y_BITS) >> (64 - Y_BITS));
	}

	public static long asLong(int x, int y, int z) {
		return (x & 0x3FFFFFL) << (Z_BITS + Y_BITS) | (z & 0x3FFFFFL) << Y_BITS | y & 0xFFFL;
	}

	public static int floorDiv(int a, int b) {
		int r = a / b;
		// if the signs are different and modulo not zero, round down
		if ((a ^ b) < 0 && (r * b != a)) {
			r--;
		}
		return r;
	}

	public static int decodeX(int pos) {
		return (pos << 12) >> 22;
	}

	public static int decodeY(int pos) {
		return pos & 0x3FF;
	}

	public static int decodeZ(int pos) {
		return (pos << 2) >> 22;
	}

	public static int asInt(int x, int y, int z) {
		return y | (x & 0x3FF) << 10 | z << 20;
	}

	public static long asLong(int x, int z) {
		return (x & 0xFFFFFFFFL) << 0L | (z & 0xFFFFFFFFL) << 32L;
	}

	public static double square(double num) {
		return num * num;
	}

	public static int floor(float num) {
		int integral = (int) num;
		return num < integral ? integral - 1 : integral;
	}

	public static int byteToUnsigned(byte id) {
		return id & 0xFF;
	}

	public static int floor(double num) {
		int integral = (int) num;
		return num < integral ? integral - 1 : integral;
	}

	public static int ceilDiv(int a, int b) {
		return (a + b - 1) / b;
	}

	public static double floorMod(double num, double mod) {
		return num - (floor(num / mod) * mod);
	}

	public static float clamp(float value, float min, float max) {
		return Math.max(Math.min(value, max), min);
	}

	public static double clamp(double value, double min, double max) {
		return Math.max(Math.min(value, max), min);
	}

	public static int clamp(int value, int min, int max) {
		return Math.max(Math.min(value, max), min);
	}

 	public static float square(float num) {
		return num * num;
	}

	public static int sign(int a) {
		return (a >> 31) | 1;
	}

	public static int mulSign(int a, int b) {
		int mask = b >> 31;
		return (a ^ mask) - mask;
	}

	public static int square(int num) {
		return num * num;
	}

	public static long lerp(long start, long end, double t) {
		return (long) (start + (end - start) * t);
	}

	public static float lerp(float start, float end, float t) {
		return (start + (end - start) * t);
	}

	public static double lerp(double start, double end, double t) {
		return (start + (end - start) * t);
	}

	static final float PI = 3.1415927f;
	static final float PI_2 = PI / 2f;

	public static float fastAtan2(float y, float x) {
		float ax = x >= 0.0 ? x : -x, ay = y >= 0.0 ? y : -y;
		float a = ay > ax ? ax / ay : ay / ax;
		float s = a * a;
		float r = ((-0.0464964749F * s + 0.15931422F) * s - 0.327622764F) * s * a + a;

		if (ay > ax) {
			r = PI_2 - r;
		}
		if (x < 0.0) {
			r = PI - r;
		}

		return y >= 0 ? r : -r;
	}

	public static int packedNormal(float normalX, float normalY, float normalZ) {
		int nX = (byte) (normalX * 0x7F);
		int nY = (byte) (normalY * 0x7F);
		int nZ = (byte) (normalZ * 0x7F);

		return (nX & 0xFF) << 0 | (nY & 0xFF) << 8 | (nZ & 0xFF) << 16;
	}

	public static Vector3f unpackNormal(int normal, Vector3f normalVec) {
		normalVec.x = ((normal & 0xFF) - 128) * (1.0f / 0x7F);
		normal >>>= 8;
		normalVec.y = ((normal & 0xFF) - 128) * (1.0f / 0x7F);
		normal >>>= 8;
		normalVec.z = ((normal & 0xFF) - 128) * (1.0f / 0x7F);

		return normalVec;
	}

	public static int posToSectionIntegral(double position) {
		return MathExt.floor(position) >> 4;
	}

	public static double smoothStep(double t) {
		return t * t * (3.0f - 2.0f * t);
	}
}
