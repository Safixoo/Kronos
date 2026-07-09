package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.render.pipelines.terrain.cull.BFSCuller;
import dev.safixo.client.render.pipelines.terrain.cull.BFSQueue;
import dev.safixo.client.util.ClientChunkListener;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.CameraData;
import it.unimi.dsi.fastutil.longs.*;

import java.util.Arrays;

// Most of the useful section data saved in a couple of arrays, because of reasons (*performance*).
// TODO: Remove byte[] sections and use only byte[] fastSections instead, much simpler and performant.
public class SectionSet {
	// All the changes are queued until the camera and context is updated.
	private final LongSet updatesQueued = new LongOpenHashSet();

	// Saves the section data with a slower indexing, is useful for storage but
	// for heavy accesses it can be we worse.
	private byte[] sections;

	// Uses a more useful and fast system of indexing to save section data, it has some tricks
	// that make it more useful in heavy access scenarios, although it is slow to update
	// as it is easily invalidated with movement.
	public byte[] fastSections;

	// Saves the visibility value from each section in the radius, which is quantized
	// as "grid factor" (the term used in BFSCuller).
	public short[] visibilitySet;

	private int lastCameraChunkX = Integer.MIN_VALUE, lastCameraChunkZ = Integer.MIN_VALUE;
	private int lastDistance = Integer.MIN_VALUE;

	private CameraData camera;
	private int radius;

	public void updateSet(WorldManager manager, CameraData camera, boolean worldChanged) {
		CameraData lastCamera = this.camera;
		this.camera = camera;
		this.radius = camera.renderDistance + (16 / camera.renderDistance) + 1;

		this.updateSectionArray(lastCamera, worldChanged);
		this.resetVisibilityState();

		int fogDistance = (int) (BFSCuller.getFogDistance(camera) * 16);

		if (fogDistance != this.lastDistance) {
			this.clampVisibilitySet(fogDistance / 16);
			this.lastDistance = fogDistance;
		}

		this.updateSecondaryArray(manager, lastCamera, worldChanged);
	}

	public short[] getVisibilitySet() {
		return this.visibilitySet;
	}

	public byte[] getFastSectionsSet() {
		return this.fastSections;
	}

	private byte[] getSectionSet() {
		return this.sections;
	}

	private void updateSecondaryArray(WorldManager manager, CameraData lastCamera, boolean worldChanged) {
		if (worldChanged || lastCamera == null || this.camera.renderDistance != lastCamera.renderDistance) {
			this.initializeFlagData();
		} else {
			this.updateAllFlags(manager);
		}
	}

	private void clampVisibilitySet(int fogDistance) {
		float squaredDistance = Math.max(MathExt.square(8.0F), MathExt.square((fogDistance + 8) / 16.0f));
		int radius = this.radius;

		final short[] visibilitySet = this.visibilitySet;

		for (int x = 0; x <= radius; x++) {
			for (int z = 0; z <= radius; z++) {
				short overDistance = (short) (MathExt.square(x) + MathExt.square(z) >= squaredDistance ? 1 : 0);

				for (int y = 0; y < 16; y++) {
					int flagIndexPP = this.getFlagIndex(x + radius, y, z + radius);
					int flagIndexNP = this.getFlagIndex(-x + radius, y, z + radius);
					int flagIndexNN = this.getFlagIndex(-x + radius, y, -z + radius);
					int flagIndexPN = this.getFlagIndex(x + radius, y, -z + radius);

					visibilitySet[flagIndexPP] = overDistance;
					visibilitySet[flagIndexNP] = overDistance;
					visibilitySet[flagIndexNN] = overDistance;
					visibilitySet[flagIndexPN] = overDistance;
				}
			}
		}
	}

