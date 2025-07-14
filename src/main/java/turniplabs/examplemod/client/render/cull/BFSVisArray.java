package turniplabs.examplemod.client.render.cull;

import turniplabs.examplemod.client.util.BitArray;

public class BFSVisArray {
	private static int lastDistance;
	private static BitArray visArray;

	private static int offsetX;
	private static int offsetZ;

	private static int sizeX;

	public static void start(int cameraX, int cameraZ, int renderDistance) {
		int powRenderDistance = nextPowerOfTwo(renderDistance);

		int xzWide = (powRenderDistance * 2) + 1;
		int yLength = 256 / 16;

		int totalVolume = (xzWide * xzWide) * yLength;

		offsetX = cameraX - powRenderDistance;
		offsetZ = cameraZ - powRenderDistance;

		sizeX = Integer.bitCount(powRenderDistance - 1);

		if (lastDistance == powRenderDistance) {
			visArray.clear();
		} else {
			visArray = new BitArray(totalVolume);
		}

		lastDistance = powRenderDistance;
	}

	private static int nextPowerOfTwo(int num) {
		num--;
		num |= num >> 1;
		num |= num >> 2;
		num |= num >> 4;
		num |= num >> 8;
		num |= num >> 16;
		return num + 1;
	}

	public static boolean notVisible(int sectionX, int sectionY, int sectionZ) {
		int index = getInd(sectionX, sectionY, sectionZ);

		return visArray.getFalse(index);
	}

	public static void setVisible(int sectionX, int sectionY, int sectionZ) {
		int index = getInd(sectionX, sectionY, sectionZ);

		visArray.set(index);
	}

	private static int getInd(int sectionX, int sectionY, int sectionZ) {
		int relX = sectionX - offsetX;
		int relZ = sectionZ - offsetZ;

		if (relX < 0 || relZ < 0) {
			return 0;
		}

		return ((relX << sizeX) | relZ) << sizeX | sectionY;
	}
}
