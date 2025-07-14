package turniplabs.examplemod.client.render.data;

public class CameraData {
	public int cameraX;
	public int cameraY;
	public int cameraZ;

	public float fractX;
	public float fractY;
	public float fractZ;

	public int renderDistance;

	public CameraData(float fractX, float fractY, float fractZ, int cameraX, int cameraY, int cameraZ, int renderDistance) {
		this.fractX = fractX;
		this.fractY = fractY;
		this.fractZ = fractZ;

		this.cameraX = cameraX;
		this.cameraY = cameraY;
		this.cameraZ = cameraZ;

		this.renderDistance = renderDistance;
	}

	public float cameraX() {
		return this.cameraX + this.fractX;
	}

	public float cameraY() {
		return this.cameraY + this.fractY;
	}

	public float cameraZ() {
		return this.cameraZ + this.fractZ;
	}
}