	private void initializeFlagData() {
		int size = MathExt.square(this.radius * 2 + 1) * 16;

		if (this.fastSections == null || this.fastSections.length != size) {
			this.fastSections = new byte[size];
		}

		Arrays.fill(this.fastSections, (byte) SectionFlags.SECTION_INVALID);

		CameraData camera = this.camera;
		int radius = this.radius;

		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				for (int sectionY = 0; sectionY < 16; sectionY++) {
					int sectionX = (camera.intX >> 4) + x;
					int sectionZ = (camera.intZ >> 4) + z;

					int section = this.getSection(sectionX, sectionY, sectionZ);
					this.setFlag(x + radius, sectionY, z + radius, section);
				}
			}
		}
	}

	public void notifyFlagChange(int sectionX, int sectionY, int sectionZ) {
		this.updatesQueued.add(MathExt.asLong(sectionX, sectionY, sectionZ));
	}

	public void updateSectionData(int sectionX, int sectionY, int sectionZ, int flag) {
		if (this.isInBounds(sectionX, sectionZ)) {
			this.sections[this.getSectionIndex(sectionX, sectionY, sectionZ)] = (byte) flag;
		}

		this.notifyFlagChange(sectionX, sectionY, sectionZ);
	}

	public void updateSectionArray(CameraData lastCamera, boolean worldChanged) {
		int diameter = this.radius * 2 + 1;
		int arrayLength = MathExt.square(diameter) * 16;

		boolean arrayCreated = false;

		if (this.sections == null || this.sections.length != arrayLength) {
			this.sections = new byte[arrayLength];
			Arrays.fill(this.sections, (byte) SectionFlags.SECTION_INVALID);

			arrayCreated = true;
		}

		this.calculateTranslatedSections(lastCamera);

		if (worldChanged || arrayCreated) {
			this.markAllVolumeDirty();
		}
	}

	private void markAllVolumeDirty() {
		int cameraChunkX = this.camera.intX >> 4;
		int cameraChunkZ = this.camera.intZ >> 4;

		int radius = this.radius;

		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				for (int sectionY = 0; sectionY < 16; sectionY++) {
					this.markDirty(cameraChunkX + x, sectionY, cameraChunkZ + z);
				}
			}
		}
	}

	private void calculateTranslatedSections(CameraData lastCamera) {
		ClientChunkListener map = ClientChunkListener.getChunkListener();

		CameraData camera = this.camera;
		int radius = this.radius;

		int lastCameraChunkX = lastCamera == null ? Integer.MIN_VALUE : lastCamera.intX >> 4;
		int lastCameraChunkZ = lastCamera == null ? Integer.MIN_VALUE : lastCamera.intZ >> 4;

		int currentCameraChunkX = camera.intX >> 4;
		int currentCameraChunkZ = camera.intZ >> 4;

		int diffCameraChunkX = currentCameraChunkX - lastCameraChunkX;
		int diffCameraChunkZ = currentCameraChunkZ - lastCameraChunkZ;

		for (int offsetX = -radius; offsetX <= radius; offsetX++) {
			for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
				if (Math.abs(offsetX + diffCameraChunkX) <= radius && Math.abs(offsetZ + diffCameraChunkZ) <= radius) {
					continue;
				}

				int chunkX = offsetX + currentCameraChunkX;
				int chunkZ = offsetZ + currentCameraChunkZ;

				boolean canLoad = map.canLoadChunk(chunkX, chunkZ);

				for (int sectionY = 0; sectionY < 16; sectionY++) {
					int sectionIndex = this.getSectionIndex(chunkX, sectionY, chunkZ);

					this.sections[sectionIndex] = (byte) (canLoad ? SectionFlags.SECTION_DEFAULT_DIRTY : SectionFlags.SECTION_INVALID);
					this.notifyFlagChange(chunkX, sectionY, chunkZ);
				}
			}
		}
	}

	public static int positiveModulo(int a, int b) {
		int r = a % b;
		return r + (r >> 31 & b);
	}

	private int getSectionIndex(int x, int y, int z) {
		int diameter = this.radius * 2 + 1;

		int xi = positiveModulo(x, diameter);
		int zi = positiveModulo(z, diameter);

		return y | ((xi + zi * diameter) << 4);
	}

	private int getSectionIndexXZ(int x, int z) {
		int diameter = this.radius * 2 + 1;

		int xi = positiveModulo(x, diameter);
		int zi = positiveModulo(z, diameter);

		return (xi + zi * diameter) << 4;
	}

	public int getSectionX(int sectionIndex) {
		int diameter = this.radius * 2 + 1;
		return positiveModulo(MathExt.floorDiv(sectionIndex >> 4, diameter), diameter);
	}

	public int getSectionY(int sectionIndex) {
		return sectionIndex & 0xF;
	}

	public int getSectionZ(int sectionIndex) {
		int diameter = this.radius * 2 + 1;
		return positiveModulo(MathExt.floorDiv(sectionIndex >> 4, diameter * diameter), diameter);
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
		if (!ClientChunkListener.getChunkListener().canLoadChunk(sectionX, sectionZ)) {
			return;
		}

		if (this.isInBounds(sectionX, sectionZ)) {
			this.sections[this.getSectionIndex(sectionX, sectionY, sectionZ)] |= (byte) SectionFlags.DIRTY_FLAG;
		}

		this.notifyFlagChange(sectionX, sectionY, sectionZ);
	}

	private void resetVisibilityState() {
		int size = MathExt.square(this.radius * 2 + 1) * 16;

		if (this.visibilitySet == null || this.visibilitySet.length != size) {
			this.visibilitySet = new short[size];
			return;
		}

		final short[] visSet = this.visibilitySet;
		final int[] graphIndices = BFSQueue.GRAPH_INDICES;

		int maxIndex = BFSQueue.bfsIndex;
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

	// [X][Y][Z]
	public int getFlagIndex(int offsetX, int sectionY, int offsetZ) {
		int diameter = (this.radius * 2 + 1);
		return offsetX + (sectionY + (offsetZ << 4)) * diameter;
	}

	private void updateAllFlags(WorldManager manager) {
		int size = MathExt.square(this.radius * 2 + 1) * 16;

		if (this.fastSections == null || this.fastSections.length != size) {
			this.initializeFlagData();
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

		if (diffCameraX == 0 && diffCameraZ == 0) {
			this.processQueuedFlagChanges();
			return;
		}

		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				int chunkX = x + cameraChunkX;
				int chunkZ = z + cameraChunkZ;

				if (Math.abs(chunkX - lastCameraChunkX) > radius || Math.abs(chunkZ - lastCameraChunkZ) > radius) {
					continue;
				}

				int lastFlagIndex = this.getSectionIndexXZ(x + cameraChunkX, z + cameraChunkZ);

				for (int sectionY = 0; sectionY < 16; sectionY++) {
					int flagIndex = this.getFlagIndex(x + radius, sectionY, z + radius);
					this.fastSections[flagIndex] = this.sections[lastFlagIndex | sectionY];
				}
			}
		}

		this.processQueuedFlagChanges();

		this.lastCameraChunkX = cameraChunkX;
		this.lastCameraChunkZ = cameraChunkZ;
	}

	private void processQueuedFlagChanges() {
		int cameraChunkX = this.camera.intX >> 4;
		int cameraChunkZ = this.camera.intZ >> 4;
		int radius = this.radius;

		for (long position : this.updatesQueued) {
			int sectionX = MathExt.decodeX(position);
			int sectionY = MathExt.decodeY(position);
			int sectionZ = MathExt.decodeZ(position);

			if (!this.isInBounds(sectionX, sectionZ)) {
				continue;
			}

			int diffChunkX = sectionX - cameraChunkX;
			int diffChunkZ = sectionZ - cameraChunkZ;

			this.setFlag(diffChunkX + radius, sectionY, diffChunkZ + radius, this.getSection(sectionX, sectionY, sectionZ));
		}

		this.updatesQueued.clear();
	}

	public int getRadius() {
		return this.radius;
	}

	private void setFlag(int offsetX, int sectionY, int offsetZ, int flag) {
		if (this.fastSections == null) {
			return;
		}

		int flagIndex = this.getFlagIndex(offsetX, sectionY, offsetZ);
		this.fastSections[flagIndex] = (byte) flag;
	}
}
