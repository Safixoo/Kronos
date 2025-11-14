package dev.safixo.client.render.pipelines.terrain.cull;

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
	private static final SectionSorter SECTION_SORTER = new SectionSorter();

	private static final SectionRender[] UPDATE_QUEUE_BFS = new SectionRender[384];
	private static int UPDATE_POSITION = 0;

	public static void addToList(SectionRender render) {
		if (UPDATE_POSITION >= 128) {
			return;
		}

		// Try to at least minimize duplicated queued sections.
		if (render == UPDATE_QUEUE_BFS[UPDATE_POSITION]) {
			return;
		}

		UPDATE_QUEUE_BFS[UPDATE_POSITION++] = render;
	}

	public static void clear() {
		UPDATE_POSITION = 0;
	}

	public static SectionRender[] getBackedArray(final CameraData camera) {
		SECTION_SORTER.setCamera(camera);
		Arrays.sort(UPDATE_QUEUE_BFS, 0, UPDATE_POSITION, SECTION_SORTER);

		return UPDATE_QUEUE_BFS;
	}

	public static int size() {
		return UPDATE_POSITION;
	}

	private static class SectionSorter implements Comparator<SectionRender> {
		public int playerX, playerY, playerZ;

		public SectionSorter() {}

		public void setCamera(CameraData camera) {
			this.playerX = camera.intX;
			this.playerY = camera.intY;
			this.playerZ = camera.intZ;
		}

		@Override
		public int compare(SectionRender t1, SectionRender t2) {
			int distance1 = MathExt.manhattanDistanceXYZFast(t1, this.playerX, this.playerY, this.playerZ);
			int distance2 = MathExt.manhattanDistanceXYZFast(t2, this.playerX, this.playerY, this.playerZ);

			return Integer.signum(distance1 - distance2);
		}
	}
}
