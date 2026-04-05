package dev.safixo.client.render.pipelines.terrain.meshing.data;

import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.data.PrimitivesFlags;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;

import java.util.Arrays;

import static dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache.*;

public class CullSetGenerator {
	public static final ShortArrayList QUEUE = new ShortArrayList();
	private static final byte[] FULL_SOLID = new byte[4096];

	static {
		Arrays.fill(FULL_SOLID, (byte) 1);
	}

	public static int floodFillSection(SectionRender section, CameraData camera) {
		System.arraycopy(FULL_SOLID, 0, VISITED_CENTER_BLOCKS, 0, 4096);
		QUEUE.clear();

		int cameraChunkX = camera.intX >> 4, cameraChunkY = camera.intY >> 4, cameraChunkZ = camera.intZ >> 4;
		int sectionX = section.blockX >> 4, sectionY = section.blockY >> 4, sectionZ = section.blockZ >> 4;
		boolean inside = cameraChunkX == sectionX && cameraChunkY == sectionY && cameraChunkZ == sectionZ;

		if (inside && isVisitable(pack(camera.intX & 15, camera.intY & 15, camera.intZ & 15))) {
			addToQueue(pack(camera.intX & 15, camera.intY & 15, camera.intZ & 15));
		}

		visitStartingEdges();

		int index = 0;
		int openFaces = 0;

		while (index < QUEUE.size()) {
			int position = QUEUE.getShort(index++);
			int x = (position >>> 0) & 0xF;
			int z = (position >>> 4) & 0xF;
			int y = (position >>> 8);

			openFaces |= addOpenFaces(x, y, z);

			{
				if (x < 15 && isVisitable(position + pack(1, 0, 0))) {
					addToQueue(position + pack(1, 0, 0));
				}

				if (x > 0 && isVisitable(position - pack(1, 0, 0))) {
					addToQueue(position - pack(1, 0, 0));
				}
			}

			{
				if (y < 15 && isVisitable(position + pack(0, 1, 0))) {
					addToQueue(position + pack(0, 1, 0));
				}

				if (y > 0 && isVisitable(position - pack(0, 1, 0))) {
					addToQueue(position - pack(0, 1, 0));
				}
			}

			{
				if (z < 15 && isVisitable(position + pack(0, 0, 1))) {
					addToQueue(position + pack(0, 0, 1));
				}

				if (z > 0 && isVisitable(position - pack(0, 0, 1))) {
					addToQueue(position - pack(0, 0, 1));
				}
			}
		}

		return ~openFaces;
	}

	public static void visitStartingEdges() {
		byte[] topData = SECTION_BLOCKS[sectionIndex(1, 1 + 1, 1)];
		byte[] bottomData = SECTION_BLOCKS[sectionIndex(1, 1 - 1, 1)];

		// +Y -Y
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				if (isVisitable(bottomData, pack(x, 15, z)) && isVisitable(pack(x, 0, z))) {
					addToQueue(pack(x, 0, z));
				}

				if (isVisitable(topData, pack(x, 0, z)) && isVisitable(pack(x, 15, z))) {
					addToQueue(pack(x, 15, z));
				}
			}
		}

		byte[] rightData = SECTION_BLOCKS[sectionIndex(1 + 1, 1, 1)];
		byte[] leftData = SECTION_BLOCKS[sectionIndex(1 - 1, 1, 1)];

		// +X -X
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				if (isVisitable(rightData, pack(0, y, z)) && isVisitable(pack(15, y, z))) {
					addToQueue(pack(15, y, z));
				}

				if (isVisitable(leftData, pack(15, y, z)) && isVisitable(pack(0, y, z))) {
					addToQueue(pack(0, y, z));
				}
			}
		}

		rightData = SECTION_BLOCKS[sectionIndex(1, 1, 1 + 1)];
		leftData = SECTION_BLOCKS[sectionIndex(1, 1, 1 - 1)];

		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (isVisitable(rightData, pack(x, y, 0)) && isVisitable(pack(x, y, 15))) {
					addToQueue(pack(x, y, 15));
				}

				if (isVisitable(leftData, pack(x, y, 15)) && isVisitable(pack(x, y, 0))) {
					addToQueue(pack(x, y, 0));
				}
			}
		}
	}

	private static void addToQueue(int pack) {
		QUEUE.add((short) pack);
		VISITED_CENTER_BLOCKS[pack] = 0;
	}

	private static boolean isVisitable(int packed) {
		return isVisitable(CENTER_BLOCKS, packed);
	}

	private static boolean isVisitable(byte[] blockData, int packed) {
		return PrimitivesFlags.SOLID_CULL_MASK[blockData[packed] & 0xFF] == 0 && VISITED_CENTER_BLOCKS[packed] == 1;
	}

	private static int addOpenFaces(int x, int y, int z) {
		int cullBits = 0;

		if (x == 0)  cullBits |= 1 << Direction.WEST;
		else if (x == 15) cullBits |= 1 << Direction.EAST;

		if (y == 0)  cullBits |= 1 << Direction.DOWN;
		else if (y == 15) cullBits |= 1 << Direction.UP;

		if (z == 0)  cullBits |= 1 << Direction.NORTH;
		else if (z == 15) cullBits |= 1 << Direction.SOUTH;

		return cullBits;
	}

	public static int pack(int x, int y, int z) {
		return makeBlockIndex(x, y, z);
	}
}
