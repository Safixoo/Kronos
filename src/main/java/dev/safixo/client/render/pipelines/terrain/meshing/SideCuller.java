package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.util.Direction;
import dev.safixo.client.util.data.PrimitivesFlags;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

public class SideCuller {
	public static void calculateSolidSides(Block block, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		PrimitivesFlags.FULL_FACES[block.blockID] = (byte) getMask(minX, minY, minZ, maxX, maxY, maxZ);
	}

	public static int getMask(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		int mask = 0;

		mask |= equal(minY, 0) ? Direction.DOWN_BIT : 0;
		mask |= equal(maxY, 1) ? Direction.UP_BIT : 0;

		mask |= equal(minZ, 0) ? Direction.NORTH_BIT : 0;
		mask |= equal(maxZ, 1) ? Direction.SOUTH_BIT : 0;

		mask |= equal(minX, 0) ? Direction.WEST_BIT : 0;
		mask |= equal(maxX, 1) ? Direction.EAST_BIT : 0;

		return mask;
	}

	public static boolean equal(double a, double b) {
		return a == b;
	}

	public static boolean shouldSideBeRendered(Block block, IBlockAccess blockCache, int x, int y, int z, int dir) {
		if ((PrimitivesFlags.FULL_FACES[block.blockID] & (1 << dir)) == 0) {
			return true;
		}

		return !blockCache.isBlockOpaqueCube(x, y, z);
	}
}
