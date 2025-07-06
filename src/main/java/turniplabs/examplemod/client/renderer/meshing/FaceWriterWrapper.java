package turniplabs.examplemod.client.renderer.meshing;

import net.minecraft.core.block.Block;
import turniplabs.examplemod.client.renderer.BlockRenderer;

public abstract class FaceWriterWrapper {
	public float minU, minV, maxU, maxV;

	public void setUV(float maxU, float maxV, float minU, float minV) {
		this.minU = minU;
		this.minV = minV;
		this.maxU = maxU;
		this.maxV = maxV;
	}

	public abstract void colorizeQuad(BlockRenderer blockRenderer, ModelBoundsData bounds, Block<?> block, int x, int y, int z, int color);
	public abstract void bufferQuad(ModelBoundsData modelData, int colorTopLeft, int colorBottomLeft, int colorBottomRight, int colorTopRight);
}
