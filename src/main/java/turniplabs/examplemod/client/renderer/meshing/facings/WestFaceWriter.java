package turniplabs.examplemod.client.renderer.meshing.facings;

import net.minecraft.core.block.Block;
import turniplabs.examplemod.client.VertexWriterManager;
import turniplabs.examplemod.client.renderer.BlockRenderer;
import turniplabs.examplemod.client.renderer.meshing.FaceWriterWrapper;
import turniplabs.examplemod.client.renderer.meshing.ModelBoundsData;
import turniplabs.examplemod.client.util.Direction;

public class WestFaceWriter extends FaceWriterWrapper {
	@Override
	public void colorizeAndBufferQuad(BlockRenderer blockRenderer, ModelBoundsData modelData, Block<?> block, int x, int y, int z, int color) {
		blockRenderer.colorizeQuad(block, x, y, z, Direction.WEST, -1, 0, 0, modelData.minXB, 0, 0, 1, modelData.maxZB, modelData.minZB, 0, 1, 0, modelData.maxYB, modelData.minYB, color);

		this.vertexOne(modelData, blockRenderer.colorTopLeft | 0xFF000000);
		this.vertexTwo(modelData, blockRenderer.colorBottomLeft | 0xFF000000);
		this.vertexThree(modelData, blockRenderer.colorBottomRight | 0xFF000000);
		this.vertexFour(modelData, blockRenderer.colorTopRight | 0xFF000000);
	}

	public void vertexOne(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.minX, model.maxY, model.maxZ);
		man.setUv(maxU, maxV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordTopLeft);

		man.addVertex();
	}

	public void vertexTwo(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.minX, model.maxY, model.minZ);
		man.setUv(minU, maxV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordBottomLeft);

		man.addVertex();
	}

	public void vertexThree(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.minX, model.minY, model.minZ);
		man.setUv(minU, minV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordBottomRight);

		man.addVertex();
	}

	public void vertexFour(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.minX, model.minY, model.maxZ);
		man.setUv(maxU, minV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordTopRight);

		man.addVertex();
	}
}
