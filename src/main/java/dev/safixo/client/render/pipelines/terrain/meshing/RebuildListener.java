package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.util.ClientChunkListener;
import dev.safixo.client.util.MathExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.world.chunk.IChunkProvider;

public class RebuildListener {
	public static ClientChunkListener getChunkListener() {
		WorldClient worldClient = Minecraft.getMinecraft().theWorld;
		IChunkProvider provider = worldClient.getChunkProvider();

		return (ClientChunkListener) provider;
	}

	public static void notifyBlockUpdateRange(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		minY = MathExt.clamp(minY, 0, 255);
		maxY = MathExt.clamp(maxY, 0, 255);

		minX >>= 4; maxX >>= 4;
		minY >>= 4; maxY >>= 4;
		minZ >>= 4; maxZ >>= 4;

		WorldManager manager = WorldManager.getCurrentInstance();
		ClientChunkListener provider = getChunkListener();

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				if (!provider.shouldLoadChunk(x, z)) {
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
