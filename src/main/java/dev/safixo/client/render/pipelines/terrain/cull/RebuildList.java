package dev.safixo.client.render.pipelines.terrain.cull;

import dev.safixo.client.render.pipelines.terrain.SectionManager;
import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.CameraData;

import java.util.Arrays;
import java.util.Comparator;

/**
*  Saves a list of dirty to update sections, the list can be saved somewhat random but when retrieving the
 * final array, the array is sorted based in sections distances, this is important as it fixes delays in rebuilding
 * the origin.
 */
public class RebuildList {
	private static final SectionRender[] UPDATE_QUEUE_BFS = new SectionRender[SectionManager.MAX_UPDATE_QUEUES];
	private static int UPDATE_POSITION = 0;

	public static void addToList(SectionRender render) {
		if (UPDATE_POSITION >= SectionManager.MAX_UPDATE_QUEUES) {
			return;
		}

		UPDATE_QUEUE_BFS[UPDATE_POSITION++] = render;
	}

	public static void clear() {
		UPDATE_POSITION = 0;
	}

	public static SectionRender[] getBackedArray() {
		return UPDATE_QUEUE_BFS;
	}

	public static int size() {
		return UPDATE_POSITION;
	}
}
