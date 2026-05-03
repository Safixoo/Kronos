package dev.safixo.client.render.pipelines.terrain.cull;

public class BFSQueue {
	public static final int[] RENDER_INDICES = new int[1 << 16];
	public static final int[] GRAPH_INDICES = new int[1 << 16];

	public static int bfsIndex;
	public static int renderIndex;

	public static void clear() {
		bfsIndex = 0;
		renderIndex = 0;
	}
}
