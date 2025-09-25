package turniplabs.examplemod.client.render.util;

import turniplabs.examplemod.client.render.SectionRender;
import turniplabs.examplemod.client.render.util.data.CameraData;
import turniplabs.examplemod.client.render.region.RegionRender;

public class MathExt {
	public static float squaredDistance(SectionRender render, CameraData cameraData) {
		float distX = (render.blockX - cameraData.intX + 8) - cameraData.fractX;
		float distY = (render.blockY - cameraData.intY + 8) - cameraData.fractY;
		float distZ = (render.blockZ - cameraData.intZ + 8) - cameraData.fractZ;

		return MathExt.square(distX) + MathExt.square(distY) + MathExt.square(distZ);
	}

	public static float squaredDistance(RegionRender region, CameraData cameraData) {
		float distX = (region.centerBlockX() - cameraData.intX) - cameraData.fractX;
		float distY = (region.centerBlockY() - cameraData.intY) - cameraData.fractY;
		float distZ = (region.centerBlockZ() - cameraData.intZ) - cameraData.fractZ;

		return MathExt.square(distX) + MathExt.square(distY) + MathExt.square(distZ);
	}

	public static double square(double num) {
		return num * num;
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
