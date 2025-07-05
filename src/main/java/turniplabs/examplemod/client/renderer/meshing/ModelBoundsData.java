package turniplabs.examplemod.client.renderer.meshing;

import turniplabs.examplemod.client.renderer.BlockRenderer;

public class ModelBoundsData {
	public float minX, maxX;
	public float minY, maxY;
	public float minZ, maxZ;

	public float minXB, maxXB;
	public float minYB, maxYB;
	public float minZB, maxZB;

	public BlockRenderer render;

	public ModelBoundsData() {
	}

	public ModelBoundsData(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
		this.minX = minX;
		this.maxX = maxX;

		this.minY = minY;
		this.maxY = maxY;

		this.minZ = minZ;
		this.maxZ = maxZ;
	}

	public ModelBoundsData(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
		this.minX = (float) minX;
		this.maxX = (float) maxX;

		this.minY = (float) minY;
		this.maxY = (float) maxY;

		this.minZ = (float) minZ;
		this.maxZ = (float) maxZ;
	}

	public void setRender(BlockRenderer renderer) {
		this.render = renderer;
	}

	public void setBoundsData(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		this.minX = (float) minX;
		this.maxX = (float) maxX;

		this.minY = (float) minY;
		this.maxY = (float) maxY;

		this.minZ = (float) minZ;
		this.maxZ = (float) maxZ;
	}

	public void setBoundsDataExtra(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		this.minXB = (float) minX;
		this.maxXB = (float) maxX;

		this.minYB = (float) minY;
		this.maxYB = (float) maxY;

		this.minZB = (float) minZ;
		this.maxZB = (float) maxZ;
	}
}
