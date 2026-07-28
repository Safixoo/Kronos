package dev.safixo.client.render.pipelines.terrain.meshing.model;

import dev.safixo.client.render.pipelines.terrain.meshing.data.Quad;
import dev.safixo.client.util.MathExt;
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
		setBounds(blocks, bounds);

		if (!partial) {
			return 0b0;
		}

		int flag = 0;
		flag |= blocks.renderMinY >= 0.025f || blocks.renderMaxY <= 0.975f ? 0b111100 : 0;
		flag |= blocks.renderMinX >= 0.025f || blocks.renderMaxX <= 0.975f ? 0b001111 : 0;
		flag |= blocks.renderMinZ >= 0.025f || blocks.renderMaxZ <= 0.975f ? 0b110011 : 0;

		return flag;
	}

	public static void setBounds(RenderBlocks blocks, float[] bounds) {
		bounds[MIN_Y] = (float) (blocks.renderMinY);
		bounds[MAX_Y] = (float) (blocks.renderMaxY);

		bounds[MIN_Z] = (float) (blocks.renderMinZ);
		bounds[MAX_Z] = (float) (blocks.renderMaxZ);

		bounds[MIN_X] = (float) (blocks.renderMinX);
		bounds[MAX_X] = (float) (blocks.renderMaxX);
	}

	public static int getBlockCached(IBlockAccess cache, int x, int y, int z) {
		int blockId = cache.getBlockId(x, y, z);
		int solidBlock = PrimitivesFlags.SOLID_LIGHT_MASK[blockId];

		if (solidBlock == 1) {
			return 1;
		}

		return cache.getLightBrightnessForSkyBlocks(x, y, z, 0);
	}

	public static int fullFace(int blockCache) {
		return blockCache & 0b1;
	}

	public static int ao(int side1, int side2, int corner) {
		side1 = -fullFace(side1) & FULL_BLOCK_REDUCE;
		side2 = -fullFace(side2) & FULL_BLOCK_REDUCE;
		corner = -fullFace(corner) & FULL_BLOCK_REDUCE;

		corner |= side1 & side2;

		return ((EMPTY_BLOCK_OCC_FACTOR << 2) - (side1 + side2 + corner)) >> 2;
	}

	public static int avgU(int a, int b) {
		if (a == 1) {
			return b;
		}

		return (a + b) >>> 1;
	}

	public static int avg(int a, int b) {
		if (a == 1) {
			return b;
		}
		if (b == 1) {
			return a;
		}

		return (a + b) >>> 1;
	}

	public static boolean checkPartial(Quad quad, int blockX, int blockY, int blockZ) {
		for (int i = 0; i < 4; i++) {
			float x = quad.getPosRelX(blockX, i);
			float y = quad.getPosRelY(blockY, i);
			float z = quad.getPosRelZ(blockZ, i);

			if (!MathExt.equals(x, 0.0F) && !MathExt.equals(x, 1.0F)) {
				return true;
			}
			if (!MathExt.equals(y, 0.0F) && !MathExt.equals(y, 1.0F)) {
				return true;
			}
			if (!MathExt.equals(z, 0.0F) && !MathExt.equals(z, 1.0F)) {
				return true;
			}
		}

		return false;
	}
}
