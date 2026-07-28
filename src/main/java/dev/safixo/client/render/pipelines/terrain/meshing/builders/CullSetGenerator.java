package dev.safixo.client.render.pipelines.terrain.meshing.builders;

import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.data.PrimitivesFlags;

import java.util.Arrays;

import static dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache.*;
import static dev.safixo.client.util.Direction.*;

public class CullSetGenerator {
	private static final byte[] FULL_SOLID = new byte[16 * 16 * 16];

	public static final int VISITED = 0;
	public static final int NOT_VISITED = 1;

	private final short[] queue = new short[16 * 16 * 16];
	private final byte[] visitedSectionBlocks = new byte[4096];
	private int queueIndex = 0;

	static {
		Arrays.fill(FULL_SOLID, (byte) NOT_VISITED);
	}

	private void restartQueue() {
		System.arraycopy(FULL_SOLID, 0, this.visitedSectionBlocks, 0, 4096);
		this.queueIndex = 0;
	}

	private void handlePlayerPosition(SectionCache cache, SectionRender section, CameraData camera) {
		int cameraChunkX = camera.intX >> 4, cameraChunkY = camera.intY >> 4, cameraChunkZ = camera.intZ >> 4;
		int sectionX = section.blockX >> 4, sectionY = section.blockY >> 4, sectionZ = section.blockZ >> 4;
		boolean inside = cameraChunkX == sectionX && cameraChunkY == sectionY && cameraChunkZ == sectionZ;

		if (inside && this.isVisitable(cache, pack(camera.intX & 15, camera.intY & 15, camera.intZ & 15))) {
			this.addToQueue(pack(camera.intX & 15, camera.intY & 15, camera.intZ & 15));
		}
	}

	public int floodFillSection(SectionCache cache, SectionRender section, CameraData camera) {
		this.restartQueue();
		this.handlePlayerPosition(cache, section, camera);
		this.visitStartingEdges(cache);

		cache.setVisitedArray(this.visitedSectionBlocks);

		int openFaces = 0;
		int index = 0;

		while (index < this.queueIndex) {
			int position = this.queue[index++];
			int x = (position >>> 0) & 0xF;
			int z = (position >>> 4) & 0xF;
			int y = (position >>> 8);

			{
				if (x == 15) {
					openFaces |= 1 << EAST;
				} else if (this.isVisitable(cache, position + pack(1, 0, 0))) {
					this.addToQueue(position + pack(1, 0, 0));
				}
				if (x == 0) {
					openFaces |= 1 << WEST;
				} else if (this.isVisitable(cache, position - pack(1, 0, 0))) {
					this.addToQueue(position - pack(1, 0, 0));
				}
			}
			{
				if (y == 15) {
					openFaces |= 1 << UP;
				} else if (this.isVisitable(cache, position + pack(0, 1, 0))) {
					this.addToQueue(position + pack(0, 1, 0));
				}
				if (y == 0) {
					openFaces |= 1 << DOWN;
				} else if (this.isVisitable(cache, position - pack(0, 1, 0))) {
					this.addToQueue(position - pack(0, 1, 0));
				}
			}
			{
				if (z == 15) {
					openFaces |= 1 << SOUTH;
				} else if (this.isVisitable(cache, position + pack(0, 0, 1))) {
					this.addToQueue(position + pack(0, 0, 1));
				}
				if (z == 0) {
					openFaces |= 1 << NORTH;
				} else if (this.isVisitable(cache, position - pack(0, 0, 1))) {
					this.addToQueue(position - pack(0, 0, 1));
				}
			}
		}

		return openFaces ^ 0x3F;
	}

	public void visitStartingEdges(SectionCache cache) {
		int topData = sectionIndex(1, 1 + 1, 1);
		int bottomData = sectionIndex(1, 1 - 1, 1);

		// +Y -Y
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				if (this.isVisitable(cache, bottomData, pack(x, 15, z)) && this.isVisitable(cache, pack(x, 0, z))) {
					this.addToQueue(pack(x, 0, z));
				}

				if (this.isVisitable(cache, topData, pack(x, 0, z)) && this.isVisitable(cache, pack(x, 15, z))) {
					this.addToQueue(pack(x, 15, z));
				}
			}
		}

		int rightData = sectionIndex(1 + 1, 1, 1);
		int leftData = sectionIndex(1 - 1, 1, 1);

		// +X -X
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				if (this.isVisitable(cache, rightData, pack(0, y, z)) && this.isVisitable(cache, pack(15, y, z))) {
					this.addToQueue(pack(15, y, z));
				}

				if (this.isVisitable(cache, leftData, pack(15, y, z)) && this.isVisitable(cache, pack(0, y, z))) {
					this.addToQueue(pack(0, y, z));
				}
			}
		}

		rightData = sectionIndex(1, 1, 1 + 1);
		leftData = sectionIndex(1, 1, 1 - 1);

		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (this.isVisitable(cache, rightData, pack(x, y, 0)) && this.isVisitable(cache, pack(x, y, 15))) {
					this.addToQueue(pack(x, y, 15));
				}

				if (isVisitable(cache, leftData, pack(x, y, 15)) && this.isVisitable(cache, pack(x, y, 0))) {
					this.addToQueue(pack(x, y, 0));
				}
			}
		}
	}

	private void addToQueue(int pack) {
		this.queue[this.queueIndex++] = (short) pack;
		this.visitedSectionBlocks[pack] = VISITED;
	}

	private boolean isVisitable(SectionCache cache, int packed) {
		return isVisitable(cache, SectionCache.sectionIndex(1, 1, 1), packed);
	}

	private boolean isVisitable(SectionCache cache, int sectionIndex, int packed) {
		return this.visitedSectionBlocks[packed] == NOT_VISITED && PrimitivesFlags.SOLID_CULL_MASK[cache.getBlockId(sectionIndex, packed)] == 0;
	}

	public static int pack(int x, int y, int z) {
		return makeBlockIndex(x, y, z);
	}
}
