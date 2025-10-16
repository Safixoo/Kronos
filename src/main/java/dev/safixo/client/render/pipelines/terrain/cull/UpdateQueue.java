package dev.safixo.client.render.pipelines.terrain.cull;

import dev.safixo.client.render.pipelines.terrain.SectionRender;

public class UpdateQueue {
	public static final SectionRender[] UPDATE_QUEUE = new SectionRender[256];
	public static int UPDATE_POSITION = 0;

	public static void addToQueue(SectionRender render) {
		if (UPDATE_POSITION >= 256) {
			return;
		}

		UPDATE_QUEUE[UPDATE_POSITION++] = render;
	}

	public static void clear() {
		UPDATE_POSITION = 0;
	}

	public static SectionRender get(int pos) {
		return UPDATE_QUEUE[pos];
	}

	public static int size() {
		return UPDATE_POSITION;
	}
}
