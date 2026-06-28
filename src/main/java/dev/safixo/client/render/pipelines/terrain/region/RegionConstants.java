package dev.safixo.client.render.pipelines.terrain.region;

public class RegionConstants {
	public static final RegionRender NULL = new RegionRender(null, 0, Integer.MIN_VALUE, 0);

	public static final int BLOCK_SHIFT_X = 7;
	public static final int BLOCK_SHIFT_Y = 7;
	public static final int BLOCK_SHIFT_Z = 7;

	public static final int BLOCK_BITS_X = (1 << BLOCK_SHIFT_X) - 1;
	public static final int BLOCK_BITS_Y = (1 << BLOCK_SHIFT_Y) - 1;
	public static final int BLOCK_BITS_Z = (1 << BLOCK_SHIFT_Z) - 1;

	public static final int RADIUS_X = 1 << (BLOCK_SHIFT_X - 1);
	public static final int RADIUS_Y = 1 << (BLOCK_SHIFT_Y - 1);
	public static final int RADIUS_Z = 1 << (BLOCK_SHIFT_Z - 1);

	public static final int DIAMETER_X = 1 << (BLOCK_SHIFT_X);
	public static final int DIAMETER_Y = 1 << (BLOCK_SHIFT_Y);
	public static final int DIAMETER_Z = 1 << (BLOCK_SHIFT_Z);

	// Region total volume area in SectionRenders.
	public static final int REGION_SECTION_SIZE = 512; // 8 * 8 * 8
}
