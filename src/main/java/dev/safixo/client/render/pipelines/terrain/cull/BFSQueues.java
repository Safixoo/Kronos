package dev.safixo.client.render.pipelines.terrain.cull;

import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.CameraData;

public class BFSQueues {
	public static final int[] RENDER_INDICES = new int[1 << 16];
	public static final int[] GRAPH_INDICES = new int[1 << 16];

	private static final int[] UPDATE_QUEUE = new int[WorldManager.MAX_UPDATES_TRIES];

	public static int bfsIndex;

	private static int renderIndex;
	private static int rebuildPosition;

	public static void clear() {
		bfsIndex = 0;
		renderIndex = 0;
		rebuildPosition = 0;
	}

	public static void addToRenderList(int sectionX, int sectionY, int sectionZ) {
		RENDER_INDICES[renderIndex++] = MathExt.asInt(sectionX, sectionY, sectionZ);
	}

	public static void addToRebuildList(int sectionX, int sectionY, int sectionZ) {
		if (rebuildPosition >= WorldManager.MAX_UPDATES_TRIES) {
			return;
		}

		UPDATE_QUEUE[rebuildPosition++] = MathExt.asInt(sectionX, sectionY, sectionZ);
	}

	public static long getSectionPos(CameraData camera, int index) {
		int relativePos = UPDATE_QUEUE[index];

		int posX = MathExt.decodeX(relativePos) + (camera.intX >> 4);
		int posY = MathExt.decodeY(relativePos);
		int posZ = MathExt.decodeZ(relativePos) + (camera.intZ >> 4);

		return MathExt.asLong(posX, posY, posZ);
	}

	public static int getRebuildIndex() {
		return rebuildPosition;
	}

	public static int getRenderIndex() {
		return renderIndex;
	}
}
