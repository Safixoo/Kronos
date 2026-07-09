package dev.safixo.client.render.pipelines.terrain.meshing.data;

import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.data.PrimitivesFlags;

import java.util.Arrays;

import static dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache.*;
import static dev.safixo.client.util.Direction.*;

public class CullSetGenerator {
	private static final short[] QUEUE = new short[16 * 16 * 16];
	private static final byte[] FULL_SOLID = new byte[16 * 16 * 16];

	public static final int VISITED = 0;
	public static final int NOT_VISITED = 1;

	private static int INDEX = 0;

	static {
		Arrays.fill(FULL_SOLID, (byte) NOT_VISITED);
		Arrays.fill(VISITED_CENTER_BLOCKS, (byte) NOT_VISITED);
	}

	private static void restartQueue() {
		System.arraycopy(FULL_SOLID, 0, VISITED_CENTER_BLOCKS, 0, 4096);
		INDEX = 0;
	}

	private static void handlePlayerPosition(SectionCache cache, SectionRender section, CameraData camera) {
		int cameraChunkX = camera.intX >> 4, cameraChunkY = camera.intY >> 4, cameraChunkZ = camera.intZ >> 4;
		int sectionX = section.blockX >> 4, sectionY = section.blockY >> 4, sectionZ = section.blockZ >> 4;
		boolean inside = cameraChunkX == sectionX && cameraChunkY == sectionY && cameraChunkZ == sectionZ;

		if (inside && isVisitable(cache, pack(camera.intX & 15, camera.intY & 15, camera.intZ & 15))) {
			addToQueue(pack(camera.intX & 15, camera.intY & 15, camera.intZ & 15));
		}
	}

	public static int floodFillSection(SectionCache cache, SectionRender section, CameraData camera) {
		restartQueue();
		handlePlayerPosition(cache, section, camera);
		visitStartingEdges(cache);

		int openFaces = 0;
		int index = 0;

		while (index < INDEX) {
			int position = QUEUE[index++];
			int x = (position >>> 0) & 0xF;
			int z = (position >>> 4) & 0xF;
			int y = (position >>> 8);

			{
				if (x == 15) {
					openFaces |= 1 << EAST;
				} else if (isVisitable(cache, position + pack(1, 0, 0))) {
					addToQueue(position + pack(1, 0, 0));
				}
				if (x == 0) {
					openFaces |= 1 << WEST;
				} else if (isVisitable(cache, position - pack(1, 0, 0))) {
					addToQueue(position - pack(1, 0, 0));
				}
			}
			{
				if (y == 15) {
					openFaces |= 1 << UP;
				} else if (isVisitable(cache, position + pack(0, 1, 0))) {
					addToQueue(position + pack(0, 1, 0));
				}
				if (y == 0) {
					openFaces |= 1 << DOWN;
				} else if (isVisitable(cache, position - pack(0, 1, 0))) {
					addToQueue(position - pack(0, 1, 0));
				}
			}
			{
				if (z == 15) {
					openFaces |= 1 << SOUTH;
				} else if (isVisitable(cache, position + pack(0, 0, 1))) {
					addToQueue(position + pack(0, 0, 1));
				}
				if (z == 0) {
					openFaces |= 1 << NORTH;
				} else if (isVisitable(cache, position - pack(0, 0, 1))) {
					addToQueue(position - pack(0, 0, 1));
				}
			}
		}

		return openFaces ^ 0x3F;
	}

	public static void visitStartingEdges(SectionCache cache) {
		int topData = sectionIndex(1, 1 + 1, 1);
		int bottomData = sectionIndex(1, 1 - 1, 1);

		// +Y -Y
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				if (isVisitable(cache, bottomData, pack(x, 15, z)) && isVisitable(cache, pack(x, 0, z))) {
					addToQueue(pack(x, 0, z));
				}

				if (isVisitable(cache, topData, pack(x, 0, z)) && isVisitable(cache, pack(x, 15, z))) {
					addToQueue(pack(x, 15, z));
				}
			}
		}

		int rightData = sectionIndex(1 + 1, 1, 1);
		int leftData = sectionIndex(1 - 1, 1, 1);

		// +X -X
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				if (isVisitable(cache, rightData, pack(0, y, z)) && isVisitable(cache, pack(15, y, z))) {
					addToQueue(pack(15, y, z));
				}

				if (isVisitable(cache, leftData, pack(15, y, z)) && isVisitable(cache, pack(0, y, z))) {
					addToQueue(pack(0, y, z));
				}
			}
		}

		rightData = sectionIndex(1, 1, 1 + 1);
		leftData = sectionIndex(1, 1, 1 - 1);

		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (isVisitable(cache, rightData, pack(x, y, 0)) && isVisitable(cache, pack(x, y, 15))) {
					addToQueue(pack(x, y, 15));
				}

				if (isVisitable(cache, leftData, pack(x, y, 15)) && isVisitable(cache, pack(x, y, 0))) {
					addToQueue(pack(x, y, 0));
				}
			}
		}
	}

	private static void addToQueue(int pack) {
		QUEUE[INDEX++] = (short) pack;
		VISITED_CENTER_BLOCKS[pack] = VISITED;
	}

	private static boolean isVisitable(SectionCache cache, int packed) {
		return isVisitable(cache, SectionCache.sectionIndex(1, 1, 1), packed);
	}

	private static boolean isVisitable(SectionCache cache, int sectionIndex, int packed) {
		return VISITED_CENTER_BLOCKS[packed] == NOT_VISITED && PrimitivesFlags.SOLID_CULL_MASK[cache.getBlockId(sectionIndex, packed)] == 0;
	}


	public static int pack(int x, int y, int z) {
		return makeBlockIndex(x, y, z);
	}
}
