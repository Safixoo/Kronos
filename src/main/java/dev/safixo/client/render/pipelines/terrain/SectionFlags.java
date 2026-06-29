package dev.safixo.client.render.pipelines.terrain;

public class SectionFlags {
	public static final int PASSES_NON_EMPTY    = 0b0000000000000011; // 1-2b
	public static final int ADJACENT_MASK       = 0b0000000011111100; // 3-8b
	public static final int CULL_FACES          = 0b0011111100000000; // 9-14b
	public static final int DIRTY               = 0b0100000000000000; // 15b
	public static final int VALID             = 0b1000000000000000; // 15b

	public static int setValid(int flags, boolean valid) {
		return (flags & ~VALID) | (valid ? 1 : 0);
	}

	public static boolean isValid(int flags) {
		return (flags & VALID) != 0;
	}

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
