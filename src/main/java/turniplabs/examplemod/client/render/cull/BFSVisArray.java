package turniplabs.examplemod.client.render.cull;

import turniplabs.examplemod.client.util.BitArray;

public class BFSVisArray {
	private static BitArray visArray;

	private static int offsetX;
	private static int offsetZ;

	private static int sizeX;
	private static int sizeZ;

	public static void start(int cameraX, int cameraZ, int renderDistance) {
		int powRenderDistance = nextPowerOfTwo(renderDistance);

		int xzWide = (powRenderDistance * 2) + 1;
		int yLength = 256 / 16;

		int totalVolume = (xzWide * xzWide) * yLength;

		offsetX = cameraX - renderDistance;
		offsetZ = cameraZ - renderDistance;

		int bits = Integer.bitCount(powRenderDistance - 1);

		sizeZ = bits + bits;
		sizeX = bits;

		visArray = new BitArray(totalVolume);
	}

	private static int nextPowerOfTwo(int num) {
		if (num <= 0) return 1;
		num--;
		num |= num >> 1;
		num |= num >> 2;
		num |= num >> 4;
		num |= num >> 8;
		num |= num >> 16;
		return num + 1;
	}

	public static boolean getVisible(int sectionX, int sectionY, int sectionZ) {
		int index = getInd(sectionX, sectionY, sectionZ);

		return visArray.get(index);
	}

	public static void setVisible(int sectionX, int sectionY, int sectionZ) {
		int index = getInd(sectionX, sectionY, sectionZ);

		visArray.set(index);
	}

	private static int getInd(int sectionX, int sectionY, int sectionZ) {
		int relX = sectionX - offsetX;
		int relZ = sectionZ - offsetZ;

		return (relX << sizeX) | (relZ << sizeZ) | sectionY;
	}
}
