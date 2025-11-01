package dev.safixo.core.hooks;

import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.BlocksFlags;
import dev.safixo.client.util.memory.UnsafeUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;

@SuppressWarnings("unused")
public class MinecraftHook {
	private static final int ITEM_STRIDE = 24;

	public static void checkGLError(Minecraft minecraft, String str) {
		if (!BlocksFlags.DETECTED) {
			BlocksFlags.processDevInfo();
		}

		// NO-OP
	}

	private static final int TOP_NORMAL = MathExt.packedNormal(0.0F, 1.0F, 0.0F);
	private static final int BOTTOM_NORMAL = MathExt.packedNormal(0.0F, -1.0F, 0.0F);

	private static final int FRONT_NORMAL = MathExt.packedNormal(0.0F, 0.0F, 1.0F);
	private static final int BEHIND_NORMAL = MathExt.packedNormal(0.0F, 0.0F, -1.0F);

	private static final int RIGHT_NORMAL = MathExt.packedNormal(1.0F, 0.0F, 0.0F);
	private static final int LEFT_NORMAL = MathExt.packedNormal(-1.0F, 0.0F, 0.0F);

	public static void renderItemIn2D(Tessellator tes, float minU, float maxV, float maxU, float minV, int width, int height, float scale) {
		ImprovedTessellator ver = (ImprovedTessellator) tes;

		ver.startDrawingQuads();

		long ptr = ver.vertexPtr + ver.offset;

		ver.offset = (int) (ptr - ver.vertexPtr);
		if (ver.offset + ITEM_STRIDE * 8 >= ver.capacity) {
			ver.resize();
		}

		// Front quad.
		ptr = addVertex(ptr, 0.0F, 0.0F, 0.0F, minU, minV, FRONT_NORMAL);
		ptr = addVertex(ptr, 1.0F, 0.0F, 0.0F, maxU, minV, FRONT_NORMAL);
		ptr = addVertex(ptr, 1.0F, 1.0F, 0.0F, maxU, maxV, FRONT_NORMAL);
		ptr = addVertex(ptr, 0.0F, 1.0F, 0.0F, minU, maxV, FRONT_NORMAL);

		// Behind quad.
		ptr = addVertex(ptr, 0.0F, 1.0F, -scale, minU, maxV, BEHIND_NORMAL);
		ptr = addVertex(ptr, 1.0F, 1.0F, -scale, maxU, maxV, BEHIND_NORMAL);
		ptr = addVertex(ptr, 1.0F, 0.0F, -scale, maxU, minV, BEHIND_NORMAL);
		ptr = addVertex(ptr, 0.0F, 0.0F, -scale, minU, minV, BEHIND_NORMAL);

		float invWidth = 1.0f / width;
		float invHeight = 1.0f / height;

		float halfTexelU = 0.5F * (minU - maxU) * invWidth;
		float haltTexelV = 0.5F * (minV - maxV) * invHeight;

		ver.offset = (int) (ptr - ver.vertexPtr);
		if (ver.offset + ITEM_STRIDE * 8L * width >= ver.capacity) {
			ver.resize();
		}

		for (int i = 0; i < width; i++) {
			float relW = i * invWidth;
			float nextRelW = relW + invWidth;
			float u = minU + (maxU - minU) * relW - halfTexelU;
			// -X
			ptr = addVertex(ptr, relW, 0.0F, -scale, u, minV, LEFT_NORMAL);
			ptr = addVertex(ptr, relW, 0.0F, 0.0F, u, minV, LEFT_NORMAL);
			ptr = addVertex(ptr, relW, 1.0F, 0.0F, u, maxV, LEFT_NORMAL);
			ptr = addVertex(ptr, relW, 1.0F, -scale, u, maxV, LEFT_NORMAL);

			// +X
			ptr = addVertex(ptr, nextRelW, 1.0F, -scale, u, maxV, RIGHT_NORMAL);
			ptr = addVertex(ptr, nextRelW, 1.0F, 0.0F, u, maxV, RIGHT_NORMAL);
			ptr = addVertex(ptr, nextRelW, 0.0F, 0.0F, u, minV, RIGHT_NORMAL);
			ptr = addVertex(ptr, nextRelW, 0.0F, -scale, u, minV, RIGHT_NORMAL);
		}

		ver.offset = (int) (ptr - ver.vertexPtr);
		if (ver.offset + ITEM_STRIDE * 8L * height >= ver.capacity) {
			ver.resize();
		}

		for (int i = 0; i < height; i++) {
			float relW = i * invHeight;
			float nextRelW = relW + invHeight;
			float v = minV + (maxV - minV) * relW - haltTexelV;
			// +Y
			ptr = addVertex(ptr, 0.0F, nextRelW, 0.0F, minU, v, TOP_NORMAL);
			ptr = addVertex(ptr, 1.0F, nextRelW, 0.0F, maxU, v, TOP_NORMAL);
			ptr = addVertex(ptr, 1.0F, nextRelW, -scale, maxU, v, TOP_NORMAL);
			ptr = addVertex(ptr, 0.0F, nextRelW, -scale, minU, v, TOP_NORMAL);

			// -Y
			ptr = addVertex(ptr, 1.0F, relW, 0.0F, maxU, v, BOTTOM_NORMAL);
			ptr = addVertex(ptr, 0.0F, relW, 0.0F, minU, v, BOTTOM_NORMAL);
			ptr = addVertex(ptr, 0.0F, relW, -scale, minU, v, BOTTOM_NORMAL);
			ptr = addVertex(ptr, 1.0F, relW, -scale, maxU, v, BOTTOM_NORMAL);
		}

		ver.offset = (int) (ptr - ver.vertexPtr);
		ver.vertices = ver.offset / ITEM_STRIDE;
		ver.flags = ImprovedTessellator.VERTEX_UV | ImprovedTessellator.VERTEX_NORMAL;
		ver.draw();
	}

