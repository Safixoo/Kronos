package dev.safixo.client.util;

import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;
import dev.safixo.core.HookUtils;
import dev.safixo.core.hooks.RenderGlobalHook;
import net.minecraft.client.settings.GameSettings;

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

	public static int floorDiv(int x, int y) {
		int r = x / y;
		// if the signs are different and modulo not zero, round down
		if ((x ^ y) < 0 && (r * y != x)) {
			r--;
		}
		return r;
	}

	public static float squaredDistanceXYZ(SectionRender render, CameraData cameraData) {
		float distX = (render.blockX - cameraData.intX + 8) - cameraData.fractX;
		float distY = (render.blockY - cameraData.intY + 8) - cameraData.fractY;
		float distZ = (render.blockZ - cameraData.intZ + 8) - cameraData.fractZ;

		return MathExt.square(distX) + MathExt.square(distY) + MathExt.square(distZ);
	}

	public static int manhattanDistanceXYZFast(SectionRender render, int pX, int pY, int pZ) {
		int distX = Math.abs(render.blockX - pX);
		int distY = Math.abs(render.blockY - pY);
		int distZ = Math.abs(render.blockZ - pZ);

		return distX + distY + distZ;
	}

	public static float squaredDistanceXZ(SectionRender render, CameraData cameraData) {
		float distX = (render.blockX - cameraData.intX + 8) - cameraData.fractX;
		float distZ = (render.blockZ - cameraData.intZ + 8) - cameraData.fractZ;

		return MathExt.square(distX) + MathExt.square(distZ);
	}

	public static float squaredDistanceXZ(int posX, int posZ, CameraData cameraData) {
		float distX = (posX - cameraData.intX + 8) - cameraData.fractX;
		float distZ = (posZ - cameraData.intZ + 8) - cameraData.fractZ;

		return MathExt.square(distX) + MathExt.square(distZ);
	}

	public static int getLightmapCoord(int skyLight, int blockLight) {
		return skyLight << 20 | blockLight << 4;
	}

	public static float fma(float a, float b, float c) {
		return a * b + c;
	}

	public static float euclideanDistance(RegionRender region, CameraData cameraData) {
		float distX = (region.blockX() - cameraData.intX) - cameraData.fractX;
		float distZ = (region.blockZ() - cameraData.intZ) - cameraData.fractZ;

		if (region.blockX() < cameraData.intX - RegionRender.RADIUS_X) {
			distX += RegionRender.DIAMETER_X;
		}

		if (region.blockZ() < cameraData.intZ - RegionRender.RADIUS_Z) {
			distZ += RegionRender.DIAMETER_Z;
		}

		return MathExt.square(distX) + MathExt.square(distZ);
	}

	private static final int X_BITS = 22;
	private static final int Z_BITS = 22;
	private static final int Y_BITS = 12;

	public static int decodeX(long pos) {
		return (int) (pos << (64 - X_BITS - 34) >> (64 - X_BITS));
	}

	public static int decodeZ(long pos) {
		return (int) (pos << (64 - Z_BITS - 12) >> (64 - Z_BITS));
	}

	public static int decodeY(long pos) {
		return (int) (pos << (64 - Y_BITS) >> (64 - Y_BITS));
	}

	public static long asLong(int x, int y, int z) {
		return (x & 0x3FFFFFL) << (Z_BITS + Y_BITS) | (z & 0x3FFFFFL) << Y_BITS | y & 0xFFFL;
	}

	public static long asLong(int x, int z) {
		return (x & 0xFFFFFFFFL) << 0L | (z & 0xFFFFFFFFL) << 32L;
	}

	public static double square(double num) {
		return num * num;
	}

	public static int floor(float num) {
		int integral = (int) num;
		return num < 0 ? integral - 1 : integral;
	}

	public static int byteToUnsigned(byte id) {
		return id & 0xFF;
	}

	public static int floor(double num) {
		int integral = (int) num;
		return num < 0 ? integral - 1 : integral;
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

	public static int packedNormal(float normalX, float normalY, float normalZ) {
		int nX = (byte) (normalX * 0x7F);
		int nY = (byte) (normalY * 0x7F);
		int nZ = (byte) (normalZ * 0x7F);

		return (nX & 0xFF) << 0 | (nY & 0xFF) << 8 | (nZ & 0xFF) << 16;
	}

	public static int byteToUnsignedInt(byte x) {
		return x & 0xFF;
	}

	public static int byteToUnsignedInt(int x) {
		return (byte) x & 0xFF;
	}


	public static int posToSectionIntegral(double position) {
		return MathExt.floor(position) >> 4;
	}

	public static double smoothStep(double t) {
		return t * t * (3.0f - 2.0f * t);
	}
}
