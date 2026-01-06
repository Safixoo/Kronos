package dev.safixo.client.render.pipelines.terrain.meshing.vanilla;

import java.util.Arrays;

/**
 * For possibly complex models is convenient cache light results, this is usually applied to simple full blocks given
 * how simple they are to mesh, which makes adding a cache ultimately simply an indirection more.
 */
public class LightBlockCache {
	public int[] blockCache = new int[3 * 3 * 3];
	public int blockX, blockY, blockZ;

	public LightBlockCache() {

	}

	public void resetAndSet(int x, int y, int z) {
		this.blockX = x;
		this.blockY = y;
		this.blockZ = z;
		Arrays.fill(this.blockCache, 0);
	}

	public int getLightmap(int x, int y, int z) {
		return 0;
	}

	public boolean isSolidBlock(int x, int y, int z) {
		return false;
	}
}
