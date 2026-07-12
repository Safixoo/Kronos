package dev.safixo.client.render.pipelines.terrain;

public class SectionFlags {
	public static final int DIRTY_FLAG = SectionFlags.setDirty(0b0, true);
	public static final int FULL_SOLID_FLAG = SectionFlags.setSolidFaces(0b0, 0x3F);

	public static final int SECTION_INVALID = (FULL_SOLID_FLAG);
	public static final int SECTION_DEFAULT_DIRTY = (FULL_SOLID_FLAG | DIRTY_FLAG);

	private static final int SOLID_FACES_OFFSET = 0;
	private static final int DIRTY_OFFSET       = 6;
	private static final int PASSES_OFFSET      = 7;

	private static final int PASSES      = 0b1 << PASSES_OFFSET;
	private static final int SOLID_FACES = 0b111111 << SOLID_FACES_OFFSET;
	private static final int DIRTY       = 0b1 << DIRTY_OFFSET;

	public static int setPassesNonEmpty(int flags, int nonEmpty) {
		return (flags & ~PASSES) | (nonEmpty << PASSES_OFFSET) & PASSES;
	}

	public static boolean hasPassesNonEmpty(int flags) {
		return (flags & PASSES) != 0;
	}

	public static boolean hasRenderTasks(int flags) {
		return (flags & (PASSES | DIRTY)) != 0;
	}

	public static int getSolidFaces(int flags) {
		return (flags & SOLID_FACES) >>> SOLID_FACES_OFFSET;
	}

	public static int setSolidFaces(int flags, int cullFaces) {
		return (flags & ~SOLID_FACES) | (cullFaces & SOLID_FACES) << SOLID_FACES_OFFSET;
	}

	public static boolean isDirty(int flags) {
		return (flags & DIRTY) != 0;
	}

	public static int setDirty(int flags, boolean dirty) {
		return (flags & ~DIRTY) | (dirty ? DIRTY : 0);
	}
}
