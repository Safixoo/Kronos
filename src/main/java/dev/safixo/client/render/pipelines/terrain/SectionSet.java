package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.render.pipelines.terrain.cull.GraphCuller;
import dev.safixo.client.render.pipelines.terrain.cull.CullerQueue;
import dev.safixo.client.util.data.ClientChunkListener;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.CameraData;

// Most of the useful section data saved in a couple of arrays, because of reasons (*performance*).
public class SectionSet {
	// Useful and fast system of indexing to save section data, it has some tricks
	// that make it more useful in heavy access scenarios, although is dependant of the
	// section position of the player making it easily invalidated to movement.
	private byte[] sections;

	// Saves the visibility value from each section in the radius, which is quantized
	// as "grid factor" (the term used in BFSCuller).
	private short[] visibilitySet;

	private int lastSectionX = Integer.MIN_VALUE, lastSectionZ = Integer.MIN_VALUE;

	private CameraData camera;
	private int radius;

	public void updateSet(WorldManager manager, CameraData camera, boolean worldChanged) {
		CameraData lastCamera = this.camera;
		this.camera = camera;
		this.radius = camera.renderDistance + (16 / camera.renderDistance) + 1;

		if (this.lastSectionX == Integer.MIN_VALUE || this.lastSectionZ == Integer.MIN_VALUE) {
			this.lastSectionX = this.camera.intX >> 4;
			this.lastSectionZ = this.camera.intZ >> 4;
		}

		this.resetVisibilityState(manager.getGraphCuller().getCullerQueue());
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

	private void clampVisibilitySet() {
		int radius = this.radius;

		final short[] visibilitySet = this.visibilitySet;

		int cameraChunkX = this.camera.intX >> 4;
		int cameraChunkZ = this.camera.intZ >> 4;

		for (int x = 0; x <= radius; x++) {
			for (int z = 0; z <= radius; z++) {
				short overDistance = (short) (Math.max(x, z) >= radius ? GraphCuller.TOLERANCE : 0);

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
		}

		int cameraSectionX = this.camera.intX >> 4;
		int cameraSectionZ = this.camera.intZ >> 4;

		this.lastSectionX = cameraSectionX;
		this.lastSectionZ = cameraSectionZ;

		int radius = this.radius;
		ClientChunkListener map = ClientChunkListener.getChunkListener();

		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				int chunkX = cameraSectionX + x;
				int chunkZ = cameraSectionZ + z;

				this.initializeChunkInfo(chunkX, chunkZ, map.canLoadChunk(chunkX, chunkZ));
			}
		}
	}

	private void initializeChunkInfo(int sectionX, int sectionZ, boolean canLoad) {
		if (!this.isInBounds(sectionX, sectionZ)) {
			return;
		}

		byte flag = (byte) (canLoad ? SectionFlags.SECTION_DEFAULT_DIRTY : SectionFlags.SECTION_INVALID);

		for (int sectionY = 0; sectionY < 16; sectionY++) {
			int sectionIndex = this.getSectionIndex(sectionX, sectionY, sectionZ);
			this.sections[sectionIndex] = flag;
		}
	}

	public void setSectionInfo(int sectionX, int sectionY, int sectionZ, int flag) {
		if (!this.isInBounds(sectionX, sectionZ)) {
			return;
		}

		int sectionIndex = this.getSectionIndex(sectionX, sectionY, sectionZ);
		this.sections[sectionIndex] = (byte) flag;
	}

	private int shapeCullData(int diffX, int diffY, int diffZ, int cullData) {
		return ~cullData & GraphCuller.getOutwardDirections(diffX, diffY, diffZ);
	}

	public boolean isInBounds(int sectionX, int sectionZ) {
		if (this.camera == null) {
			return false;
		}

		int diffChunkX = sectionX - this.lastSectionX;
		int diffChunkZ = sectionZ - this.lastSectionZ;

		return Math.abs(diffChunkX) <= this.radius && Math.abs(diffChunkZ) <= this.radius;
	}

	public int getSection(int sectionX, int sectionY, int sectionZ) {
		if (!this.isInBounds(sectionX, sectionZ)) {
			return SectionFlags.SECTION_INVALID;
		}

		return MathExt.byteToUnsigned(this.sections[this.getSectionIndex(sectionX, sectionY, sectionZ)]);
	}

	public boolean isVisible(int sectionX, int sectionY, int sectionZ) {
		if (!this.isInBounds(sectionX, sectionZ)) {
			return false;
		}

		return this.visibilitySet[this.getSectionIndex(sectionX, sectionY, sectionZ)] > GraphCuller.TOLERANCE;
	}

	public SectionRender getSectionInstance(WorldManager manager, int sectionX, int sectionY, int sectionZ) {
		return new SectionRender(manager, this, sectionX << 4, sectionY << 4, sectionZ << 4, this.getSection(sectionX, sectionY, sectionZ));
	}

