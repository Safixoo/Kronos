package dev.safixo.client.util;

import dev.safixo.client.render.pipelines.terrain.WorldManager;
import it.unimi.dsi.fastutil.longs.LongArrayList;
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
	private final FastLongHashMap chunkMap = new FastLongHashMap(512);
	private final LongArrayList chunksToSend = new LongArrayList();

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
		ChunkMetadata meta = (ChunkMetadata) this.chunkMap.get(MathExt.asLong(x, z));
		return meta != null && meta.adjacentMask == 0b111_111_111;
	}

	public void unloadChunk(int x, int z) {
		ChunkMetadata meta = (ChunkMetadata) this.chunkMap.get(MathExt.asLong(x, z));

		if (meta == null) {
			return;
		}

		Chunk chunk = meta.chunk;

		if (!chunk.isEmpty()) {
			chunk.onChunkUnload();
			this.testNeighborArea(meta, true);
		}

		this.chunkMap.remove(MathExt.asLong(x, z));
	}

	@Override
	public Chunk loadChunk(int x, int z) {
		Chunk chunk = new Chunk(this.world, x, z);
		ChunkMetadata meta = new ChunkMetadata(chunk, 1 << getAdjacentMask(1, 1));

		this.chunkMap.put(MathExt.asLong(x, z), meta);
		MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(chunk));
		chunk.isChunkLoaded = true;

		this.testNeighborArea(meta, false);
		return chunk;
	}

	@Override
	public Chunk provideChunk(int x, int z) {
		long position = MathExt.asLong(x, z);
		ChunkMetadata meta = (ChunkMetadata) this.chunkMap.get(position);
		return meta == null ? this.blankChunk : meta.chunk;
	}

	public void processAllQueuedSections(WorldManager worldManager) {
		if (this.chunksToSend.isEmpty()) {
			return;
		}

		for (long position : this.chunksToSend) {
			int x = MathExt.decodeX(position);
			int z = MathExt.decodeZ(position);

			for (int y = 0; y < 16; y++) {
				worldManager.markDirty(x, y, z);
			}
		}

		this.chunksToSend.clear();
	}

	// Tests the 3x3 chunk surrounding area for existence of chunks, if the whole area is already
	// created, send the notice to the client that it should consider it for rendering.
	private void testNeighborArea(ChunkMetadata currentNode, boolean erase) {
		Chunk chunk = currentNode.chunk;

		for (int x = chunk.xPosition - 1; x <= chunk.xPosition + 1; x++) {
			for (int z = chunk.zPosition - 1; z <= chunk.zPosition + 1; z++) {
				if (x == chunk.xPosition && z == chunk.zPosition) {
					continue;
				}

				ChunkMetadata neighborNode = (ChunkMetadata) this.chunkMap.get(MathExt.asLong(x, z));

				int currentX = x - chunk.xPosition + 1;
				int currentZ = z - chunk.zPosition + 1;

				int neighborX = chunk.xPosition - x + 1;
				int neighborZ = chunk.zPosition - z + 1;

				if (neighborNode == null) {
					currentNode.adjacentMask &= ~(1 << getAdjacentMask(currentX, currentZ));
					continue;
				}

				int prevNeighborMask = neighborNode.adjacentMask;
				int prevCurrentMask = currentNode.adjacentMask;

				if (erase) {
					neighborNode.adjacentMask &= ~(1 << getAdjacentMask(neighborX, neighborZ));
				} else {
					currentNode.adjacentMask |= 1 << getAdjacentMask(currentX, currentZ);
					neighborNode.adjacentMask |= 1 << getAdjacentMask(neighborX, neighborZ);
				}

				if (currentNode.adjacentMask == 0b111_111_111 && prevCurrentMask != 0b111_111_111) {
					WorldManager manager = WorldManager.getCurrentInstance();
					Minecraft mc = Minecraft.getMinecraft();
					if (mc.skipRenderWorld) {
						for (int y = 0; y < 16; y++) {
							manager.markDirty(chunk.xPosition, y, chunk.zPosition);
						}
					} else {
						this.chunksToSend.add(MathExt.asLong(chunk.xPosition, chunk.zPosition));
					}
				}

				if (neighborNode.adjacentMask == 0b111_111_111 && prevNeighborMask != 0b111_111_111) {
					WorldManager manager = WorldManager.getCurrentInstance();
					Minecraft mc = Minecraft.getMinecraft();
					if (mc.skipRenderWorld) {
						for (int y = 0; y < 16; y++) {
							manager.markDirty(x, y, z);
						}
					} else {
						this.chunksToSend.add(MathExt.asLong(x, z));
					}
				}
			}
		}
	}

	private static int getAdjacentMask(int x, int z) {
		return x + z * 3;
	}

	public static class ChunkMetadata {
		public Chunk chunk;
		public int adjacentMask;

		public ChunkMetadata(Chunk chunk, int mask) {
			this.chunk = chunk;
			this.adjacentMask = mask;
		}
	}
}


