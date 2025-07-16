package turniplabs.examplemod.client.util;

public class Mth {
	public static long encodePosition(int x, int y, int z) {
		long bitsX = (x + (1 << 24L)) & ((1L << 25L) - 1L);
		long bitsY = y & ((1L << 7L) - 1);
		long bitsZ = (z + (1 << 24L)) & ((1L << 25L) - 1L);

		return bitsX << 0L | bitsY << 25L | bitsZ << 32L;
	}

	public static int getX(long position) {
		return (int) ((position >> 0L) & ((1 << 25L) - 1)) - (1 << 24);
	}

	public static int getY(long position) {
		return (int) ((position >> 25L) & ((1 << 6L) - 1));
	}

	public static int getZ(long position) {
		return (int) ((position >> 32L) & ((1 << 25L) - 1)) - (1 << 24);
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
