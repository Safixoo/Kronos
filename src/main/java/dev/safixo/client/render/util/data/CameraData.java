package dev.safixo.client.render.util.data;

public class CameraData {
	public int intX;
	public int intY;
	public int intZ;

	public float fractX;
	public float fractY;
	public float fractZ;

	public int renderDistance;

	public CameraData(float fractX, float fractY, float fractZ, int cameraX, int cameraY, int cameraZ, int renderDistance) {
		this.fractX = fractX;
		this.fractY = fractY;
		this.fractZ = fractZ;

		this.intX = cameraX;
		this.intY = cameraY;
		this.intZ = cameraZ;

		this.renderDistance = renderDistance;
	}

	public float cameraX() {
		return this.intX + this.fractX;
	}

	public float cameraY() {
		return this.intY + this.fractY;
	}

	public float cameraZ() {
		return this.intZ + this.fractZ;
	}

	public double cameraXD() {
		return this.intX + (double) this.fractX;
	}

	public double cameraYD() {
		return this.intY + (double) this.fractY;
	}

	public double cameraZD() {
		return this.intZ + (double) this.fractZ;
	}
}
