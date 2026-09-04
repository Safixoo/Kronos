package dev.safixo.client.render.pipelines.terrain.region;

import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MeshDirection;

public class RegionConstants {
	public static final RegionRender NULL = new RegionRender(null, 0, Integer.MIN_VALUE, 0);

	public static final int BLOCK_SHIFT_X = 7;
	public static final int BLOCK_SHIFT_Y = 7;
	public static final int BLOCK_SHIFT_Z = 7;

	public static final int BLOCK_BITS_X = (1 << BLOCK_SHIFT_X) - 1;
	public static final int BLOCK_BITS_Y = (1 << BLOCK_SHIFT_Y) - 1;
	public static final int BLOCK_BITS_Z = (1 << BLOCK_SHIFT_Z) - 1;

	public static final int RADIUS_X = 1 << (BLOCK_SHIFT_X - 1);
	public static final int RADIUS_Y = 1 << (BLOCK_SHIFT_Y - 1);
	public static final int RADIUS_Z = 1 << (BLOCK_SHIFT_Z - 1);

	public static final int DIAMETER_X = 1 << (BLOCK_SHIFT_X);
	public static final int DIAMETER_Y = 1 << (BLOCK_SHIFT_Y);
	public static final int DIAMETER_Z = 1 << (BLOCK_SHIFT_Z);

	// Region total volume area in SectionRenders.
	public static final int REGION_SECTION_SIZE = 512; // 8 * 8 * 8

	// Count of different render-passes possibly dispatched.
	// - SOLID (0)
	// - TRANSLUCENT (1)
	public static final int RENDER_PASSES = 2;
	public static final int SOLID_PASS = 0, TRANSLUCENT_PASS = 1;

	public static final int TRANSLUCENT_DRAWS = 1;
	public static final int SOLID_DRAWS = MeshDirection.COUNT;
	public static final int TOTAL_DRAWS = SOLID_DRAWS + TRANSLUCENT_DRAWS;

	public static int getSolidMask(int drawMask) {
		return drawMask >>> 1;
	}

	public static int getTranslucentMask(int drawMask) {
		return drawMask & 0b1;
	}

	public static int getDrawMask(int solidBits, int translucentBit) {
		return solidBits << 1 | translucentBit & 1;
	}

	public static int getSectionVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
		int planes = 1 << MeshDirection.GENERIC;

		planes |= greaterThan(originX, (chunkX - 3)) << Direction.EAST;
		planes |= greaterThan(originY, (chunkY - 3)) << Direction.UP;
		planes |= greaterThan(originZ, (chunkZ - 3)) << Direction.SOUTH;

		planes |= lessThan(originX, (chunkX + 19)) << Direction.WEST;
		planes |= lessThan(originY, (chunkY + 19)) << Direction.DOWN;
		planes |= lessThan(originZ, (chunkZ + 19)) << Direction.NORTH;

		return planes;
	}

	public static int getSectionVisibleFaces(int meshOffsets, int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
		int planes = 1 << MeshDirection.getByIndex(meshOffsets, MeshDirection.GENERIC);

		planes |= greaterThan(originX, (chunkX - 3)) << MeshDirection.getByIndex(meshOffsets, Direction.EAST);
		planes |= greaterThan(originY, (chunkY - 3)) << MeshDirection.getByIndex(meshOffsets, Direction.UP);
		planes |= greaterThan(originZ, (chunkZ - 3)) << MeshDirection.getByIndex(meshOffsets, Direction.SOUTH);

		planes |= lessThan(originX, (chunkX + 19)) << MeshDirection.getByIndex(meshOffsets, Direction.WEST);
		planes |= lessThan(originY, (chunkY + 19)) << MeshDirection.getByIndex(meshOffsets, Direction.DOWN);
		planes |= lessThan(originZ, (chunkZ + 19)) << MeshDirection.getByIndex(meshOffsets, Direction.NORTH);

		return planes;
	}

	private static int lessThan(int a, int b) {
		return (a - b) >>> 31;
	}

	private static int greaterThan(int a, int b) {
		return (b - a) >>> 31;
	}

	public static int regionIndex(int sectionX, int sectionY, int sectionZ) {
		int bitsX = sectionX & (BLOCK_BITS_X >> 4);
		int bitsY = sectionY & (BLOCK_BITS_Y >> 4);
		int bitsZ = sectionZ & (BLOCK_BITS_Z >> 4);

		return (bitsX << 0) | (bitsY << 3) | (bitsZ << 6);
	}

	public static int getLocalSectionX(int regionIndex) {
		return (regionIndex & 0b000_000_111) >>> 0;
	}

	public static int getLocalSectionY(int regionIndex) {
		return (regionIndex & 0b000_111_000) >>> 3;
	}

	public static int getLocalSectionZ(int regionIndex) {
		return (regionIndex & 0b111_000_000) >>> 6;
	}
}
