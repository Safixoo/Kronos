package turniplabs.examplemod.client.render.tile;

import turniplabs.examplemod.client.util.Direction;

// 4x4x4 in section space (64x64x64 in blocks).
public class TileRender {
	public final int tileX, tileY, tileZ;
	public final TileRender[] adjacents = new TileRender[Direction.COUNT];

	public int activeFrame, searchMask;

	public final long[] occFacesMask = new long[Direction.COUNT];
	public long startPosition, nonEmptyMask;

	public TileRender(int tileX, int tileY, int tileZ) {
		this.tileX = tileX;
		this.tileY = tileY;
		this.tileZ = tileZ;
	}

	public void saveOccResult(long occ, long index) {
		// NOT it now so it doesn't have to do it later
		// to mask-off results in the tile BFS.
		occ = ~occ;

		this.occFacesMask[Dir.YN] |= (occ >> Dir.YN) << index;
		this.occFacesMask[Dir.YP] |= (occ >> Dir.YP) << index;

		this.occFacesMask[Dir.ZN] |= (occ >> Dir.ZN) << index;
		this.occFacesMask[Dir.ZP] |= (occ >> Dir.ZP) << index;

		this.occFacesMask[Dir.XN] |= (occ >> Dir.XN) << index;
		this.occFacesMask[Dir.XP] |= (occ >> Dir.XP) << index;
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
