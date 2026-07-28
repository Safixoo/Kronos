package dev.safixo.client.render.pipelines.terrain.meshing.model;

import dev.safixo.client.render.pipelines.terrain.meshing.data.Quad;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.MeshDirection;

// Detects the model normal and tries to assign a possible direction
// to which is facing if it is axis fixed.
public class NormalUtil {
	private static final int[] ALIGNED_NORMAL = new int[Direction.COUNT];
	private static final int NOT_BITSET_MASK = 0x80000000;

	static {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			ALIGNED_NORMAL[dir] = MathExt.packedNormal(Direction.x(dir), Direction.y(dir), Direction.z(dir));
		}
	}

	// Cross product "determinant"
	// | nX nY nZ |
	// | x0 y0 z0 |
	// | x2 y2 z2 |
	public static int assignNormal(Quad quad) {
		// Later checks are gonna be reversed because the substraction here
		// is reversed versus what it should be.
		float x2 = quad.getX(2) - quad.getX(1);
		float y2 = quad.getY(2) - quad.getY(1);
		float z2 = quad.getZ(2) - quad.getZ(1);

		float x0 = quad.getX(0) - quad.getX(1);
		float y0 = quad.getY(0) - quad.getY(1);
		float z0 = quad.getZ(0) - quad.getZ(1);

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

		if (dir == MeshDirection.GENERIC) {
			return normalizeAndPackNormal(nX, nY, nZ);
		}

		return dir;
	}

	static final boolean NORMALIZE = false;

	static int normalizeAndPackNormal(float x, float y, float z) {
		if (NORMALIZE) {
			float invSqrt = 1.0f / (float) Math.sqrt(x * x + y * y + z * z);

			x *= invSqrt;
			y *= invSqrt;
			z *= invSqrt;
		}

		return MathExt.packedNormal(x, y, z) | NOT_BITSET_MASK;
	}

	public static int getNormalVector(int normal) {
		return (normal & NOT_BITSET_MASK) == 0 ? ALIGNED_NORMAL[normal] : normal & ~NOT_BITSET_MASK;
	}

	public static int getNormalEnum(int normal) {
		return (normal & NOT_BITSET_MASK) != 0 ? MeshDirection.GENERIC : normal;
	}

	private static boolean equalZero(float a) {
		return Math.abs(a) < 0.005F;
	}
}
