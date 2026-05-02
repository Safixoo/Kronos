package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.CameraData;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.Arrays;

public class SectionSet {
	private static final int FLAG_NULL = CompressedFlags.setTraversableFaces(0b0, 0b0);

	private final ReferenceOpenHashSet<SectionRender> queuedFlags = new ReferenceOpenHashSet<>();
	private SectionRender lastSection;

	public byte[] sectionFlags;
	public byte[] tempFlags;
	public short[] visibilitySet;

	private int lastCameraChunkX = Integer.MIN_VALUE, lastCameraChunkZ = Integer.MIN_VALUE;
	private CameraData camera;
	private int radius;

	public void updateSet(SectionManager manager, CameraData camera, boolean worldChanged) {
		CameraData lastCamera = this.camera;
		this.camera = camera;
		this.radius = camera.renderDistance + (16 / camera.renderDistance) + 1;

		this.resetVisibilityState();

		if (worldChanged || lastCamera == null || camera.renderDistance != lastCamera.renderDistance) {
			this.initializeFlagData(manager);
		} else {
			this.updateAllFlags(manager);
		}
	}

	private void initializeFlagData(SectionManager manager) {
		int size = MathExt.square(this.radius * 2 + 1) * 16;

		if (this.sectionFlags == null || this.sectionFlags.length != size) {
			this.sectionFlags = new byte[size];
		}

		Arrays.fill(this.sectionFlags, (byte) FLAG_NULL);
		CameraData camera = this.camera;
		int radius = this.radius;

		Long2ReferenceMap<SectionRender> sectionMap = manager.getSectionMap();

		for (SectionRender section : sectionMap.values()) {
			int diffChunkX = (section.blockX >> 4) - (camera.intX >> 4);
			int diffChunkZ = (section.blockZ >> 4) - (camera.intZ >> 4);

			if (Math.abs(diffChunkX) > radius || Math.abs(diffChunkZ) > radius) {
				continue;
			}

			int compressedFlags = CompressedFlags.sectionToCompressed(section.flags);
			this.setFlag(diffChunkX + radius, section.blockY >> 4, diffChunkZ + radius, compressedFlags);
		}

		this.queuedFlags.clear();
	}

	@SuppressWarnings("MismatchedReadAndWriteOfArray")
	private static final short[] EMPTY_ARRAY = new short[8192];

	private void resetVisibilityState() {
		int size = MathExt.square(this.radius * 2 + 1) * 16;

		if (this.visibilitySet == null || this.visibilitySet.length != size) {
			this.visibilitySet = new short[size];
		}

		short[] array = this.visibilitySet;
		int length = array.length;

		System.arraycopy(EMPTY_ARRAY, 0, array, 0, Math.min(8192, length));

		for (int i = 8192; i < length; i += i) {
			System.arraycopy(array, 0, array, i, Math.min(i, length - i));
		}
	}

	public static int getFlagIndex(int offsetX, int sectionY, int offsetZ, int radius) {
		int rd = (radius * 2 + 1);
		return offsetX + (sectionY * rd) + (offsetZ * rd * 16);
	}

	public void queueFlagChange(SectionRender section) {
		if (this.lastSection == section) {
			return;
		}

		this.queuedFlags.add(section);
		this.lastSection = section;
	}

	private void updateAllFlags(SectionManager manager) {
		int size = MathExt.square(this.radius * 2 + 1) * 16;

		if (this.sectionFlags == null || this.sectionFlags.length != size) {
			this.initializeFlagData(manager);
			return;
		}

		if (this.tempFlags == null || this.tempFlags.length != size) {
			this.tempFlags = new byte[size];
		}

		if (this.lastCameraChunkX == Integer.MIN_VALUE || this.lastCameraChunkZ == Integer.MIN_VALUE) {
			this.lastCameraChunkX = camera.intX >> 4;
			this.lastCameraChunkZ = camera.intZ >> 4;
			return;
		}

		int radius = this.radius;

		int lastCameraChunkX = this.lastCameraChunkX;
		int lastCameraChunkZ = this.lastCameraChunkZ;

		int cameraChunkX = this.camera.intX >> 4;
		int cameraChunkZ = this.camera.intZ >> 4;

		int diffCameraX = cameraChunkX - lastCameraChunkX;
		int diffCameraZ = cameraChunkZ - lastCameraChunkZ;

		if (diffCameraX == 0 && diffCameraZ == 0) {
			this.processQueuedFlagChanges();
			return;
		}

		// 1D : X
		// 2D : Y
		// 3D : Z
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				int chunkX = x + cameraChunkX;
				int chunkZ = z + cameraChunkZ;

				if (!(Math.abs(chunkX - lastCameraChunkX) <= radius && Math.abs(chunkZ - lastCameraChunkZ) <= radius)) {
					continue;
				}

				for (int y = 0; y < 16; y++) {
					int lastFlagIndex = getFlagIndex(x + diffCameraX + radius, y, z + diffCameraZ + radius, radius);
					int flagIndex = getFlagIndex(x + radius, y, z + radius, radius);

					this.tempFlags[flagIndex] = this.sectionFlags[lastFlagIndex];
				}
			}
		}

		// swap arrays.
		byte[] newFlags = this.tempFlags;
		this.tempFlags = this.sectionFlags;
		this.sectionFlags = newFlags;

		this.processQueuedFlagChanges();

		this.lastCameraChunkX = cameraChunkX;
		this.lastCameraChunkZ = cameraChunkZ;
	}

	private void processQueuedFlagChanges() {
		int cameraChunkX = this.camera.intX >> 4;
		int cameraChunkZ = this.camera.intZ >> 4;
		int radius = this.radius;

		for (SectionRender section : this.queuedFlags) {
			int flags = CompressedFlags.sectionToCompressed(section.flags);

			int sectionX = section.blockX >> 4;
			int sectionY = section.blockY >> 4;
			int sectionZ = section.blockZ >> 4;

			int diffChunkX = sectionX - cameraChunkX;
			int diffChunkZ = sectionZ - cameraChunkZ;

			if (Math.abs(diffChunkX) > radius || Math.abs(diffChunkZ) > radius) {
				continue;
			}

			this.setFlag(diffChunkX + radius, sectionY, diffChunkZ + radius, flags);
		}

		this.queuedFlags.clear();
		this.lastSection = null;
	}

	public int getRadius() {
		return this.radius;
	}

	private void setFlag(int offsetX, int sectionY, int offsetZ, int flag) {
		if (this.sectionFlags == null) {
			return;
		}

		int flagIndex = getFlagIndex(offsetX, sectionY, offsetZ, this.radius);
		this.sectionFlags[flagIndex] = (byte) flag;
	}
}
