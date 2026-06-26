package dev.safixo.client.render.pipelines.terrain.cull;

import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.CameraData;

/**
*  Saves a list of dirty to update sections, the list can be saved somewhat random but when retrieving the
 * final array, the array is sorted based in sections distances, this is important as it fixes delays in rebuilding
 * the origin.
 */
public class RebuildList {
	private static final int[] UPDATE_QUEUE = new int[WorldManager.MAX_UPDATES_TRIES];
	private static int UPDATE_POSITION = 0;

	public static void addToList(int section) {
		if (UPDATE_POSITION >= WorldManager.MAX_UPDATES_TRIES) {
			return;
		}

		UPDATE_QUEUE[UPDATE_POSITION++] = section;
	}

	public static void clear() {
		UPDATE_POSITION = 0;
	}

	public static long getSectionPos(CameraData camera, int index) {
		int relativePos = UPDATE_QUEUE[index];

		int posX = MathExt.decodeX(relativePos) + (camera.intX >> 4);
		int posY = MathExt.decodeY(relativePos);
		int posZ = MathExt.decodeZ(relativePos) + (camera.intZ >> 4);

		return MathExt.asLong(posX, posY, posZ);
	}

	public static int size() {
		return UPDATE_POSITION;
	}
}
