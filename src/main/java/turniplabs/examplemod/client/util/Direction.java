package turniplabs.examplemod.client.util;

import net.minecraft.core.util.helper.Side;

public class Direction {
	public static final int DOWN    = 0;
	public static final int UP      = 1;
	public static final int NORTH   = 2;
	public static final int SOUTH   = 3;
	public static final int WEST    = 4;
	public static final int EAST    = 5;

	public static final int DOWN_SET    = 1 << 0;
	public static final int UP_SET      = 1 << 1;
	public static final int NORTH_SET   = 1 << 2;
	public static final int SOUTH_SET   = 1 << 3;
	public static final int WEST_SET    = 1 << 4;
	public static final int EAST_SET    = 1 << 5;


	public static final int COUNT   = 6;

	private static final Side[] ENUMS;
	private static final int[] OPPOSITE;
	private static final byte[] X, Y, Z;

	static {
		OPPOSITE = new int[COUNT];
		OPPOSITE[DOWN] = UP;
		OPPOSITE[UP] = DOWN;
		OPPOSITE[NORTH] = SOUTH;
		OPPOSITE[SOUTH] = NORTH;
		OPPOSITE[WEST] = EAST;
		OPPOSITE[EAST] = WEST;

		X = new byte[COUNT];
		X[WEST] = -1;
		X[EAST] = 1;

		Y = new byte[COUNT];
		Y[DOWN] = -1;
		Y[UP] = 1;

		Z = new byte[COUNT];
		Z[NORTH] = -1;
		Z[SOUTH] = 1;

		ENUMS = new Side[COUNT];
		ENUMS[DOWN] = Side.BOTTOM;
		ENUMS[UP] = Side.TOP;
		ENUMS[NORTH] = Side.NORTH;
		ENUMS[SOUTH] = Side.SOUTH;
		ENUMS[WEST] = Side.WEST;
		ENUMS[EAST] = Side.EAST;
	}

	public static int opposite(int direction) {
		return direction ^ 1;
	}

	public static int set(int direction) {
		return 1 << direction;
	}

	public static boolean hasSet(int num, int dir) {
		return (num & dir) != 0;
	}

	public static int x(int direction) {
		return X[direction];
	}

	public static int y(int direction) {
		return Y[direction];
	}

	public static int z(int direction) {
		return Z[direction];
	}

	public static Side toEnum(int direction) {
		return ENUMS[direction];
	}
}
