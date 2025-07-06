package turniplabs.examplemod.client.renderer.meshing;

import net.minecraft.core.block.Block;
import turniplabs.examplemod.client.renderer.BlockRenderer;

public abstract class FaceWriterWrapper {
	public float minU, minV, maxU, maxV;

	public void setUV(float maxU, float maxV, float minU, float minV) {
		this.maxU = maxU;
		this.maxV = maxV;
		this.minU = minU;
		this.minV = minV;
	}

	public abstract void colorizeAndBufferQuad(BlockRenderer blockRenderer, ModelBoundsData modelData, Block<?> block, int x, int y, int z, int color);
}
