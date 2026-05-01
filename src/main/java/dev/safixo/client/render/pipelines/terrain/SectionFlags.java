package dev.safixo.client.render.pipelines.terrain;

public class SectionFlags {
	public static final int PASSES_NON_EMPTY    = 0b000000000000011; // 1-2b
	public static final int ADJACENT_MASK       = 0b000000011111100; // 3-8b
	public static final int CULL_FACES          = 0b011111100000000; // 9-15b
	public static final int DIRTY               = 0b100000000000000; // 16b

	public static int setPassesNonEmpty(int flags, int nonEmpty) {
		return (flags & ~PASSES_NON_EMPTY) | nonEmpty << 0;
	}

	public static boolean hasPassesNonEmpty(int flags) {
		return (flags & PASSES_NON_EMPTY) != 0;
	}

	public static int getPassesNonEmpty(int flags) {
		return (flags & PASSES_NON_EMPTY);
	}

	public static int getAdjacentMask(int flags) {
		return (flags & ADJACENT_MASK) >>> 2;
	}

	public static int setAdjacentMask(int flags, int adjacent) {
		return (flags & ~ADJACENT_MASK) | (adjacent << 2);
	}

	public static int getCullFaces(int flags) {
		return (flags & CULL_FACES) >>> 8;
	}

	public static int setCullFaces(int flags, int cullFaces) {
		return (flags & ~CULL_FACES) | (cullFaces << 8);
	}

	public static boolean isDirty(int flags) {
		return (flags & DIRTY) != 0;
	}

	public static int setDirty(int flags, boolean dirty) {
		return (flags & ~DIRTY) | ((dirty ? 1 : 0) << 14);
	}
}
