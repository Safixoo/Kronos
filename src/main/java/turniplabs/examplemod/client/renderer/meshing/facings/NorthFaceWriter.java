package turniplabs.examplemod.client.renderer.meshing.facings;

import net.minecraft.client.render.LightmapHelper;
import net.minecraft.core.util.phys.AABB;
import turniplabs.examplemod.client.VertexWriterManager;
import turniplabs.examplemod.client.renderer.meshing.FaceWriterWrapper;
import turniplabs.examplemod.client.renderer.meshing.ModelBoundsData;

public class NorthFaceWriter implements FaceWriterWrapper {
	private float minU, minV, maxU, maxV;

	@Override
	public void setUV(float minU, float minV, float maxU, float maxV) {
		this.minU = minU;
		this.minV = minV;
		this.maxU = maxU;
		this.maxV = maxV;
	}

	@Override
	public void vertexOne(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.minX, model.maxY, model.minZ);
		man.setUv(maxU, maxV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordTopLeft);
		man.addVertex();
	}

	@Override
	public void vertexTwo(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.maxX, model.maxY, model.minZ);
		man.setUv(minU, maxV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordBottomLeft);

		man.addVertex();
	}

	@Override
	public void vertexThree(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.maxX, model.minY, model.minZ);
		man.setUv(minU, minV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordBottomRight);

		man.addVertex();
	}

	@Override
	public void vertexFour(ModelBoundsData model, int color) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		man.setPos(model.minX, model.minY, model.minZ);
		man.setUv(maxU, minV);
		man.setColor(color);
		man.setLightMap(model.render.lightMapCoordTopRight);


		man.addVertex();
	}
}
