package turniplabs.examplemod.client.render.cull;

import turniplabs.examplemod.client.render.SectionRender;

import java.util.Arrays;

public class UpdateQueue {
	public static final SectionRender[] UPDATE_QUEUE = new SectionRender[48];
	public static int UPDATE_POSITION = 0;

	public static void addToQueue(SectionRender render) {
		if (UPDATE_POSITION == 48) {
			return;
		}

		UPDATE_QUEUE[UPDATE_POSITION++] = render;
	}

	public static void addToQueueUnsafe(SectionRender render) {
		UPDATE_QUEUE[UPDATE_POSITION++] = render;
	}

	public static boolean hasSpace() {
		return UPDATE_POSITION < 8;
	}

	public static void clear() {
		Arrays.fill(UPDATE_QUEUE, null);
		UPDATE_POSITION = 0;
	}

	public static SectionRender get(int pos) {
		return UPDATE_QUEUE[pos];
	}

	public static int size() {
		return UPDATE_POSITION;
	}
}
