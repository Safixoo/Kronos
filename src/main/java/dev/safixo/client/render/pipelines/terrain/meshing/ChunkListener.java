package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.render.pipelines.terrain.SectionManager;
import dev.safixo.client.util.MathExt;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.renderer.RenderGlobal;

public class ChunkListener {
	// Available sections.
	private static final LongOpenHashSet AVAILABLE_SECTIONS = new LongOpenHashSet();

	// Available sections with all neighbors.
	private static final LongOpenHashSet LOADABLE_SECTIONS = new LongOpenHashSet();

	private static long LAST_REQUEST;
	private static boolean LAST_RESULT;

	public static void unloadRenderChunk(int chunkX, int chunkZ) {

	}

	public static boolean canLoadChunk(int sectionX, int sectionZ) {
		long position = MathExt.asLong(sectionX, sectionZ);
		boolean result;

		if (position != LAST_REQUEST) {
			LAST_REQUEST = position;
			result = LAST_RESULT = LOADABLE_SECTIONS.contains(MathExt.asLong(sectionX, sectionZ));
		} else {
			result = LAST_RESULT;
		}

		return result;
	}

	private static void checkChunkSurrounding(int chunkX, int chunkZ, boolean loadNeighbors) {
		int minChunkX = chunkX - 1;
		int minChunkZ = chunkZ - 1;

		int maxChunkX = chunkX + 1;
		int maxChunkZ = chunkZ + 1;

		boolean canLoad = true;

		for (int chunkOffX = minChunkX; chunkOffX <= maxChunkX; chunkOffX++) {
			for (int chunkOffZ = minChunkZ; chunkOffZ <= maxChunkZ; chunkOffZ++) {
				long position = MathExt.asLong(chunkOffX, chunkOffZ);

				if (!AVAILABLE_SECTIONS.contains(position)) {
					canLoad = false;
				}

				if (loadNeighbors) {
					checkChunkSurrounding(chunkOffX, chunkOffZ, false);
				}
			}
		}

		SectionManager manager = SectionManager.getCurrentInstance();
		long position = MathExt.asLong(chunkX, chunkZ);

		if (canLoad && !LOADABLE_SECTIONS.contains(position)) {
			for (int y = 0; y < 16; y++) {
				manager.markDirty(chunkX, y, chunkZ);
			}
			LOADABLE_SECTIONS.add(position);
		}
	}

	// For each chunk ingest check surrounding,
	public static void loadRenderChunk(int chunkX, int chunkZ) {
		long position = MathExt.asLong(chunkX, chunkZ);

		if (!LOADABLE_SECTIONS.contains(position)) {
			AVAILABLE_SECTIONS.add(position);
			checkChunkSurrounding(chunkX, chunkZ, true);
		}
	}

	public static void clearData() {
		AVAILABLE_SECTIONS.clear();
		LOADABLE_SECTIONS.clear();
	}

	public static void notifyBlockUpdateRange(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		minY = MathExt.clamp(minY, 0, 255);
		maxY = MathExt.clamp(maxY, 0, 255);

		minX = MathExt.posToSectionIntegral(minX >> 4);
		minY = MathExt.posToSectionIntegral(minY >> 4);
		minZ = MathExt.posToSectionIntegral(minZ >> 4);

		maxX = MathExt.posToSectionIntegral(maxX >> 4);
		maxY = MathExt.posToSectionIntegral(maxY >> 4);
		maxZ = MathExt.posToSectionIntegral(maxZ >> 4);

		SectionManager manager = SectionManager.getCurrentInstance();

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					if (LOADABLE_SECTIONS.contains(MathExt.asLong(x >> 4, z >> 4))) {
						continue;
					}

					manager.markDirty(x, y, z);
				}
			}
		}
	}

	public static void markBlockForUpdate(RenderGlobal renderGlobal, int minX, int minY, int minZ) {
		notifyBlockUpdateRange(minX - 1, minY - 1, minZ - 1, minX + 1, minY + 1, minZ + 1);
	}

	public static void markBlockForRenderUpdate(RenderGlobal renderGlobal, int minX, int minY, int minZ) {
		notifyBlockUpdateRange(minX - 1, minY - 1, minZ - 1, minX + 1, minY + 1, minZ + 1);
	}

	public static void markBlockRangeForRenderUpdate(RenderGlobal renderGlobal, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		notifyBlockUpdateRange(minX - 1, minY - 1, minZ - 1,  maxX + 1, maxY + 1, maxZ + 1);
	}

	public static void markBlocksForUpdate(RenderGlobal renderGlobal, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		notifyBlockUpdateRange(minX - 1, minY - 1, minZ - 1,  maxX + 1, maxY + 1, maxZ + 1);
	}
}
