package turniplabs.examplemod.client.render.cull;

import turniplabs.examplemod.client.render.SectionRender;

import java.util.Arrays;

public class UpdateQueue {
	public static final SectionRender[] UPDATE_QUEUE = new SectionRender[8];
	public static int POSITION = 0;

	public static void addToQueue(SectionRender render) {
		if (POSITION == 8) {
			return;
		}

		UPDATE_QUEUE[POSITION++] = render;
	}

	public static void addToQueueUnsafe(SectionRender render) {
		UPDATE_QUEUE[POSITION++] = render;
	}

	public static boolean hasSpace() {
		return POSITION != 8;
	}

	public static void clear() {
		Arrays.fill(UPDATE_QUEUE, null);
		POSITION = 0;
	}

	public static SectionRender get(int pos) {
		return UPDATE_QUEUE[pos];
	}

	public static int size() {
		return POSITION;
	}
}
