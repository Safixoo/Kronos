package dev.safixo.client.render.pipelines.terrain.meshing.vanilla;

import dev.safixo.client.util.Direction;
import net.minecraft.client.renderer.RenderBlocks;

public class ABBIndexed {
	public static final int MIN_Y = Direction.DOWN + 1;
	public static final int MAX_Y = Direction.UP + 1;

	public static final int MIN_Z = Direction.NORTH + 1;
	public static final int MAX_Z = Direction.SOUTH + 1;

	public static final int MIN_X = Direction.WEST + 1;
	public static final int MAX_X = Direction.EAST + 1;

	public static void aabbToArray(RenderBlocks blocks, float[] bounds) {
		bounds[MIN_Y] = (float) (blocks.renderMinY);
		bounds[MIN_Z] = (float) (blocks.renderMinZ);
		bounds[MIN_X] = (float) (blocks.renderMinX);

		bounds[MAX_Y] = (float) (blocks.renderMaxY);
		bounds[MAX_Z] = (float) (blocks.renderMaxZ);
		bounds[MAX_X] = (float) (blocks.renderMaxX);
	}
}
