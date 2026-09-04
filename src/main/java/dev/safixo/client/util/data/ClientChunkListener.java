package dev.safixo.client.util.data;

import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.collection.ChunkMap;
import dev.safixo.client.util.collection.FastLongHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;

public class ClientChunkListener extends ChunkProviderClient {
	private final ChunkMap chunkMap = new ChunkMap(128);

	private final World world;
	private final EmptyChunk blankChunk;

	public ClientChunkListener(World world) {
		super(world);
		this.world = world;
		this.blankChunk = new EmptyChunk(world, 0, 0);
	}

	public static ClientChunkListener getChunkListener() {
		WorldClient worldClient = Minecraft.getMinecraft().theWorld;
		IChunkProvider provider = worldClient.getChunkProvider();

		return (ClientChunkListener) provider;
	}

	@Override
	public String makeString() {
		return "KronosChunkCache: " + this.chunkMap.getSize();
	}

	public boolean canLoadChunk(int x, int z) {
		int chunkIndex = this.chunkMap.getIndex(x, z);
		int adjacentMask = this.chunkMap.getAdjacentMask(chunkIndex);

		return adjacentMask == 0b111_111_111;
	}

	public void unloadChunk(int x, int z) {
		int index = this.chunkMap.getIndex(x, z);
		Chunk chunk = this.chunkMap.getChunk(index);

		if (chunk == null) {
			return;
		}

		if (!chunk.isEmpty()) {
			chunk.onChunkUnload();
			this.testNeighborArea(index, true);
		}

		this.chunkMap.remove(x, z);
	}

	@Override
	public Chunk loadChunk(int x, int z) {
		Chunk chunk = new Chunk(this.world, x, z);
		this.chunkMap.put(x, z, chunk);

		int currentIndex = this.chunkMap.getIndex(x, z);
		this.chunkMap.addAdjacentDirection(currentIndex, 1 << getAdjacentMask(1, 1));

		MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(chunk));
		chunk.isChunkLoaded = true;

		this.testNeighborArea(currentIndex, false);
		return chunk;
	}

	@Override
	public Chunk provideChunk(int x, int z) {
		Chunk chunk = this.chunkMap.getChunk(x, z);
		return chunk == null ? this.blankChunk : chunk;
	}

	// Tests the 3x3 chunk surrounding area for existence of chunks, if the whole area is already
	// created, send the notice to the client that it should consider it for rendering.
	private void testNeighborArea(int currentIndex, boolean erase) {
		Chunk chunk = this.chunkMap.getChunk(currentIndex);

		for (int x = chunk.xPosition - 1; x <= chunk.xPosition + 1; x++) {
			for (int z = chunk.zPosition - 1; z <= chunk.zPosition + 1; z++) {
				if (x == chunk.xPosition && z == chunk.zPosition) {
					continue;
				}

				int neighborIndex = this.chunkMap.getIndex(x, z);
				Chunk neighborNode = this.chunkMap.getChunk(neighborIndex);

				int currentX = x - chunk.xPosition + 1;
				int currentZ = z - chunk.zPosition + 1;

				int neighborX = chunk.xPosition - x + 1;
				int neighborZ = chunk.zPosition - z + 1;

				if (neighborNode == null) {
					this.chunkMap.removeAdjacentDirection(currentIndex, 1 << getAdjacentMask(currentX, currentZ));
					continue;
				}

				int prevNeighborMask = this.chunkMap.getAdjacentMask(neighborIndex);
				int prevCurrentMask = this.chunkMap.getAdjacentMask(currentIndex);

				if (erase) {
					this.chunkMap.removeAdjacentDirection(neighborIndex, 1 << getAdjacentMask(neighborX, neighborZ));
				} else {
					this.chunkMap.addAdjacentDirection(currentIndex, 1 << getAdjacentMask(currentX, currentZ));
					this.chunkMap.addAdjacentDirection(neighborIndex, 1 << getAdjacentMask(neighborX, neighborZ));
				}

				if (this.chunkMap.getAdjacentMask(currentIndex) == 0b111_111_111 && prevCurrentMask != 0b111_111_111) {
					WorldManager manager = WorldManager.getCurrentInstance();
					Minecraft mc = Minecraft.getMinecraft();

					if (mc.skipRenderWorld) {
						for (int y = 0; y < 16; y++) {
							manager.markDirty(chunk.xPosition, y, chunk.zPosition);
						}
					}
				}

				if (this.chunkMap.getAdjacentMask(neighborIndex) == 0b111_111_111 && prevNeighborMask != 0b111_111_111) {
					WorldManager manager = WorldManager.getCurrentInstance();
					Minecraft mc = Minecraft.getMinecraft();

					if (mc.skipRenderWorld) {
						for (int y = 0; y < 16; y++) {
							manager.markDirty(x, y, z);
						}
					}
				}
			}
		}
	}

	private static int getAdjacentMask(int x, int z) {
		return x + z * 3;
	}
}


