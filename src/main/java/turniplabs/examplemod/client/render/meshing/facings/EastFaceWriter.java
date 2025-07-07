package turniplabs.examplemod.client.render.meshing.facings;

import net.minecraft.core.block.Block;
import turniplabs.examplemod.client.vertex.VertexWriterManager;
import turniplabs.examplemod.client.render.meshing.BlockRenderer;
import turniplabs.examplemod.client.render.meshing.FaceWriterWrapper;
import turniplabs.examplemod.client.render.data.ModelBoundsData;
import turniplabs.examplemod.client.util.Direction;

public class EastFaceWriter extends FaceWriterWrapper {
	@Override
	public void colorizeAndBufferQuad(BlockRenderer blockRenderer, ModelBoundsData modelData, Block<?> block, int x, int y, int z, int color) {
		blockRenderer.colorizeQuad(block, x, y, z, Direction.DOWN, 1, 0, 0, 1.0F - modelData.maxX, 0, 0, 1, modelData.maxZ, modelData.minZ, 0, -1, 0, 1.0F - modelData.minY, 1.0F - modelData.maxY, color);

		this.vertexOne(modelData, blockRenderer.colorTopLeft | 0xFF000000);
		this.vertexTwo(modelData, blockRenderer.colorBottomLeft | 0xFF000000);
		this.vertexThree(modelData, blockRenderer.colorBottomRight | 0xFF000000);
		this.vertexFour(modelData, blockRenderer.colorTopRight | 0xFF000000);
	}

	public void vertexOne(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.x1, model.y0, model.z1);
		man.setUv(minU, minV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordTopLeft);

		man.addVertex();
	}

	public void vertexTwo(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.x1, model.y0, model.z0);
		man.setUv(maxU, minV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordBottomLeft);

		man.addVertex();
	}

	public void vertexThree(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.x1, model.y1, model.z0);
		man.setUv(maxU, maxV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordBottomRight);

		man.addVertex();
	}

	public void vertexFour(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.x1, model.y1, model.z1);
		man.setUv(minU, maxV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordTopRight);

		man.addVertex();
	}
}
