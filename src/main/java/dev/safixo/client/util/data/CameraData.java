package dev.safixo.client.util.data;

import net.minecraft.client.Minecraft;

public class CameraData {
	public final int intX;
	public final int intY;
	public final int intZ;

	public final float fractX;
	public final float fractY;
	public final float fractZ;

	public final int renderDistance;
	private final float yaw, pitch, fov;

	public CameraData(float fractX, float fractY, float fractZ, int cameraX, int cameraY, int cameraZ, int renderDistance) {
		this.fractX = fractX;
		this.fractY = fractY;
		this.fractZ = fractZ;

		this.intX = cameraX;
		this.intY = cameraY;
		this.intZ = cameraZ;

		Minecraft minecraft = Minecraft.getMinecraft();

		float fov = minecraft.gameSettings.fovSetting;
		float yaw = minecraft.renderViewEntity.rotationYaw;
		float pitch = minecraft.renderViewEntity.rotationPitch;

		this.yaw = yaw;
		this.pitch = pitch;
		this.fov = fov;

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

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof CameraData)) {
			return false;
		}

		CameraData other = (CameraData) obj;

		if (other.cameraX() != this.cameraX() || other.cameraY() != this.cameraY() || other.cameraZ() != this.cameraZ()) {
			return false;
		}

		return other.fov == this.fov && other.renderDistance == this.renderDistance && other.pitch == this.pitch && other.yaw == this.yaw;
	}
}
