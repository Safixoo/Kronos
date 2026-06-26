package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.render.pipelines.terrain.cull.BFSCuller;
import dev.safixo.client.render.pipelines.terrain.cull.BFSQueue;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.CameraData;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Arrays;

// Most of the useful section data saved in a couple of arrays, because of reasons (*performance*).
public class SectionSet {
	private static final int FLAG_NULL = CompressedFlags.setTraversableFaces(0b0, 0b111_111) | CompressedFlags.setDirty(0b0, true);
	private final LongOpenHashSet queuedChanges = new LongOpenHashSet();

	private SectionRender[] sections;

	// Saves a CompressedFlags bit-mask of each section in the radius.
	public byte[] sectionFlags;

	// Temporary array to be able to make changes in sectionFlags easier.
	public byte[] tempFlags;

	// Saves the visibility value from each section in the radius, which is quantized
	// as "grid factor" (the term used in BFSCuller).
	public short[] visibilitySet;

	private int lastCameraChunkX = Integer.MIN_VALUE, lastCameraChunkZ = Integer.MIN_VALUE;
	private int lastDistance = Integer.MIN_VALUE;

	private final LongSet dirtyPositions = new LongOpenHashSet();

	private CameraData camera;
	private int radius;

	public void updateSet(WorldManager manager, CameraData camera, boolean worldChanged) {
		CameraData lastCamera = this.camera;
		this.camera = camera;
		this.radius = camera.renderDistance + (16 / camera.renderDistance) + 1;

		this.updateSectionArray(lastCamera);
		this.resetVisibilityState();

		int fogDistance = (int) (BFSCuller.getFogDistance(camera) * 16);

		if (fogDistance != this.lastDistance) {
			this.clampVisibilitySet(fogDistance / 16);
			this.lastDistance = fogDistance;
		}

		if (worldChanged || lastCamera == null || camera.renderDistance != lastCamera.renderDistance) {
			this.initializeFlagData(manager);
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
					int flagIndexPP = getFlagIndex(x + radius, y, z + radius, radius);
					int flagIndexNP = getFlagIndex(-x + radius, y, z + radius, radius);
					int flagIndexNN = getFlagIndex(-x + radius, y, -z + radius, radius);
					int flagIndexPN = getFlagIndex(x + radius, y, -z + radius, radius);

					visibilitySet[flagIndexPP] = overDistance;
					visibilitySet[flagIndexNP] = overDistance;
					visibilitySet[flagIndexNN] = overDistance;
					visibilitySet[flagIndexPN] = overDistance;
				}
			}
		}
	}

	private void initializeFlagData(WorldManager manager) {
		int size = MathExt.square(this.radius * 2 + 1) * 16;

		if (this.sectionFlags == null || this.sectionFlags.length != size) {
			this.sectionFlags = new byte[size];
		}

		Arrays.fill(this.sectionFlags, (byte) FLAG_NULL);
		CameraData camera = this.camera;
		int radius = this.radius;

		for (SectionRender section : this.sections) {
			if (section == null) {
				continue;
			}

			int diffChunkX = (section.blockX >> 4) - (camera.intX >> 4);
			int diffChunkZ = (section.blockZ >> 4) - (camera.intZ >> 4);

			if (Math.abs(diffChunkX) > radius || Math.abs(diffChunkZ) > radius) {
				continue;
			}

			int compressedFlags = CompressedFlags.sectionToCompressed(section.flags);
			this.setFlag(diffChunkX + radius, section.blockY >> 4, diffChunkZ + radius, compressedFlags);
		}

		this.queuedChanges.clear();
	}

	public void updateSectionArray(CameraData lastCamera) {
		int diameter = this.radius * 2 + 1;
		int arrayLength = MathExt.square(diameter) * 16;

		boolean changed = false;

		if (this.sections == null || this.sections.length != arrayLength) {
			this.sections = new SectionRender[arrayLength];
			changed = true;
		}

		if (!changed) {
			CameraData camera = this.camera;
			int radius = this.radius;

			int lastCameraChunkX = lastCamera.intX >> 4;
			int lastCameraChunkZ = lastCamera.intZ >> 4;

			int currentCameraChunkX = camera.intX >> 4;
			int currentCameraChunkZ = camera.intZ >> 4;

			int diffCameraChunkX = currentCameraChunkX - lastCameraChunkX;
			int diffCameraChunkZ = currentCameraChunkZ - lastCameraChunkZ;

			for (int offsetX = -radius; offsetX <= radius; offsetX++) {
				for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
					if (Math.abs(offsetX + diffCameraChunkX) > radius || Math.abs(offsetZ + diffCameraChunkZ) > radius) {
						int chunkX = offsetX + currentCameraChunkX;
						int chunkZ = offsetZ + currentCameraChunkZ;

						for (int sectionY = 0; sectionY < 16; sectionY++) {
							int sectionIndex = this.getSectionIndex(chunkX, sectionY, chunkZ);
							SectionRender sectionRender = this.sections[sectionIndex];

							if (sectionRender != null) {
								sectionRender.clearAllocations();
								this.disconnectNeighbors(sectionRender);
							}

							sectionRender = this.sections[sectionIndex] = new SectionRender(this, chunkX << 4, sectionY << 4, chunkZ << 4);
							this.connectNeighbors(sectionRender);
						}
					}
				}
			}
		}

		for (long position : this.dirtyPositions) {
			int sectionX = MathExt.decodeX(position);
			int sectionY = MathExt.decodeY(position);
			int sectionZ = MathExt.decodeZ(position);

			if (!this.isInBounds(sectionX, sectionZ)) {
				continue;
			}

			int sectionIndex = this.getSectionIndex(sectionX, sectionY, sectionZ);
			SectionRender section = this.sections[sectionIndex];

			if (section == null) {
				section = this.sections[sectionIndex] = new SectionRender(this, sectionX << 4, sectionY << 4, sectionZ << 4);
				this.connectNeighbors(section);
			} else {
				if (section.blockX >> 4 == sectionX && section.blockZ >> 4 == sectionZ) {
					section.markDirty(true);
				} else {
					section.clearAllocations();
					this.disconnectNeighbors(section);

					this.sections[sectionIndex] = new SectionRender(this, sectionX << 4, sectionY << 4, sectionZ << 4);
					this.connectNeighbors(section);
				}
			}
		}

		this.dirtyPositions.clear();
	}

	private SectionRender getAdjacent(SectionRender section, int direction) {
		int sectionX = (section.blockX >> 4) + Direction.x(direction);
		int sectionY = (section.blockY >> 4) + Direction.y(direction);
		int sectionZ = (section.blockZ >> 4) + Direction.z(direction);

		return sectionY < 0 || sectionY >= 16 ? null : this.getSection(sectionX, sectionY, sectionZ);
	}

	public void connectNeighbors(SectionRender render) {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			SectionRender adjacent = this.getAdjacent(render, dir);

			if (adjacent != null) {
				adjacent.setAdjacentNeighbor(render, Direction.opposite(dir));
			}

			render.setAdjacentNeighbor(adjacent, dir);
		}
	}

	public void disconnectNeighbors(SectionRender render) {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			SectionRender adjacent = this.getAdjacent(render, dir);

			if (adjacent != null) {
				adjacent.setAdjacentNeighbor(null, Direction.opposite(dir));
			}

			render.setAdjacentNeighbor(null, dir);
		}
	}

	private int getSectionIndex(int x, int y, int z) {
		int diameter = this.radius * 2 + 1;

		int xi = positiveModulo(x, diameter);
		int zi = positiveModulo(z, diameter);

		return y + ((xi + zi * diameter) << 4);
	}

	public static int positiveModulo(int a, int b) {
		int r = a % b;
		return r + (r >> 31 & b);
	}

	private boolean isInBounds(int sectionX, int sectionZ) {
		int diffChunkX = sectionX - (this.camera.intX >> 4);
		int diffChunkZ = sectionZ - (this.camera.intZ >> 4);

		return Math.abs(diffChunkX) <= this.radius && Math.abs(diffChunkZ) <= this.radius;
	}

	public SectionRender getSection(int sectionX, int sectionY, int sectionZ) {
		if (!this.isInBounds(sectionX, sectionZ)) {
			return null;
		}

		SectionRender sectionRender = this.sections[this.getSectionIndex(sectionX, sectionY, sectionZ)];

		if (sectionRender != null && sectionRender.blockX >> 4 == sectionX && sectionRender.blockZ >> 4 == sectionZ) {
			return sectionRender;
		}

		return null;
	}

	public void markDirty(int sectionX, int sectionY, int sectionZ) {
		this.dirtyPositions.add(MathExt.asLong(sectionX, sectionY, sectionZ));
	}

	public void clearSectionSet() {
		if (this.sections == null) {
			return;
		}

		for (int i = 0; i < this.sections.length; i++) {
			if (this.sections[i] != null) {
				this.sections[i].clearAllocations();
			}
		}
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

		for (int i = 0; i < maxIndex >> 3; i++) {
			int j = i << 3;

			visSet[graphIndices[j + 0]] = 0;
			visSet[graphIndices[j + 1]] = 0;
			visSet[graphIndices[j + 2]] = 0;
			visSet[graphIndices[j + 3]] = 0;

			visSet[graphIndices[j + 4]] = 0;
			visSet[graphIndices[j + 5]] = 0;
			visSet[graphIndices[j + 6]] = 0;
			visSet[graphIndices[j + 7]] = 0;
		}

		for (int i = maxIndex & -8; i < maxIndex; i++) {
			visSet[graphIndices[i]] = 0;
		}
	}

	public static int getFlagIndex(int offsetX, int sectionY, int offsetZ, int radius) {
		int rd = (radius * 2 + 1);
		return offsetX + (sectionY * rd) + (offsetZ * rd * 16);
	}

	public void queueFlagChange(SectionRender section) {
		this.queuedChanges.add(section.globalPosition);
	}

	private void updateAllFlags(WorldManager manager) {
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

				for (int sectionY = 0; sectionY < 16; sectionY++) {
					int lastFlagIndex = getFlagIndex(x + diffCameraX + radius, sectionY, z + diffCameraZ + radius, radius);
					int flagIndex = getFlagIndex(x + radius, sectionY, z + radius, radius);

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

		for (long position : this.queuedChanges) {
			int sectionX = MathExt.decodeX(position);
			int sectionY = MathExt.decodeY(position);
			int sectionZ = MathExt.decodeZ(position);

			SectionRender section = this.getSection(sectionX, sectionY, sectionZ);

			if (section == null) {
				continue;
			}

			int flags = CompressedFlags.sectionToCompressed(section.flags);

			int diffChunkX = sectionX - cameraChunkX;
			int diffChunkZ = sectionZ - cameraChunkZ;

			if (Math.abs(diffChunkX) > radius || Math.abs(diffChunkZ) > radius) {
				continue;
			}

			this.setFlag(diffChunkX + radius, sectionY, diffChunkZ + radius, flags);
		}

		this.queuedChanges.clear();
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