	private static long addVertex(long ptr, float x, float y, float z, float u, float v, int normal) {
		UnsafeUtil.memPutFloat(ptr + 0, x);
		UnsafeUtil.memPutFloat(ptr + 4, y);
		UnsafeUtil.memPutFloat(ptr + 8, z);

		UnsafeUtil.memPutFloat(ptr + 12, u);
		UnsafeUtil.memPutFloat(ptr + 16, v);

		UnsafeUtil.memPutInt(ptr + 20, normal);

		return ptr + ITEM_STRIDE;
	}

	public static int getLightBrightnessForSkyBlocks(World world, int x, int y, int z, int minBlockLight) {
		// if y < 0, makes y = 0, otherwise nothing.
		y &= ~(y >> 31);

		if (y >= 256 || x < -30000000 || z < -30000000 || x >= 30000000 || z >= 30000000) {
			return MathExt.getLightmapCoord(15, 0);
		}

		return getSavedLightValue(world, x, y, z);
	}

	private static Chunk LAST_CHUNK;
	private static long LAST_POSITION;

	public static int getBlockId(World world, int x, int y, int z) {
		Chunk chunk = getChunk(world, x >> 4, z >> 4);
		return chunk.getBlockID(x & 15, y, z & 15);
	}

	public static int getSavedLightValue(World world, int x, int y, int z) {
		if (y >= 256) {
			y = 255;
		}

		int chunkX = x >> 4;
		int chunkZ = z >> 4;

		Chunk chunk = getChunk(world, chunkX, chunkZ);

		int blockId = chunk.getBlockID(x & 15, y, z & 15);

		if (BlocksFlags.SOLID[blockId]) {
			return 0;
		}

		int blockLight = chunk.getSavedLightValue(EnumSkyBlock.Block, x & 15, y, z & 15);
		int skyLight = chunk.getSavedLightValue(EnumSkyBlock.Sky, x & 15, y, z & 15);

		return MathExt.getLightmapCoord(skyLight, blockLight);
	}

	public static Chunk getChunk(World world, int chunkX, int chunkZ) {
		long position = MathExt.asLong(chunkX, chunkZ);
		Chunk chunk;

		if (position == LAST_POSITION && LAST_CHUNK != null) {
			chunk = LAST_CHUNK;
		} else {
			LAST_CHUNK = chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);
			LAST_POSITION = position;
		}

		return chunk;
	}
}
