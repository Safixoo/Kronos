package turniplabs.examplemod.client.render;

public class SectionFlags {
	public static final int PASSES_NON_EMPTY    = 0b000000000000000000000011; // 0-1b
	public static final int EMPTY_SOLID_SECTION = 0b000000000000000000000100; // 2b
	public static final int ADJACENT_MASK       = 0b000000000000000111111000; // 3b
	public static final int CULL_FACES          = 0b000000000111111000000000; // 9b
	public static final int DRAWABLE_FACES      = 0b001111111000000000000000; // 15b
	public static final int DIRTY               = 0b010000000000000000000000; // 22b
	public static final int HAS_REGION          = 0b100000000000000000000000; // 23b

	public static int getPassesNonEmpty(int flags) {
		return (flags >>> 0 & PASSES_NON_EMPTY);
	}

	public static boolean hasSolidPass(int flags) {
		return (flags >>> 0 & 1) != 0;
	}

	public static boolean hasTranslucentPass(int flags) {
		return (flags >>> 0 & 2) != 0;
	}

	public static int setPassesNonEmpty(int flags, int nonEmpty) {
		return (flags & ~PASSES_NON_EMPTY) | nonEmpty << 0;
	}

	public static int hasPassesNonEmptyBit(int flags) {
		return (flags & PASSES_NON_EMPTY) != 0 ? 1 : 0;
	}

	public static boolean hasPassesNonEmpty(int flags) {
		return (flags & PASSES_NON_EMPTY) != 0;
	}

	public static int getAdjacentMask(int flags) {
		return (flags & ADJACENT_MASK) >>> 3;
	}

	public static int setAdjacentMask(int flags, int adjacent) {
		return (flags & ~ADJACENT_MASK) | (adjacent << 3);
	}

	public static boolean isEmptySolid(int flags) {
		return (flags & EMPTY_SOLID_SECTION) != 0;
	}

	public static boolean isNotEmptySolid(int flags) {
		return (flags & EMPTY_SOLID_SECTION) == 0;
	}

	public static int setEmptySolid(int flags, boolean emptySolid) {
		return (flags & ~EMPTY_SOLID_SECTION) | (emptySolid ? 1 : 0) << 2;
	}

	public static boolean hasRegion(int flags) {
		return (flags & HAS_REGION) != 0;
	}

	public static int setRegion(int flags, boolean yesOrNo) {
		return (flags & ~HAS_REGION) | (yesOrNo ? 1 : 0) << 23;
	}

	public static int getCullFaces(int flags) {
		return (flags & CULL_FACES) >>> 9;
	}

	public static int setCullFaces(int flags, int cullFaces) {
		return (flags & ~CULL_FACES) | (cullFaces << 9);
	}

	public static int getDrawableFaces(int flags) {
		return (flags & DRAWABLE_FACES) >>> 15;
	}

	public static boolean hasDrawableFaces(int flags) {
		return (flags & DRAWABLE_FACES) != 0;
	}

	public static int setDrawableFaces(int flags, int faces) {
		return (flags & ~DRAWABLE_FACES) | (faces << 15);
	}

	public static boolean isDirty(int flags) {
		return (flags & DIRTY) != 0;
	}

	public static int dirtyMask(int flags) {
		return (flags >>> 22) & 1;
	}

	public static int setDirty(int flags, boolean dirty) {
		return (flags & ~DIRTY) | ((dirty ? 1 : 0) << 22);
	}
}
