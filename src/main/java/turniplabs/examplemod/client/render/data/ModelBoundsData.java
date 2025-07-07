package turniplabs.examplemod.client.render.data;

import turniplabs.examplemod.client.render.meshing.BlockRenderer;

public class ModelBoundsData {
	public float x0, x1;
	public float y0, y1;
	public float z0, z1;

	public float minX, maxX;
	public float minY, maxY;
	public float minZ, maxZ;

	public BlockRenderer render;

	public void setRender(BlockRenderer renderer) {
		this.render = renderer;
	}

	public void setBoundsData(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		this.x0 = (float) minX;
		this.x1 = (float) maxX;

		this.y0 = (float) minY;
		this.y1 = (float) maxY;

		this.z0 = (float) minZ;
		this.z1 = (float) maxZ;
	}

	public void setBoundsDataExtra(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		this.minX = (float) minX;
		this.maxX = (float) maxX;

		this.minY = (float) minY;
		this.maxY = (float) maxY;

		this.minZ = (float) minZ;
		this.maxZ = (float) maxZ;
	}
}
