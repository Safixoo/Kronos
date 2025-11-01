package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.render.pipelines.terrain.SectionManager;
import dev.safixo.client.util.MathExt;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;

public class ChunkListener {
	// Loaded chunks.
	private static final LongOpenHashSet LOADED_CHUNKS = new LongOpenHashSet(2048, 0.5F);

	// Loaded chunks with neighbors.
	private static final LongOpenHashSet READY_CHUNKS = new LongOpenHashSet(2048, 0.5F);

	public static boolean canUnloadChunk(int chunkX, int chunkZ) {
		long position = MathExt.asLong(chunkX, chunkZ);

		if (!READY_CHUNKS.contains(position)) {
			return true;
		}

		WorldClient world = Minecraft.getMinecraft().theWorld;

		for (int x = chunkX - 1; x <= chunkX + 1; x++) {
			for (int z = chunkZ - 1; z <= chunkZ + 1; z++) {
				boolean chunkLoaded = LOADED_CHUNKS.contains(position);

				if (chunkLoaded) {
					Chunk chunk = world.getChunkFromChunkCoords(x, z);

					if (chunk.getClass() != EmptyChunk.class && chunk.isChunkLoaded) {
						return false;
					}

					LOADED_CHUNKS.remove(position);
				}
			}
		}

		READY_CHUNKS.remove(position);
		return true;
	}

	public static boolean canLoadChunk(int chunkX, int chunkZ) {
		long position = MathExt.asLong(chunkX, chunkZ);

		if (READY_CHUNKS.contains(position)) {
			return true;
		}

		WorldClient world = Minecraft.getMinecraft().theWorld;

		for (int x = chunkX - 1; x <= chunkX + 1; x++) {
			for (int z = chunkZ - 1; z <= chunkZ + 1; z++) {
				boolean chunkLoaded = LOADED_CHUNKS.contains(position);

				if (!chunkLoaded) {
					Chunk chunk = world.getChunkFromChunkCoords(x, z);

					if (!chunk.isChunkLoaded) {
						return false;
					}

					LOADED_CHUNKS.add(position);
				}
			}
		}

		READY_CHUNKS.add(position);
		return true;
	}

	public static void clearData() {
		LOADED_CHUNKS.clear();
		READY_CHUNKS.clear();
	}

	public static void notifyBlockUpdateRange(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		minY = MathExt.clamp(minY, 0, 255);
		maxY = MathExt.clamp(maxY, 0, 255);

		minX >>= 4; maxX >>= 4;
		minY >>= 4; maxY >>= 4;
		minZ >>= 4; maxZ >>= 4;

		SectionManager manager = SectionManager.getCurrentInstance();

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {

				if (!ChunkListener.canLoadChunk(x, z)) {
					continue;
				}

				for (int y = minY; y <= maxY; y++) {
					manager.markDirty(x, y, z);
				}
			}
		}
	}

	public static void markBlockForUpdate(RenderGlobal renderGlobal, int x, int y, int z) {
		notifyBlockUpdateRange(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
	}

	public static void markBlockForRenderUpdate(RenderGlobal renderGlobal, int x, int y, int z) {
		notifyBlockUpdateRange(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
	}

	public static void markBlockRangeForRenderUpdate(RenderGlobal renderGlobal, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		notifyBlockUpdateRange(minX - 1, minY - 1, minZ - 1,  maxX + 1, maxY + 1, maxZ + 1);
	}

	public static void markBlocksForUpdate(RenderGlobal renderGlobal, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		notifyBlockUpdateRange(minX - 1, minY - 1, minZ - 1,  maxX + 1, maxY + 1, maxZ + 1);
	}
}
