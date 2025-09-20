package turniplabs.examplemod.client.util;

import turniplabs.examplemod.client.render.SectionRender;
import turniplabs.examplemod.client.render.data.CameraData;

public class MathExt {
	public static float euclideanDistance(SectionRender render, CameraData cameraData) {
		float distX = (render.blockX - cameraData.intX) - cameraData.fractX;
		float distY = (render.blockY - cameraData.intY) - cameraData.fractY;
		float distZ = (render.blockZ - cameraData.intZ) - cameraData.fractZ;

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
