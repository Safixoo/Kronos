package turniplabs.examplemod.client.renderer.meshing.facings;

import net.minecraft.client.render.LightmapHelper;
import net.minecraft.core.block.Block;
import net.minecraft.core.util.phys.AABB;
import turniplabs.examplemod.client.VertexWriterManager;
import turniplabs.examplemod.client.renderer.BlockRenderer;
import turniplabs.examplemod.client.renderer.meshing.FaceWriterWrapper;
import turniplabs.examplemod.client.renderer.meshing.ModelBoundsData;
import turniplabs.examplemod.client.util.Direction;

public class NorthFaceWriter extends FaceWriterWrapper {
	@Override
	public void colorizeQuad(BlockRenderer blockRenderer, ModelBoundsData bounds, Block<?> block, int x, int y, int z, int color) {
		blockRenderer.colorizeQuad(block, x, y, z, Direction.NORTH, 0, 0, -1, bounds.minZB, -1, 0, 0, 1.0F - bounds.minXB, 1.0F - bounds.maxXB, 0, 1, 0, bounds.maxYB, bounds.minYB, color);
	}

	@Override
	public void bufferQuad(ModelBoundsData modelData, int colorTopLeft, int colorBottomLeft, int colorBottomRight, int colorTopRight) {
		this.vertexOne(modelData, colorTopLeft | 0xFF000000);
		this.vertexTwo(modelData, colorBottomLeft | 0xFF000000);
		this.vertexThree(modelData, colorBottomRight | 0xFF000000);
		this.vertexFour(modelData, colorTopRight | 0xFF000000);
	}

	public void vertexOne(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.minX, model.maxY, model.minZ);
		man.setUv(maxU, maxV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordTopLeft);
		man.addVertex();
	}

	public void vertexTwo(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.maxX, model.maxY, model.minZ);
		man.setUv(minU, maxV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordBottomLeft);

		man.addVertex();
	}

	public void vertexThree(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.maxX, model.minY, model.minZ);
		man.setUv(minU, minV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordBottomRight);

		man.addVertex();
	}

	public void vertexFour(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.minX, model.minY, model.minZ);
		man.setUv(maxU, minV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordTopRight);


		man.addVertex();
	}
}
