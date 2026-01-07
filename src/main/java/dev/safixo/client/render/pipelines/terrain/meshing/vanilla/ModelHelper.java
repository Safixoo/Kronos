package dev.safixo.client.render.pipelines.terrain.meshing.vanilla;

import dev.safixo.client.util.Direction;
import dev.safixo.client.util.data.PrimitivesFlags;
import net.minecraft.block.Block;
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

	/**
	 * Behaves just as a quad from a full voxel block.
	 */
	public static final int FULLY_ALIGNED_QUAD = 0b001;
	/**
	 * The quad doesn't fill the entire volume, can't map the vertices
	 * lighting directly from the corners as it's corners are not the same
	 * as full block ones.
	 */
	public static final int PARTIAL_QUAD = 0b010;
	/**
	 * Is not fully aligned to the grid, although their volume could be
	 * aligned to the one from a full quad, i.e. it can be full quad that was
	 * translated and no longer is aligned to the grid.
	 */
	public static final int PARALLEL_QUAD = 0b100;

	public static int processModel(RenderBlocks blocks, float[] bounds) {
		bounds[MIN_Y] = (float) (blocks.renderMinY);
		bounds[MIN_Z] = (float) (blocks.renderMinZ);
		bounds[MIN_X] = (float) (blocks.renderMinX);

		bounds[MAX_Y] = (float) (blocks.renderMaxY);
		bounds[MAX_Z] = (float) (blocks.renderMaxZ);
		bounds[MAX_X] = (float) (blocks.renderMaxX);

		boolean minY = lossyEqual(blocks.renderMinY, 0.0f);
		boolean maxY = lossyEqual(blocks.renderMaxY, 1.0f);

		int flag = 0;

		if (minY || maxY) {
			flag |= 0b111100;
		}

		boolean minX = lossyEqual(blocks.renderMinX, 0.0f);
		boolean maxX = lossyEqual(blocks.renderMaxX, 1.0f);

		if (minX || maxX) {
			flag |= 0b001111;
		}

		boolean minZ = lossyEqual(blocks.renderMinZ, 0.0f);
		boolean maxZ = lossyEqual(blocks.renderMaxZ, 1.0f);

		if (minZ || maxZ) {
			flag |= 0b110011;
		}

		return flag;
	}

	public static int getBlockCached(IBlockAccess cache, int x, int y, int z) {
		int blockId = cache.getBlockId(x, y, z);
		int solidBlock = PrimitivesFlags.SOLID_LIGHT_MASK[blockId];

		if (solidBlock == 1) {
			return solidBlock; // 0b1
		}

		return cache.getLightBrightnessForSkyBlocks(x, y, z, Block.lightValue[blockId]) << 4;
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
