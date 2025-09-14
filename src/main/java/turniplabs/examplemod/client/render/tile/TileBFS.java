package turniplabs.examplemod.client.render.tile;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import turniplabs.examplemod.client.util.Direction;

import java.util.List;

public class TileBFS {
	private static final int TOTAL_STEPS = 4;

	private static final int X_SHIFT = 1;  // 0b00001
	private static final int Y_SHIFT = 4;  // 0b00100
	private static final int Z_SHIFT = 16; // 0b10000

	// XYZ pos, XYZ neg.
	private static int bitMask(int pos, int neg) {
		return pos | neg << 3;
	}

	private static int shiftX(int bitMask) {
		return X_SHIFT & bitMask;
	}

	private static int shiftY(int bitMask) {
		return Y_SHIFT & bitMask;
	}

	private static int shiftZ(int bitMask) {
		return Z_SHIFT & bitMask;
	}

	private int outwardDirections(TileRender tile, int playerX, int playerY, int playerZ) {
		return 0;
	}

	public boolean isOneSided(int dirMask) {
		// TODO: Prove this even works (??).
		return ((dirMask + (dirMask >> 1)) & 0b10_10_10) == 0;
	}

	private void traverseTiles(int playerX, int playerY, int playerZ) {
		@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
		List<TileRender> queue = new ReferenceArrayList<>(128);

		int position = 0;

		while (position < queue.size()) {
			TileRender tile = queue.get(position++);

			if (false /* skipIfFrustum || distanceCheck */) {
				continue;
			}

			int dirMask = outwardDirections(tile, playerX, playerY, playerZ);
			long visMask = tile.startPosition;

			for (int step = 0; step < TOTAL_STEPS; step++) {
				this.floodFill(tile, visMask, dirMask);
			}

			/* reprocess dirMask and mask it with tile.searchMask  */
			this.visitNeighbors(queue, tile, visMask, tile.searchMask);
		}

	}

	private void visitNeighbors(List<TileRender> queue, TileRender tile, long visMask, int dirMask) {
		if ((dirMask & (1 << Dir.YN)) != 0) {

		}

	}

	// There 6 directions to flood fill to X+, Y+, Z+
	// and X-, Y-, Z-. For detecting the direction a mask
	// that have a bit for possible flood-fill direction.
	private long floodFill(TileRender tile, long bitPos, int dirMask) {
		// +XYZ flood-fill.
		long bitPosXP = (bitPos << shiftX(dirMask)) & tile.occFacesMask[Dir.XP];
		long bitPosYP = (bitPos << shiftY(dirMask)) & tile.occFacesMask[Dir.YP];
		long bitPosZP = (bitPos << shiftZ(dirMask)) & tile.occFacesMask[Dir.ZP];

		bitPosXP |= bitPosYP | bitPosZP;

		dirMask >>= 5;

		// -XYZ flood-fill.
		long bitPosXN = (bitPos >> shiftX(dirMask)) & tile.occFacesMask[Dir.XN];
		long bitPosYN = (bitPos >> shiftY(dirMask)) & tile.occFacesMask[Dir.YN];
		long bitPosZN = (bitPos >> shiftZ(dirMask)) & tile.occFacesMask[Dir.ZN];

		bitPosXN |= bitPosYN | bitPosZN;

		return bitPosXP | bitPosXN;
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
