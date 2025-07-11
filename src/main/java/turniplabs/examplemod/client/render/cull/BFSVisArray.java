package turniplabs.examplemod.client.render.cull;

import java.util.BitSet;

public class BFSVisArray {
	private static BitSet visArray;

	private static int offsetX;
	private static int offsetZ;

	private static int sizeX;
	private static int sizeZ;

	public static void start(int cameraX, int cameraZ, int renderDistance) {
		int xzWide = (renderDistance * 2) + 1;
		int yLength = 256 / 16;

		int totalVolume = (xzWide * xzWide) * yLength;

		offsetX = cameraX - renderDistance;
		offsetZ = cameraZ - renderDistance;

		sizeZ = renderDistance * renderDistance;
		sizeX = renderDistance;

		visArray = new BitSet(totalVolume);
	}

	public static boolean getVisible(int sectionX, int sectionY, int sectionZ) {
		int index = getInd(sectionX, sectionY, sectionZ);

		return visArray.get(index);
	}

	public static void setVisible(int sectionX, int sectionY, int sectionZ) {
		int index = getInd(sectionX, sectionY, sectionZ);

		visArray.set(index, true);
	}

	private static int getInd(int sectionX, int sectionY, int sectionZ) {
		int relX = sectionX - offsetX;
		int relZ = sectionZ - offsetZ;

		return (relX * sizeX) + (relZ * sizeZ) + sectionY;
	}
}
