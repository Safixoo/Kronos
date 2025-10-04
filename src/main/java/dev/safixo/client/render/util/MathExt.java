package dev.safixo.client.render.util;

import dev.safixo.client.render.SectionRender;
import dev.safixo.client.render.util.data.CameraData;
import dev.safixo.client.render.region.RegionRender;

public class MathExt {
	public static float squaredDistance(SectionRender render, CameraData cameraData) {
		float distX = (render.blockX - cameraData.intX + 8) - cameraData.fractX;
		float distY = (render.blockY - cameraData.intY + 8) - cameraData.fractY;
		float distZ = (render.blockZ - cameraData.intZ + 8) - cameraData.fractZ;

		return MathExt.square(distX) + MathExt.square(distY) + MathExt.square(distZ);
	}

	public static float manhattanDistance(RegionRender region, CameraData cameraData) {
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

	public static double square(double num) {
		return num * num;
	}

	public static int floor(float num) {
		int integral = (int) num;
		return num < 0 ? integral - 1 : integral;
	}

	public static int floor(double num) {
		int integral = (int) num;
		return num < 0 ? integral - 1 : integral;
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

	public static double smoothStep(double t) {
		return t * t * (3.0f - 2.0f * t);
	}
}
