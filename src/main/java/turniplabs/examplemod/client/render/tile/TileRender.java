package turniplabs.examplemod.client.render.tile;

import turniplabs.examplemod.client.util.Direction;

// 4x4x4 in section space (64x64x64 in blocks).
public class TileRender {
	public final int tileX, tileY, tileZ;
	public final TileRender[] adjacents = new TileRender[Direction.COUNT];
	public final long[] occFacesMask = new long[Direction.COUNT];

	public long startPosition;
	public int searchMask;

	public TileRender(int tileX, int tileY, int tileZ) {
		this.tileX = tileX;
		this.tileY = tileY;
		this.tileZ = tileZ;
	}

	public void saveOccResult(int occ, long index) {
		// NOT it now so it doesn't have to do it later
		// to mask-off results in the tile BFS.
		occ = ~occ;

		this.occFacesMask[Dir.YN] |= (long) (occ >> Dir.YN) << index;
		this.occFacesMask[Dir.YP] |= (long) (occ >> Dir.YP) << index;

		this.occFacesMask[Dir.ZN] |= (long) (occ >> Dir.ZN) << index;
		this.occFacesMask[Dir.ZP] |= (long) (occ >> Dir.ZP) << index;

		this.occFacesMask[Dir.XN]  |= (long) (occ >> Dir.XN) << index;
		this.occFacesMask[Dir.XP]  |= (long) (occ >> Dir.XP) << index;
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