	public SectionRender getSectionInstance(WorldManager manager, int sectionIndex) {
		int diameter = this.radius * 2 + 1;

		int sectionY = sectionIndex / diameter;
		int offsetX = sectionIndex - (sectionY * diameter);
		int offsetZ = sectionY >> 4;

		sectionY &= 15;

		int sectionX = offsetX + this.lastSectionX - this.radius;
		int sectionZ = offsetZ + this.lastSectionZ - this.radius;

		return new SectionRender(manager, this, sectionX << 4, sectionY << 4, sectionZ << 4, this.getSection(sectionX, sectionY, sectionZ));
	}

	public void markDirty(int sectionX, int sectionY, int sectionZ) {
		if (!this.isInBounds(sectionX, sectionZ)) {
			return;
		}

		if (!ClientChunkListener.getChunkListener().canLoadChunk(sectionX, sectionZ)) {
			this.sections[this.getSectionIndex(sectionX, sectionY, sectionZ)] &= (byte) ~SectionFlags.DIRTY_FLAG;
		} else {
			this.sections[this.getSectionIndex(sectionX, sectionY, sectionZ)] |= (byte) SectionFlags.DIRTY_FLAG;
		}
	}

	public void loadChunk(int sectionX, int sectionZ) {
		if (!this.isInBounds(sectionX, sectionZ)) {
			return;
		}

		this.initializeChunkInfo(sectionX, sectionZ, true);
	}

	public void unloadChunk(int sectionX, int sectionZ) {
		if (!this.isInBounds(sectionX, sectionZ)) {
			return;
		}

		this.initializeChunkInfo(sectionX, sectionZ, false);
	}

	private void resetVisibilityState(CullerQueue cullerQueue) {
		int size = MathExt.square(this.radius * 2 + 1) * 16;

		if (this.visibilitySet == null || this.visibilitySet.length != size) {
			this.visibilitySet = new short[size];
			this.clampVisibilitySet();
			return;
		}

		final short[] visSet = this.visibilitySet;
		final int[] graphIndices = cullerQueue.getGraphQueue();

		int maxIndex = cullerQueue.getGraphIndex();
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
		int offsetX = sectionX - this.lastSectionX;
		int offsetZ = sectionZ - this.lastSectionZ;

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

		int radius = this.radius;

		int lastCameraSectionX = this.lastSectionX;
		int lastCameraSectionZ = this.lastSectionZ;

		int cameraSectionX = this.camera.intX >> 4;
		int cameraSectionY = this.camera.intY >> 4;
		int cameraSectionZ = this.camera.intZ >> 4;

		int diffCameraX = cameraSectionX - lastCameraSectionX;
		int diffCameraZ = cameraSectionZ - lastCameraSectionZ;

		// No section movement since last frame.
		if (diffCameraX == 0 && diffCameraZ == 0) {
			return;
		}

		manager.markDirty();

		int signX = MathExt.sign(diffCameraX);
		int signZ = MathExt.sign(diffCameraZ);

		// Iterates the whole array volume.
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				int sectionX = cameraSectionX + x;
				int sectionZ = cameraSectionZ + z;

				// It has to iterate from the same direction from diffXZ to avoid overwriting
				// the data we are copying in the process.
				int xi = signX * x;
				int zi = signZ * z;

				this.handleSectionInVolume(sectionX, sectionZ, diffCameraX, diffCameraZ, xi, zi);
			}
		}

		this.lastSectionX = cameraSectionX;
		this.lastSectionZ = cameraSectionZ;
	}

	private void handleSectionInVolume(int chunkX, int chunkZ, int diffCameraX, int diffCameraZ, int xi, int zi) {
		ClientChunkListener map = ClientChunkListener.getChunkListener();

		// Manage the part of the volume that the array NOW represents as *inside*.
		if (Math.abs(xi + diffCameraX) > this.radius || Math.abs(zi + diffCameraZ) > this.radius) {
			this.initializeChunkInfo(chunkX, chunkZ, map.canLoadChunk(chunkX, chunkZ));
			return;
		}

		// Skip the part of the volume that the array NOW represents as *outside*.
		if (Math.abs(xi - diffCameraX) > this.radius || Math.abs(zi - diffCameraZ) > this.radius) {
			return;
		}

		// Copy part of the volume that now its inside and before it was also.
		for (int sectionY = 0; sectionY < 16; sectionY++) {
			int lastSectionIndex = this.getSectionIndexRelative(xi + diffCameraX, sectionY, zi + diffCameraZ);
			int sectionIndex = this.getSectionIndexRelative(xi, sectionY, zi);

			this.sections[sectionIndex] = this.sections[lastSectionIndex];
		}
	}

	public int getRadius() {
		return this.radius;
	}
}
