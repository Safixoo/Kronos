package turniplabs.examplemod.client.render.tile;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import turniplabs.examplemod.client.util.Direction;

import java.util.List;

public class TileBFS {
	private static final int TOTAL_STEPS = 4;

	private static final int X_SHIFT = 1;  // 0b00001
	private static final int Y_SHIFT = 4;  // 0b00100
	private static final int Z_SHIFT = 16; // 0b10000

	private static final int X_BIT = 0;
	private static final int Y_BIT = 2;
	private static final int Z_BIT = 4;

	// XYZ pos, XYZ neg.
	private static int bitMask(int pos, int neg) {
		return pos | neg << 3;
	}

	private static int shiftX(int bitMask) {
		return X_SHIFT & ((bitMask >> Dir.XP) << X_BIT);
	}

	private static int shiftY(int bitMask) {
		return Y_SHIFT & ((bitMask >> Dir.YP) << Y_BIT);
	}

	private static int shiftZ(int bitMask) {
		return Z_SHIFT & ((bitMask >> Dir.ZP) << Z_BIT);
	}

	private int outwardTileDirections(TileRender tile, int playerX, int playerY, int playerZ) {
		int outwardDirections = 0;

		int playerTileX = (playerX >> 6);
		int playerTileY = (playerY >> 6);
		int playerTileZ = (playerZ >> 6);

		outwardDirections |= playerTileX >= tile.tileX ? 1 << Dir.XN : 0;
		outwardDirections |= playerTileX <= tile.tileX ? 1 << Dir.XP : 0;

		outwardDirections |= playerTileY >= tile.tileY ? 1 << Dir.YN : 0;
		outwardDirections |= playerTileY <= tile.tileY ? 1 << Dir.YP : 0;

		outwardDirections |= playerTileZ >= tile.tileZ ? 1 << Dir.ZN : 0;
		outwardDirections |= playerTileZ <= tile.tileZ ? 1 << Dir.ZP : 0;

		return outwardDirections;
	}

	public boolean isOneSided(int dirMask) {
		// TODO: Prove this even works (??).
		return ((dirMask + (dirMask >> 1)) & 0b10_10_10) == 0;
	}

	private void traverseTiles(int playerX, int playerY, int playerZ, int activeFrame) {
		@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
		List<TileRender> queue = new ReferenceArrayList<>(128);

		int position = 0;

		while (position < queue.size()) {
			TileRender tile = queue.get(position++);

			if (false /* skipIfFrustum || distanceCheck */) {
				continue;
			}

			int dirMask = outwardTileDirections(tile, playerX, playerY, playerZ);

			// fast-path, all sections in the tile are empty, skip the visibility check.
			if (tile.nonEmptyMask == 0) {
				this.visitNeighbors(queue, tile, -1, dirMask & tile.searchMask, activeFrame);
				continue;
			}

			long visMask = tile.startPosition;

			// TODO: Implement check for one signed flood-fill and if not
			//  use more complex generalFloodFill instead of simpleFloodFill.
			for (int step = 0; step < TOTAL_STEPS; step++) {
				visMask = this.simpleFloodFill(tile, visMask, dirMask);
			}

			this.visitNeighbors(queue, tile, visMask, dirMask & tile.searchMask, activeFrame);
		}

	}

	private void visitNeighbors(List<TileRender> queue, TileRender tile, long visMask, int dirMask, int activeFrame) {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			if ((dirMask & (1 << dir)) == 0) {
				continue;
			}

			TileRender adjacent = tile.adjacents[dir];

			if (adjacent.activeFrame == activeFrame) {
				continue;
			}

			adjacent.activeFrame = activeFrame;
			queue.add(adjacent);
		}

	}

	private int calculateInitMask() {
		return 0;
	}

	// There 6 directions to flood fill to X+, Y+, Z+ and X-, Y-, Z- for detecting
	// the direction a mask that have a bit for possible flood-fill direction.
	private long simpleFloodFill(TileRender tile, long bitPos, int dirMask) {
		// +XYZ flood-fill.
		long bitPosXP = (bitPos << shiftX(dirMask)) & tile.occFacesMask[Dir.XP];
		long bitPosYP = (bitPos << shiftY(dirMask)) & tile.occFacesMask[Dir.YP];
		long bitPosZP = (bitPos << shiftZ(dirMask)) & tile.occFacesMask[Dir.ZP];

		bitPosXP |= bitPosYP | bitPosZP;

		dirMask >>= 1;

		// -XYZ flood-fill.
		long bitPosXN = (bitPos >> shiftX(dirMask)) & tile.occFacesMask[Dir.XN];
		long bitPosYN = (bitPos >> shiftY(dirMask)) & tile.occFacesMask[Dir.YN];
		long bitPosZN = (bitPos >> shiftZ(dirMask)) & tile.occFacesMask[Dir.ZN];

		bitPosXN |= bitPosYN | bitPosZN;

		return bitPosXP | bitPosXN;
	}

	// Flood-fill in a way that supports cases where the flood-fill -XYZ and +XYZ at the
	// same time without sacrificing correctness in unnecessary sections.
	private long generalFloodFill(TileRender tile, long bitPos, int dirMask) {
		/* todo implement and detect. */
		return 0;
	}

	public static class Dir {
		public static int XP = Direction.EAST;
		public static int YP = Direction.UP;
		public static int ZP = Direction.SOUTH;

		public static int XN = Direction.WEST;
		public static int YN = Direction.DOWN;
		public static int ZN = Direction.NORTH;
	}
}
