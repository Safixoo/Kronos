package dev.safixo.client.render.pipelines.terrain.meshing.model;

import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.util.data.PrimitivesFlags;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

public class ModelHelper {
	private static final double SOLID_OCC_FACTOR = 0.2;

	private static final int EMPTY_BLOCK_OCC_FACTOR = 255;
	private static final int FULL_BLOCK_REDUCE = EMPTY_BLOCK_OCC_FACTOR - (int) (SOLID_OCC_FACTOR * EMPTY_BLOCK_OCC_FACTOR);

	// Start from 1, as the minus sign is used to encode more useful data and -0 doesn't exist.
	public static final int MIN_Y = 1;
	public static final int MAX_Y = 2;

	public static final int MIN_Z = 3;
	public static final int MAX_Z = 4;

	public static final int MIN_X = 5;
	public static final int MAX_X = 6;

	public static final int SIZE = 7;

	public static int processModel(RenderBlocks blocks, float[] bounds, boolean partial) {
		bounds[MIN_Y] = (float) (blocks.renderMinY);
		bounds[MAX_Y] = (float) (blocks.renderMaxY);

		bounds[MIN_Z] = (float) (blocks.renderMinZ);
		bounds[MAX_Z] = (float) (blocks.renderMaxZ);

		bounds[MIN_X] = (float) (blocks.renderMinX);
		bounds[MAX_X] = (float) (blocks.renderMaxX);

		if (!partial) {
			return 0b0;
		}

		int flag = 0;
		flag |= blocks.renderMinY >= 0.025f || blocks.renderMaxY <= 0.975f ? 0b111100 : 0;
		flag |= blocks.renderMinX >= 0.025f || blocks.renderMaxX <= 0.975f ? 0b001111 : 0;
		flag |= blocks.renderMinZ >= 0.025f || blocks.renderMaxZ <= 0.975f ? 0b110011 : 0;
		return flag;
	}

	public static int getBlockCached(IBlockAccess cache, int x, int y, int z) {
		int blockId = cache.getBlockId(x, y, z);
		int solidBlock = PrimitivesFlags.SOLID_LIGHT_MASK[blockId];

		if (solidBlock == 1) {
			return solidBlock; // 0b1
		}

		return cache.getLightBrightnessForSkyBlocks(x, y, z, 0) << 4;
	}

	public static int getBlockCacheLazily(IBlockAccess cache, int x, int y, int z) {
		int blockId = cache.getBlockId(x, y, z);
		int solidBlock = PrimitivesFlags.SOLID_LIGHT_MASK[blockId];

		if (solidBlock == 1) {
			return solidBlock; // 0b1
		}

		// used for corners as usually you don't always need the light value explicitly.
		return ~1;
	}

	public static int fullFace(int blockCache) {
		return blockCache & 0b1;
	}

	public static int light(int blockCache) {
		return blockCache >>> 4;
	}

	public static int light(IBlockAccess cache, int x, int y, int z, int blockCache) {
		if (blockCache == ~1) {
			return cache.getLightBrightnessForSkyBlocks(x, y, z, 0);
		}

		return blockCache >>> 4;
	}

	public static int light(SectionCache cache, int x, int y, int z, int blockCache) {
		if (blockCache == ~1) {
			return cache.getLight(x, y, z, 0);
		}

		return blockCache >>> 4;
	}

	public static int lightCenter(SectionCache cache, int x, int y, int z, int blockCache) {
		if (blockCache == ~1) {
			return cache.getLightCenter(x, y, z, 0);
		}

		return blockCache >>> 4;
	}

	// Naive approximation to Minecraft ambient occlusion, it skips some classifications differences
	// (normalCube vs opaqueCube, etc.).
	public static int ao(int side1, int side2, int corner) {
		// Pick the solid bit from the masks and neg it. {0, -1} = {no solid, solid}
		side1 = -fullFace(side1);
		side2 = -fullFace(side2);
		corner = -fullFace(corner);

		// If both sides are solid, ignore the corner and treat it as solid.
		corner |= side1 & side2;

		// Use more -1 bitwise conditionals to reduce lighting if solid. Mimics Vanilla 0.2 ambient factor
		// for solid blocks, with the catch that is doesn't distinguish the blocks by #isNormalCube() but by #isOpaqueCube.
		side1 = EMPTY_BLOCK_OCC_FACTOR - (side1 & FULL_BLOCK_REDUCE);
		side2 = EMPTY_BLOCK_OCC_FACTOR - (side2 & FULL_BLOCK_REDUCE);
		corner = EMPTY_BLOCK_OCC_FACTOR - (corner & FULL_BLOCK_REDUCE);

		// The extra EMPTY_BLOCK_OCC_FACTOR is because the block by the face side of the block is always un-solid
		// either it would be culled.
		return (EMPTY_BLOCK_OCC_FACTOR + side1 + side2 + corner) >> 2;
	}

	public static int avg(int a, int b) {
		if (b == 0) {
			return a;
		}
		if (a == 0) {
			return b;
		}

		int sumLight = (a + b);
		return sumLight >>> 1;
	}

	private static boolean lossyEqual(double a, double b) {
		return Math.abs(a - b) <= (1.0 / 16.0f);
	}
}
