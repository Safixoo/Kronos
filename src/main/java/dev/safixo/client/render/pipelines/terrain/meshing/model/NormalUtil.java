package dev.safixo.client.render.pipelines.terrain.meshing.model;

import dev.safixo.client.util.MeshDirection;

// Detects the model normal and tries to assign a possible direction
// to which is facing if it is axis fixed.
public class NormalUtil {
	// Cross product "determinant"
	// | nX nY nZ |
	// | x0 y0 z0 |
	// | x2 y2 z2 |
	public static int assignNormalBitset(float x0, float y0, float z0,
										 float x1, float y1, float z1,
										 float x2, float y2, float z2) {
		// Later checks are gonna be reversed because the substraction here
		// is reversed versus what it should be.
		x2 -= x1;
		y2 -= y1;
		z2 -= z1;

		x0 -= x1;
		y0 -= y1;
		z0 -= z1;

		// Cross product.
		float nX = y0 * z2 - z0 * y2;
		float nY = z0 * x2 - x0 * z2;
		float nZ = x0 * y2 - y0 * x2;

		// Quantify the nullity of each axis of the normal.
		boolean near0X = equalZero(nX);
		boolean near0Y = equalZero(nY);
		boolean near0Z = equalZero(nZ);

		int dir = MeshDirection.GENERIC;

		// If some normal doesn't fall in these checks it means
		// that it is not an aligned normal and should be
		// classified undefined for all purposes.
		if (near0X && near0Y) {
			// Z normal
			dir = nZ < 0 ? MeshDirection.ZP : MeshDirection.ZN;
		} else if (near0Y && near0Z) {
			// X normal
			dir = nX < 0 ? MeshDirection.XP : MeshDirection.XN;
		} else if (near0Z && near0X) {
			// Y normal
			dir = nY < 0 ? MeshDirection.YP : MeshDirection.YN;
		}

		return dir;
	}

	private static boolean equalZero(float a) {
		return Math.abs(a) < 0.005F;
	}
}
