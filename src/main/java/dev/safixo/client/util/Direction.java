package dev.safixo.client.util;

import net.minecraftforge.common.ForgeDirection;
import org.joml.Vector3i;

public class Direction {
	public static final int DOWN    = 0;
	public static final int UP      = 1;
	public static final int NORTH   = 2;
	public static final int SOUTH   = 3;
	public static final int WEST    = 4;
	public static final int EAST    = 5;

	public static final int COUNT   = 6;

	private static final ForgeDirection[] ENUMS;
	private static final byte[] X, Y, Z;

	private static final Vector3i[] DIRECTIONS = new Vector3i[COUNT];

	static {
		X = new byte[COUNT];
		X[WEST] = -1;
		X[EAST] = 1;

		Y = new byte[COUNT];
		Y[DOWN] = -1;
		Y[UP] = 1;

		Z = new byte[COUNT];
		Z[NORTH] = -1;
		Z[SOUTH] = 1;

		ENUMS = new ForgeDirection[COUNT];
		ENUMS[DOWN] = ForgeDirection.DOWN;
		ENUMS[UP] = ForgeDirection.UP;
		ENUMS[NORTH] = ForgeDirection.NORTH;
		ENUMS[SOUTH] = ForgeDirection.SOUTH;
		ENUMS[WEST] = ForgeDirection.WEST;
		ENUMS[EAST] = ForgeDirection.EAST;

		for (int dir = 0; dir < COUNT; dir++) {
			DIRECTIONS[dir] = new Vector3i(X[dir], Y[dir], Z[dir]);
		}
	}

	public static int opposite(int direction) {
		return direction ^ 1;
	}

	public static int set(int direction) {
		return 1 << direction;
	}

	public static boolean hasSet(int num, int dir) {
		return (num & (1 << dir)) != 0;
	}

	public static byte x(int direction) {
		return X[direction];
	}

	public static byte y(int direction) {
		return Y[direction];
	}

	public static byte z(int direction) {
		return Z[direction];
	}

	public static Vector3i getDirection(int index) {
		return DIRECTIONS[index];
	}

	public static ForgeDirection toEnum(int direction) {
		return ENUMS[direction];
	}
}
