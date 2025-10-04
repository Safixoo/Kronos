package dev.safixo.client.render.cull;

import dev.safixo.client.render.util.MathExt;

public class BFSVisArray {
	private static final int MAX_DISTANCE = 32;
	private static final int HOR_SHIFT = Integer.bitCount(MAX_DISTANCE - 1);

	private static int OFFSET_X;
	private static int OFFSET_Z;
	private static final short[] FRAME_ARRAY = new short[MathExt.square((MAX_DISTANCE * 2 + 1) * 16) / Short.BYTES];

	public static void start(int cameraX, int cameraZ, int renderDistance) {
		int powRenderDistance = 32;

		OFFSET_X = cameraX - powRenderDistance;
		OFFSET_Z = cameraZ - powRenderDistance;
	}

	public static boolean notVisible(int sectionX, int sectionY, int sectionZ) {
		int relX = sectionX - OFFSET_X;
		int relZ = sectionZ - OFFSET_Z;

		int realInd = (relX << HOR_SHIFT | relZ) << HOR_SHIFT | sectionY;
		int bitInd = realInd & 0xF;
		int arrInd = realInd >> 4;

		return (FRAME_ARRAY[arrInd] & (1 << bitInd)) == 0;
	}

	public static void setVisible(int sectionX, int sectionY, int sectionZ) {
		int relX = sectionX - OFFSET_X;
		int relZ = sectionZ - OFFSET_Z;

		int realInd = (relX << HOR_SHIFT | relZ) << HOR_SHIFT | sectionY;
		int bitInd = realInd & 0xF;
		int arrInd = realInd >> 4;

		FRAME_ARRAY[arrInd] |= (short) (1 << bitInd);
	}
}
