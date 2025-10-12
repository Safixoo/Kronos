package dev.safixo.client.render.cull;

import dev.safixo.client.render.SectionRender;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;

public class UpdateQueue {
	public static final LongOpenHashSet EXISTENT_CANDIDATES = new LongOpenHashSet(1024);
	public static final SectionRender[] UPDATE_QUEUE = new SectionRender[256];
	public static int UPDATE_POSITION = 0;

	public static void addToQueue(SectionRender render) {
		if (UPDATE_POSITION >= 256 || EXISTENT_CANDIDATES.contains(render.sectionPos)) {
			return;
		}

		UPDATE_QUEUE[UPDATE_POSITION++] = render;
	}

	public static void clear() {
		EXISTENT_CANDIDATES.clear();
		UPDATE_POSITION = 0;
	}

	public static SectionRender get(int pos) {
		return UPDATE_QUEUE[pos];
	}

	public static int size() {
		return UPDATE_POSITION;
	}
}
