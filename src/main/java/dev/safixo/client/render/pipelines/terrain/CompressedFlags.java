package dev.safixo.client.render.pipelines.terrain;

public class CompressedFlags {
	public static final int TRAVERSABLE_FACES   = 0b00111111; // 1-6b
	public static final int PASSES_NON_EMPTY    = 0b01000000; // 7b
	public static final int DIRTY               = 0b10000000; // 8b

	public static int sectionToCompressed(int sectionFlag) {
		int compressedFlags = 0b0;

		compressedFlags = setTraversableFaces(compressedFlags, (SectionFlags.getAdjacentMask(sectionFlag) & ~SectionFlags.getCullFaces(sectionFlag)) & 0b111_111);
		compressedFlags = setDirty(compressedFlags, SectionFlags.isDirty(sectionFlag));
		compressedFlags = setPassesNonEmpty(compressedFlags, SectionFlags.hasPassesNonEmpty(sectionFlag) ? 1 : 0);

		return compressedFlags;
	}

	public static int setPassesNonEmpty(int flags, int nonEmpty) {
		return (flags & ~PASSES_NON_EMPTY) | nonEmpty << 6;
	}

	public static int setDirty(int flags, boolean dirty) {
		return (flags & ~DIRTY) | ((dirty ? 1 : 0) << 7);
	}

	public static int setTraversableFaces(int flags, int cullFaces) {
		return (flags & ~TRAVERSABLE_FACES) | cullFaces;
	}

	public static boolean isDirty(int flags) {
		return (flags & DIRTY) != 0;
	}

	public static boolean hasPassesNonEmpty(int flags) {
		return (flags & PASSES_NON_EMPTY) != 0;
	}

	public static int getTraversableFaces(int flags) {
		return flags;
	}
}
