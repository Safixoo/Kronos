package dev.safixo.client.util;

import dev.safixo.client.render.pipelines.terrain.region.RegionConstants;

public class MeshDirection {
	public static final int YN = Direction.DOWN;
	public static final int YP = Direction.UP;

	public static final int XN = Direction.WEST;
	public static final int XP = Direction.EAST;

	public static final int ZN = Direction.NORTH;
	public static final int ZP = Direction.SOUTH;

	public static final int GENERIC = Direction.COUNT;
	public static final int COUNT = GENERIC + 1;

	public static final int ALL_MASK = (1 << COUNT) - 1;

	/**
	 * Returns a set of directions sorted by the active bits in the mask passed in parameter.
	 */
	public static int getSortedMeshOrder(int meshBitDirections) {
		int preferredCount = 0;
		int restCount = 0;

		int preferredDirSet = 0;
		int restDirectionSet = 0;

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			// Mesh directions are always less than 0xF.
			if ((meshBitDirections & (1 << dir)) != 0) {
				preferredDirSet |= (dir & 0xF) << (preferredCount++ * 4);
			} else {
				restDirectionSet |= (dir & 0xF) << (restCount++ * 4);
			}
		}

		return preferredDirSet | (restDirectionSet << (preferredCount * 4));
	}

	public static int getSortedMeshOrder(int visibleFaces, int solidMask) {
		int preferredCount = 0;
		int nonNullCount = 0;
		int restCount = 0;

		int preferredDirSet = 0;
		int nonNullSet = 0;
		int restDirectionSet = 0;

		visibleFaces &= solidMask;

		// Priority:
		// 1- Visible non-null faces.
		// 2- Non-null faces.
		// 3- Null faces.
		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			// Mesh directions are always less than 0xF.
			if ((visibleFaces & (1 << dir)) != 0) {
				preferredDirSet |= (dir & 0xF) << (preferredCount++ * 4);
			} else if ((solidMask & (1 << dir)) != 0) {
				nonNullSet |= (dir & 0xF) << (nonNullCount++ * 4);
			} else {
				restDirectionSet |= (dir & 0xF) << (restCount++ * 4);
			}
		}

		return preferredDirSet | (nonNullSet << preferredCount * 4) | (restDirectionSet << (nonNullCount + preferredCount) * 4);
	}

	/**
	 * Shuffles a mask to the new order imposed by the parameter.
	 */
	public static int shuffleByOrder(int mask, int offsetPerFacing) {
		int shuffled = 0;

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			int facingBit = mask >>> dir & 1;
			shuffled |= facingBit << getByIndex(offsetPerFacing, dir);
		}

		return shuffled;
	}

	/**
	 * Returns a set, that for each indexed direction gives its new position.
	 */
	public static int getOffsetsPerFacing(int sortedMeshOrder) {
		int mask = 0;

		for (int i = 0; i < MeshDirection.COUNT; i++) {
			for (int j = 0; j < MeshDirection.COUNT; j++) {
				if (getByIndex(sortedMeshOrder, j) == i) {
					mask |= j << (i * 4L);
				}
			}
		}

		return mask;
	}

	/**
	 * The facing that are not renderable are filled with 0xF, which is useful for some bit ops.
	 */
	public static int getNonNullOffsets(int drawMask, int offsetsPerFacing) {
		int solidMask = shuffleByOrder(RegionConstants.getSolidMask(drawMask), offsetsPerFacing);

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			if ((solidMask & (1 << dir)) == 0) {
				offsetsPerFacing |= 0xF << (dir * 4);
			}
		}

		return offsetsPerFacing;
	}

	public static int getByIndex(int set, int dir) {
		return (set >>> (dir * 4L)) & 0xF;
	}
}
