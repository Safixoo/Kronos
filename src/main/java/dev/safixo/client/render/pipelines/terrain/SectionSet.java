package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.render.pipelines.terrain.cull.BFSCuller;
import dev.safixo.client.render.pipelines.terrain.cull.BFSQueues;
import dev.safixo.client.util.ClientChunkListener;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.CameraData;

// Most of the useful section data saved in a couple of arrays, because of reasons (*performance*).
public class SectionSet {
	// Useful and fast system of indexing to save section data, it has some tricks
	// that make it more useful in heavy access scenarios, although is dependant of the
	// section position of the player making it easily invalidated to movement.
	private byte[] sections;

	// Temporary array to make the process of copying sections easier.
	byte[] tempArray;

	// Saves the visibility value from each section in the radius, which is quantized
	// as "grid factor" (the term used in BFSCuller).
	private short[] visibilitySet;

	private int lastCameraChunkX = Integer.MIN_VALUE, lastCameraChunkZ = Integer.MIN_VALUE;
	private int lastDistance = Integer.MIN_VALUE;

	private CameraData camera;
	private int radius;

	public void updateSet(WorldManager manager, CameraData camera, boolean worldChanged) {
		CameraData lastCamera = this.camera;
		this.camera = camera;
		this.radius = camera.renderDistance + (16 / camera.renderDistance) + 1;

		this.resetVisibilityState();

		int fogDistance = (int) (BFSCuller.getFogDistance(camera) * 16);

		if (fogDistance != this.lastDistance) {
			this.clampVisibilitySet(fogDistance / 16);
			this.lastDistance = fogDistance;
		}

		this.updateSectionArray(manager, lastCamera, worldChanged);
	}

	public short[] getVisibilitySet() {
		return this.visibilitySet;
	}

	public byte[] getSections() {
		return this.sections;
	}

	private void updateSectionArray(WorldManager manager, CameraData lastCamera, boolean worldChanged) {
		if (worldChanged || lastCamera == null || this.camera.renderDistance != lastCamera.renderDistance) {
			this.initializeSectionArray();
		} else {
			this.updateAllSections(manager);
		}
	}

	private void clampVisibilitySet(int fogDistance) {
		float squaredDistance = Math.max(MathExt.square(8.0F), MathExt.square((fogDistance + 8) / 16.0f));
		int radius = this.radius;

		final short[] visibilitySet = this.visibilitySet;

		int cameraChunkX = this.camera.intX >> 4;
		int cameraChunkZ = this.camera.intZ >> 4;

		for (int x = 0; x <= radius; x++) {
			for (int z = 0; z <= radius; z++) {
				short overDistance = (short) (MathExt.square(x) + MathExt.square(z) >= squaredDistance ? 1 : 0);

				for (int y = 0; y < 16; y++) {
					int sectIndexPP = this.getSectionIndex(x + cameraChunkX, y, z + cameraChunkZ);
					int sectIndexNP = this.getSectionIndex(-x + cameraChunkX, y, z + cameraChunkZ);
					int sectIndexNN = this.getSectionIndex(-x + cameraChunkX, y, -z + cameraChunkZ);
					int sectIndexPN = this.getSectionIndex(x + cameraChunkX, y, -z + cameraChunkZ);

					visibilitySet[sectIndexPP] = overDistance;
					visibilitySet[sectIndexNP] = overDistance;
					visibilitySet[sectIndexNN] = overDistance;
					visibilitySet[sectIndexPN] = overDistance;
				}
			}
		}
	}

