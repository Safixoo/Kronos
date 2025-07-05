package turniplabs.examplemod.client.renderer.meshing;

import net.minecraft.core.util.helper.Side;
import turniplabs.examplemod.client.renderer.meshing.facings.*;

import static turniplabs.examplemod.client.util.Direction.*;

public class FaceDataWriters {
	private static final FaceWriterWrapper[] FACING_WRITERS = new FaceWriterWrapper[COUNT];

	static {
		FACING_WRITERS[DOWN] = new BottomFaceWriter();
		FACING_WRITERS[UP] = new TopFaceWriter();

		FACING_WRITERS[NORTH] = new NorthFaceWriter();
		FACING_WRITERS[SOUTH] = new SouthFaceWriter();

		FACING_WRITERS[EAST] = new EastFaceWriter();
		FACING_WRITERS[WEST] = new WestFaceWriter();
	}

	public static FaceWriterWrapper getWriterBySide(int side) {
		return FACING_WRITERS[side];
	}
}
