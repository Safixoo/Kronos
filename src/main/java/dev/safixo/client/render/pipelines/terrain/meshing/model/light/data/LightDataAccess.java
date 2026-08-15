package dev.safixo.client.render.pipelines.terrain.meshing.model.light.data;

import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.PrimitivesFlags;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import org.joml.Vector3i;

/**
 * The light data cache is used to make accessing the light data and occlusion properties of blocks cheaper. The data
 * for each block is stored as an integer with packed fields in order to work around the lack of value types in Java.
 * <p>
 * This code is not very pretty, but it does perform significantly faster than the vanilla implementation and has
 * good cache locality.
 * <p>
 * Each integer contains the following fields:
 * - BL: World block light, encoded as a 4-bit unsigned integer
 * - SL: World sky light, encoded as a 4-bit unsigned integer
 * - LU: Block luminance, encoded as a 4-bit unsigned integer
 * - AO: Ambient occlusion, floating point value in the range of 0.0..1.0 encoded as a 16-bit unsigned integer with 12-bit precision
 * - EM: Emissive test, true if block uses emissive lighting
 * - OP: Block opacity test, true if opaque
 * - FO: Full cube opacity test, true if opaque full cube
 * - FC: Full cube test, true if full cube
 * <p>
 * You can use the various static pack/unpack methods to extract these values in a usable format.
 */
public abstract class LightDataAccess {
    protected IBlockAccess level;

    public int get(int x, int y, int z, int d1, int d2) {
        return this.get(x + Direction.x(d1) + Direction.x(d2),
                y + Direction.y(d1) + Direction.y(d2),
                z + Direction.z(d1) + Direction.z(d2));
    }

    public int get(int x, int y, int z, int dir) {
        return this.get(x + Direction.x(dir),
                y + Direction.y(dir),
                z + Direction.z(dir));
    }

    public int get(Vector3i pos, int dir) {
        return this.get(pos.x, pos.y, pos.z, dir);
    }

    public int get(Vector3i pos) {
        return this.get(pos.x, pos.y, pos.z);
    }

	private static boolean testFullBlock(Block block) {
		return
			block.getBlockBoundsMinX() <= 0.001F && block.getBlockBoundsMaxX() >= 0.999F &&
			block.getBlockBoundsMinY() <= 0.001F && block.getBlockBoundsMaxY() >= 0.999F &&
			block.getBlockBoundsMinZ() <= 0.001F && block.getBlockBoundsMaxZ() >= 0.999F;
	}

    /**
     * Returns the light data for the block at the given position. The property fields can then be accessed using
     * the various unpack methods below.
     */
    public abstract int get(int x, int y, int z);

    protected int compute(int x, int y, int z) {
        IBlockAccess level = this.level;

        int blockId = level.getBlockId(x, y, z);
		Block block = Block.blocksList[blockId];

		boolean fo = PrimitivesFlags.SOLID[blockId];
		boolean op = PrimitivesFlags.NORMAL_BLOCK[blockId];

        int lu = Block.lightValue[blockId];
		boolean em = lu != 0;

        int bl = lu;
        int sl = 0;
		if (!fo) {
			int light = level.getLightBrightnessForSkyBlocks(x, y, z, lu);
			bl = Math.max(bl, MathExt.getBlocklight(light));
			sl = MathExt.getSkylight(light);
		}

		// FIX: Do not apply AO from blocks that emit light
        float ao;
        if (lu == 0 && block != null) {
            ao = block.getAmbientOcclusionLightValue(level, x, y, z);
        } else {
            ao = 1.0f;
        }

        return packFO(fo) | packOP(op) | packEM(em) | packAO(ao) | packSL(sl) | packBL(bl);
    }

    public static int packBL(int blockLight) {
        return blockLight & 0xF;
    }

    public static int unpackBL(int word) {
        return word & 0xF;
    }

    public static int packSL(int skyLight) {
        return (skyLight & 0xF) << 4;
    }

    public static int unpackSL(int word) {
        return (word >>> 4) & 0xF;
    }

    public static int packAO(float ao) {
        int aoi = (int) (ao * 4096.0f);
        return (aoi & 0xFFFF) << 12;
    }

    public static float unpackAO(int word) {
        int aoi = (word >>> 12) & 0xFFFF;
        return aoi * (1.0f / 4096.0f);
    }

    public static int packEM(boolean emissive) {
        return (emissive ? 1 : 0) << 28;
    }

    public static boolean unpackEM(int word) {
        return ((word >>> 28) & 0b1) != 0;
    }

    public static int packOP(boolean opaque) {
        return (opaque ? 1 : 0) << 29;
    }

    public static boolean unpackOP(int word) {
        return ((word >>> 29) & 0b1) != 0;
    }

    public static int packFO(boolean opaque) {
        return (opaque ? 1 : 0) << 30;
    }

    public static boolean unpackFO(int word) {
        return ((word >>> 30) & 0b1) != 0;
    }

    public static int getLightmap(int word) {
        return MathExt.getLightmapCoord(unpackSL(word), unpackBL(word));
    }

    public static int getEmissiveLightmap(int word) {
        if (unpackEM(word)) {
            return MathExt.getLightmapCoord(15, 15);
        } else {
            return getLightmap(word);
        }
    }

    public IBlockAccess getLevel() {
        return this.level;
    }
}
