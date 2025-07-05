package turniplabs.examplemod.client.util;

import net.minecraft.core.util.helper.MathHelper;

public class ColorBGRManager {
	private static final float NORMALIZED_TO_INTEGER = 255.0F;
	private static final float INTEGER_TO_NORMALIZED = 1.0F / NORMALIZED_TO_INTEGER;

	public static int packColor(float r, float g, float b) {
		int blue = normToInt(b);
		int green = normToInt(g);
		int red = normToInt(r);

		return (blue << 16) | (green << 8) | (red << 0);
	}

	public static int multiplyColor(int color, float factor) {
		return multiplyColor(color, MathHelper.clamp((int) (factor * 256), 0, 256));
	}

	public static int multiplyColor(int color, int factor) {
		int green =      ((color & 0x00FF00) * factor) >>> 8;
		int redAndBlue = ((color & 0xFF00FF) * factor) >>> 8;

		return (green & 0x00FF00 | redAndBlue & 0xFF00FF);
	}

	public static long multiplyTwoColorsParallel(int colorOne, int colorTwo, int r, int g, int b) {
		long twoColors = colorOne | (long) colorTwo << 32;

		long red    = (twoColors & 0x0000FF000000FFL) * r;
		long green  = (twoColors & 0x00FF000000FF00L) * g;
		long blue   = (twoColors & 0xFF000000FF0000L) * b;

		return (blue & 0xFF000000FF000000L | green & 0xFF000000FF0000L | red & 0x00FF000000FF00L) >>> 8;
	}

	public static int rgbToBgr(int color) {
		return (color & 0xFF) << 16 | (color & 0x00FF00) | ((color & 0xFF0000) >> 16);
	}

	public static int normToInt(float color) {
		return (int) (color * NORMALIZED_TO_INTEGER) & 255;
	}

	public static int intToNorm(int color) {
		return (int) (color * INTEGER_TO_NORMALIZED);
	}
}
