package dev.safixo.client.render.pipelines.terrain.meshing.model.light.data;

import net.minecraft.world.IBlockAccess;
import java.util.Arrays;

/**
 * A light data cache which uses a flat-array to store the light data for the blocks in a given chunk and its direct
 * neighbors. This is considerably faster than using a hash table to lookup values for a given block position and
 * can be re-used by {@link IBlockAccess} to avoid allocations.
 */
public class ArrayLightDataCache extends LightDataAccess {
    private static final int NEIGHBOR_BLOCK_RADIUS = 1;
    private static final int BLOCK_LENGTH = 16 + (NEIGHBOR_BLOCK_RADIUS * 2);

    private final int[] light;

    private int xOffset, yOffset, zOffset;

    public ArrayLightDataCache() {
        this.light = new int[BLOCK_LENGTH * BLOCK_LENGTH * BLOCK_LENGTH];
    }

	public void reset(IBlockAccess access, int blockX, int blockY, int blockZ) {
		this.xOffset = blockX - NEIGHBOR_BLOCK_RADIUS;
		this.yOffset = blockY - NEIGHBOR_BLOCK_RADIUS;
		this.zOffset = blockZ - NEIGHBOR_BLOCK_RADIUS;

		this.level = access;

		Arrays.fill(this.light, 0);
	}

    private int index(int x, int y, int z) {
        int x2 = x - this.xOffset;
        int y2 = y - this.yOffset;
        int z2 = z - this.zOffset;

        return (z2 * BLOCK_LENGTH * BLOCK_LENGTH) + (y2 * BLOCK_LENGTH) + x2;
    }

    @Override
    public int get(int x, int y, int z) {
        int l = this.index(x, y, z);

        int word = this.light[l];

        if (word != 0) {
            return word;
        }

        return this.light[l] = this.compute(x, y, z);
    }
}