	private void initializeSectionArray() {
		int size = MathExt.square(this.radius * 2 + 1) * 16;

		if (this.sections == null || this.sections.length != size) {
			this.sections = new byte[size];
			this.tempArray = new byte[size];
		}

		int cameraChunkX = this.camera.intX >> 4;
		int cameraChunkZ = this.camera.intZ >> 4;

		int radius = this.radius;

		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				int chunkX = cameraChunkX + x;
				int chunkZ = cameraChunkZ + z;

				this.initializeSectionInfo(chunkX, chunkZ);
			}
		}
	}

	private void initializeSectionInfo(int chunkX, int chunkZ) {
		ClientChunkListener map = ClientChunkListener.getChunkListener();

		byte flag = (byte) (!map.canLoadChunk(chunkX, chunkZ) ? SectionFlags.SECTION_INVALID : SectionFlags.SECTION_DEFAULT_DIRTY);

		for (int sectionY = 0; sectionY < 16; sectionY++) {
			this.sections[this.getSectionIndex(chunkX, sectionY, chunkZ)] = flag;
		}
	}

	public void setSectionInfo(int sectionX, int sectionY, int sectionZ, int flag) {
		if (!this.isInBounds(sectionX, sectionZ)) {
			return;
		}

		this.sections[this.getSectionIndex(sectionX, sectionY, sectionZ)] = (byte) flag;
	}

	private boolean isInBounds(int sectionX, int sectionZ) {
		if (this.camera == null) {
			return false;
		}

		int diffChunkX = sectionX - (this.camera.intX >> 4);
		int diffChunkZ = sectionZ - (this.camera.intZ >> 4);

		return Math.abs(diffChunkX) <= this.radius && Math.abs(diffChunkZ) <= this.radius;
	}

	public int getSection(int sectionX, int sectionY, int sectionZ) {
		if (!this.isInBounds(sectionX, sectionZ)) {
			return SectionFlags.SECTION_INVALID;
		}

		return MathExt.byteToUnsigned(this.sections[this.getSectionIndex(sectionX, sectionY, sectionZ)]);
	}

	public SectionRender getSectionInstance(WorldManager manager, int sectionX, int sectionY, int sectionZ) {
		return new SectionRender(manager, this, sectionX << 4, sectionY << 4, sectionZ << 4, this.getSection(sectionX, sectionY, sectionZ));
	}

	public void markDirty(int sectionX, int sectionY, int sectionZ) {
		if (!ClientChunkListener.getChunkListener().canLoadChunk(sectionX, sectionZ) || !this.isInBounds(sectionX, sectionZ)) {
			return;
		}

		this.sections[this.getSectionIndex(sectionX, sectionY, sectionZ)] |= (byte) SectionFlags.DIRTY_FLAG;
	}

	private void resetVisibilityState() {
		int size = MathExt.square(this.radius * 2 + 1) * 16;

		if (this.visibilitySet == null || this.visibilitySet.length != size) {
			this.visibilitySet = new short[size];
			return;
		}

		final short[] visSet = this.visibilitySet;
		final int[] graphIndices = BFSQueues.GRAPH_INDICES;

		int maxIndex = BFSQueues.bfsIndex;
		int i = 0;

		while (i < (maxIndex & -4)) {
			int i0 = graphIndices[i++];
			int i1 = graphIndices[i++];
			int i2 = graphIndices[i++];
			int i3 = graphIndices[i++];

			visSet[i0] = 0;
			visSet[i1] = 0;
			visSet[i2] = 0;
			visSet[i3] = 0;
		}

		for (int j = maxIndex & -4; j < maxIndex; j++) {
			visSet[graphIndices[j]] = 0;
		}
	}

	// [X, Y, Z]
	public int getSectionIndex(int sectionX, int sectionY, int sectionZ) {
		int offsetX = sectionX - (this.camera.intX >> 4);
		int offsetZ = sectionZ - (this.camera.intZ >> 4);

		return this.getSectionIndexRelative(offsetX, sectionY, offsetZ);
	}

	private int getSectionIndexRelative(int offsetX, int sectionY, int offsetZ) {
		offsetX += this.radius;
		offsetZ += this.radius;

		int diameter = (this.radius * 2 + 1);
		return offsetX + (sectionY + (offsetZ << 4)) * diameter;
	}

	private void updateAllSections(WorldManager manager) {
		int size = MathExt.square(this.radius * 2 + 1) * 16;

		if (this.sections == null || this.sections.length != size) {
			this.initializeSectionArray();
			return;
		}

		if (this.lastCameraChunkX == Integer.MIN_VALUE || this.lastCameraChunkZ == Integer.MIN_VALUE) {
			this.lastCameraChunkX = this.camera.intX >> 4;
			this.lastCameraChunkZ = this.camera.intZ >> 4;
			return;
		}

		int radius = this.radius;

		int lastCameraChunkX = this.lastCameraChunkX;
		int lastCameraChunkZ = this.lastCameraChunkZ;

		int cameraChunkX = this.camera.intX >> 4;
		int cameraChunkZ = this.camera.intZ >> 4;

		int diffCameraX = cameraChunkX - lastCameraChunkX;
		int diffCameraZ = cameraChunkZ - lastCameraChunkZ;

		// No section movement since last frame.
		if (diffCameraX == 0 && diffCameraZ == 0) {
			return;
		}

		// Iterates the whole array volume.
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				int chunkX = cameraChunkX + x;
				int chunkZ = cameraChunkZ + z;

				// Manage the part of the volume that the array NOW represents as *inside*.
				if (Math.abs(x + diffCameraX) > radius || Math.abs(z + diffCameraZ) > radius) {
					this.initializeSectionInfo(chunkX, chunkZ);
					continue;
				}

				// Manage the part of the volume that the array NOW represents as *outside*.
				if (Math.abs(x - diffCameraX) > radius || Math.abs(z - diffCameraZ) > radius) {
					// The outside part of the volume can't be represented.
					continue;
				}

				// Copy part of the volume that now its inside and before it was also.
				for (int sectionY = 0; sectionY < 16; sectionY++) {
					int lastSectionIndex = this.getSectionIndexRelative(x + diffCameraX, sectionY, z + diffCameraZ);
					int sectionIndex = this.getSectionIndexRelative(x, sectionY, z);

					this.tempArray[sectionIndex] = this.sections[lastSectionIndex];
				}
			}
		}

		// Swap arrays.
		byte[] newSection = this.tempArray;
		this.tempArray = this.sections;
		this.sections = newSection;

		this.lastCameraChunkX = cameraChunkX;
		this.lastCameraChunkZ = cameraChunkZ;
	}

	public int getRadius() {
		return this.radius;
	}
}
