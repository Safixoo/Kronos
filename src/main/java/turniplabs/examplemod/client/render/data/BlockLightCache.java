package turniplabs.examplemod.client.render.data;

import net.minecraft.core.world.WorldSource;
import turniplabs.examplemod.client.util.BlocksFlags;

public class BlockLightCache {
	private int opacityMask;
	private int lightMapMask;

	private final int[] lightmapCoordValue = new int[27];
	private int opacityValue;

	private int offsetX = Integer.MAX_VALUE;
	private int offsetY = Integer.MAX_VALUE;
	private int offsetZ = Integer.MAX_VALUE;

	private WorldSource access;

	public void setupCache(WorldSource access, int x, int y, int z) {
		this.access = access;

		this.lightMapMask = 0;
		this.opacityMask = 0;
		this.opacityValue = 0;

		this.offsetX = x;
		this.offsetY = y;
		this.offsetZ = z;
	}

	public float getBrightness(int relX, int relY, int relZ) {
		return this.getOpacity(relX, relY, relZ) ? 0.0f : 1.0f;
	}

	public boolean getOpacity(int relX, int relY, int relZ) {
		int index = (relX * 9 + relY * 3 + relZ) + 13;

		if ((this.opacityMask & (1L << index)) == 0) {
			boolean isSolid = BlocksFlags.SOLID[this.access.getBlockId(relX + this.offsetX, relY + this.offsetY, relZ + this.offsetZ)];

			this.opacityValue |= (isSolid ? 1 : 0) << index;
			this.opacityMask |= 1 << index;

			return isSolid;
		}

		return (this.opacityValue & (1 << index)) != 0;
	}

	public int getLightmapCoord(int relX, int relY, int relZ) {
		int index = (relX * 9 + relY * 3 + relZ) + 13;

		if ((this.lightMapMask & (1L << index)) == 0) {
			this.lightMapMask |= 1 << index;
			return this.lightmapCoordValue[index] = this.access.getLightmapCoord(relX + this.offsetX, relY + this.offsetY, relZ + this.offsetZ, 0);
		}

		return this.lightmapCoordValue[index];
	}
}
