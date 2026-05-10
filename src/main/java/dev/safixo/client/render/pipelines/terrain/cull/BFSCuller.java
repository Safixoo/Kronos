package dev.safixo.client.render.pipelines.terrain.cull;

import dev.safixo.client.render.pipelines.terrain.*;
import dev.safixo.core.hooks.GlStateTracker;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.render.pipelines.terrain.region.RegionManager;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;

public class BFSCuller {
	private static final long[] INV_DIVS = new long[256];

	public static final int PRECISION_BITS = 25;
	public static final int MAX_PRECISION = 1 << PRECISION_BITS;

	public static final int MAX_GRID_FACTOR = 1 << 12;
	private static final int TOLERANCE = (int) (0.15f * MAX_GRID_FACTOR);

	static {
		for (int i = 0; i < 256; i++) {
			INV_DIVS[i] = (long) Math.ceil(MAX_PRECISION / (double) i);
		}
	}

	public void init(RegionManager regionManager) {
		for (RegionRender render : regionManager.regionMap.values()) {
			render.sectionsToRender = 0;
		}

		RebuildList.clear();
		BFSQueue.clear();
	}

	/**
	 * Gets the first sections of the search, prepares the graph-search max distance and queues the first couple
	 * of sections to start the search afterward.
	 */
	public void updateRenderList(SectionManager manager, CameraData camera) {
		int blockX = camera.intX;
		int blockY = MathExt.clamp(camera.intY, 0, 255);
		int blockZ = camera.intZ;

		SectionRender origin = manager.getSectionMap().get(MathExt.asLong(blockX >> 4, blockY >> 4, blockZ >> 4));
		SectionSet sectionSet = manager.getSectionSet();

		int radius = sectionSet.getRadius();
		int cameraIndex = SectionSet.getFlagIndex(radius, blockY >> 4, radius, sectionSet.getRadius());

		short[] visibilitySet = sectionSet.visibilitySet;

		if (origin != null) {
			int flags = origin.flags;

			int diameter = radius * 2 + 1;
			int directions = SectionFlags.getAdjacentMask(flags) & ~SectionFlags.getCullFaces(flags);
			traverseNeighbors(visibilitySet, cameraIndex, diameter, directions);

			if (SectionFlags.isDirty(flags)) {
				RebuildList.addToList(MathExt.asInt(0, blockY >> 4, 0));
			}

			queueRegionNode(origin, flags);
		}

		int maxDistSquared = (int) MathExt.square(getFogDistance(camera));
		iterateGraph(sectionSet, cameraIndex, camera.intX, camera.intY, camera.intZ, maxDistSquared);

		this.enqueueRegionData(camera);
	}

	public static float getFogDistance(CameraData camera) {
		return Math.max(3 << 4, Math.min(GlStateTracker.FOG_END, camera.renderDistance << 4));
	}

	/**
	 * Reads all the indices from the visible sections enqueued in the graph and write
	 * their relative region indices in the region to later be used for drawing.
	 */
	private void enqueueRegionData(CameraData camera) {
		RegionManager regionManager = SectionManager.getRegionManager();
		RegionRender[] regions = regionManager.getIndexedRegions(camera);

		int renderDiameter = Math.min(3 << 3, camera.renderDistance * 2 + 1);
		int regionCameraX = camera.intX >> RegionRender.BLOCK_SHIFT_X;
		int regionCameraZ = camera.intZ >> RegionRender.BLOCK_SHIFT_Z;

		int cameraChunkX = camera.intX >> 4;
		int cameraChunkZ = camera.intZ >> 4;

		for (int i = 0; i < BFSQueue.renderIndex; i++) {
			int position = BFSQueue.RENDER_INDICES[i];

			int sectionX = MathExt.decodeX(position) + cameraChunkX;
			int sectionY = MathExt.decodeY(position);
			int sectionZ = MathExt.decodeZ(position) + cameraChunkZ;

			int distRegionX = (sectionX >> (RegionRender.BLOCK_SHIFT_X - 4)) - regionCameraX;
			int distRegionY = (sectionY >> (RegionRender.BLOCK_SHIFT_Y - 4));
			int distRegionZ = (sectionZ >> (RegionRender.BLOCK_SHIFT_Z - 4)) - regionCameraZ;

			RegionRender region = RegionManager.getRegionFromIndexed(regions, renderDiameter, distRegionX, distRegionY, distRegionZ);

			if (region == null) {
				continue;
			}

			region.renderIndices[region.sectionsToRender++] = (short) RegionRender.regionIndex(sectionX, sectionY, sectionZ);
		}
	}

	/**
	 * Does a BFS traversal based off <a href="https://tomcc.github.io/2014/08/31/visibility-1.html">Advanced Cave Culling</a>
	 * by tomcc and the Sodium implementation, with many differences as it doesn't try to find connectivity 100% and uses some
	 *  different ideas to avoid section queueing during the search.
	 */
	private static void iterateGraph(SectionSet sectionSet, int cameraIndex, int playerX, int playerY, int playerZ, int maxDistSquared) {
		byte[] sectionFlags = sectionSet.sectionFlags;
		short[] visSet = sectionSet.visibilitySet;

		int radius = sectionSet.getRadius();
		int diameter = radius * 2 + 1;
		int readIndex = 0;

		while (readIndex < BFSQueue.bfsIndex) {
			int sectionIndex = BFSQueue.GRAPH_INDICES[readIndex++];
			int flags = MathExt.byteToUnsigned(sectionFlags[sectionIndex]);

			// kind of ugly indexing but it works fine.
			int offsetX = sectionIndex;

			int sectionY = offsetX / diameter;
			offsetX %= diameter;

			int offsetZ = sectionY >> 4;
			sectionY &= 15;

			int diffChunkX = offsetX - radius;
			int diffChunkY = sectionY - (playerY >> 4);
			int diffChunkZ = offsetZ - radius;

			int diffX = (diffChunkX << 4) - (playerX & 15);
			int diffY = (diffChunkY << 4) - (playerY & 15);
			int diffZ = (diffChunkZ << 4) - (playerZ & 15);

			int distance = getDistance(diffX, diffY, diffZ);
			int outwardDir;

			{ // Calculates visibility per section.
				if (distance >= maxDistSquared || !FrustumCuller.withinFrustumBounds(diffX, diffY, diffZ)) {
					continue;
				}

				if (diffChunkX != 0 && diffChunkY != 0 && diffChunkZ != 0 &&
					processGridFactor(visSet, sectionIndex, diameter, diffChunkX, diffChunkY, diffChunkZ) < TOLERANCE) {
					continue;
				}

				outwardDir = getOutwardDirections(diffChunkX, diffChunkY, diffChunkZ);

				if ((distance >= 70*70 && CompressedFlags.hasPassesNonEmpty(flags) &&
					rayNotVisible(sectionFlags, visSet, cameraIndex, sectionIndex, diameter, -diffX-8, -diffY-8, -diffZ-8, outwardDir))) {
					continue;
				}
			}

			queueRenderTasks(flags, diffChunkX, sectionY, diffChunkZ);
			int directions = outwardDir & CompressedFlags.getTraversableFaces(flags);

			if (directions != 0b0) {
				traverseNeighbors(visSet, sectionIndex, diameter, directions);
			}
		}
	}

	/**
	 * Based in the flag data and position relative to the camera, saves positions to be later
	 * retrieved and processed for region rendering and meshing.
	 */
	private static void queueRenderTasks(int flags, int distChunkX, int sectionY, int distChunkZ) {
		if (CompressedFlags.isDirty(flags)) {
			RebuildList.addToList(MathExt.asInt(distChunkX, sectionY, distChunkZ));
		}

		if (CompressedFlags.hasPassesNonEmpty(flags)) {
			BFSQueue.RENDER_INDICES[BFSQueue.renderIndex++] = MathExt.asInt(distChunkX, sectionY, distChunkZ);
		}
	}

	/**
	 * Generates a mask to discard invariants inward directions early in the search.
	 */
	private static int getOutwardDirections(int diffChunkX, int diffChunkY, int diffChunkZ) {
		int planes = 0;

		planes |= (diffChunkX >> 31) & Direction.EAST_BIT  | (-diffChunkX >> 31) & Direction.WEST_BIT;
		planes |= (diffChunkY >> 31) & Direction.UP_BIT    | (-diffChunkY >> 31) & Direction.DOWN_BIT;
		planes |= (diffChunkZ >> 31) & Direction.SOUTH_BIT | (-diffChunkZ >> 31) & Direction.NORTH_BIT;

		return planes ^ 0b111_111;
	}

	/**
	 * For non-empty renderable sections that has been visited by the graph, their indices are saved in their respective
	 * region to later be rendered in order.
	 */
	private static void queueRegionNode(SectionRender section, int flags) {
		if (SectionFlags.hasPassesNonEmpty(flags)) {
			RegionRender region = section.region;
			region.renderIndices[region.sectionsToRender++] = (short) section.regionIndex;
		}
	}

	/**
	 * Searches outwards from the player position for possible visitable sections, based of the occlusion from the section faces,
	 * skips visiting sections if they were already visited in the active frame.
	 */
	private static void traverseNeighbors(short[] visSet, int sectionIndex, int renderDiameter, int directions) {
		int offsetX = 1;
		int offsetY = renderDiameter;
		int offsetZ = renderDiameter << 4;

		int index = BFSQueue.bfsIndex;
		final int[] graphIndices = BFSQueue.GRAPH_INDICES;

		if (Direction.hasSet(directions, Direction.DOWN) && visSet[sectionIndex - offsetY] == 0) {
			graphIndices[index++] = sectionIndex - offsetY;
			visSet[sectionIndex - offsetY] = MAX_GRID_FACTOR;
		}

		if (Direction.hasSet(directions, Direction.UP) && visSet[sectionIndex + offsetY] == 0) {
			graphIndices[index++] = sectionIndex + offsetY;
			visSet[sectionIndex + offsetY] = MAX_GRID_FACTOR;
		}

		if (Direction.hasSet(directions, Direction.NORTH) && visSet[sectionIndex - offsetZ] == 0) {
			graphIndices[index++] = sectionIndex - offsetZ;
			visSet[sectionIndex - offsetZ] = MAX_GRID_FACTOR;
		}

		if (Direction.hasSet(directions, Direction.SOUTH) && visSet[sectionIndex + offsetZ] == 0) {
			graphIndices[index++] = sectionIndex + offsetZ;
			visSet[sectionIndex + offsetZ] = MAX_GRID_FACTOR;
		}

		if (Direction.hasSet(directions, Direction.WEST) && visSet[sectionIndex - offsetX] == 0) {
			graphIndices[index++] = sectionIndex - offsetX;
			visSet[sectionIndex - offsetX] = MAX_GRID_FACTOR;
		}

		if (Direction.hasSet(directions, Direction.EAST) && visSet[sectionIndex + offsetX] == 0) {
			graphIndices[index++] = sectionIndex + offsetX;
			visSet[sectionIndex + offsetX] = MAX_GRID_FACTOR;
		}

		BFSQueue.bfsIndex = index;
	}

	/**
	 * Uses the key idea from the article of <a href ="https://towardsdatascience.com/a-quick-and-clear-look-at-grid-based-visibility-bf63769fbc78">Grid Based Visibility</a>
	 * to determine the factor of grid visibility in 3D for the current visited section of the graph. It might not be perfectly
	 * optimized, but it helps a ton when there's a lot of occluders.
	 * @return Grid Visibility Factor
	 */
	private static short processGridFactor(short[] visSet, int sectionIndex, int renderDiameter, int diffX, int diffY, int diffZ) {
		int gradInd = 0;

		int signX = MathExt.sign(diffX);
		int signY = MathExt.mulSign(renderDiameter, diffY);
		int signZ = MathExt.mulSign(renderDiameter << 4, diffZ);

		diffX = Math.abs(diffX);
		diffY = Math.abs(diffY);
		diffZ = Math.abs(diffZ);

		gradInd += diffX * (visSet[sectionIndex - signX]);
		gradInd += diffY * (visSet[sectionIndex - signY]);
		gradInd += diffZ * (visSet[sectionIndex - signZ]);

		return visSet[sectionIndex] = ((short) ((gradInd * INV_DIVS[diffX + diffY + diffZ]) >> PRECISION_BITS));
	}

	/**
	 * Squared Euclidean distance to the nearest section corner, which should return
	 * the nearest point in cases where an axis is not intersecting with the player.
	 */
	private static int getDistance(int distX, int distY, int distZ) {
		distX += (distX >>> 27); // ... >>> 31) << 4
		distY += (distY >>> 27); // ... >>> 31) << 4
		distZ += (distZ >>> 27); // ... >>> 31) << 4
		return MathExt.square(distX) + MathExt.square(distY) + MathExt.square(distZ);
	}

	private static final int MAX_SCALE = (1 << 25);

	/**
	 * Traces a ray from the section to the camera and tries to find obstruction in the way using the section
	 * current frame.
	 */
	private static boolean rayNotVisible(byte[] flags, short[] visSet, int cameraIndex, int sectionIndex, int renderDiameter, int dX, int dY, int dZ, int outwardDir) {
		int tMaxX = MAX_SCALE / (Math.abs(dX) | 1);
		int tMaxY = MAX_SCALE / (Math.abs(dY) | 1);
		int tMaxZ = MAX_SCALE / (Math.abs(dZ) | 1);

		int tDeltaX = tMaxX << 1;
		int tDeltaY = tMaxY << 1;
		int tDeltaZ = tMaxZ << 1;

		int signX = MathExt.sign(dX);
		int signY = MathExt.mulSign(renderDiameter, dY);
		int signZ = MathExt.mulSign(renderDiameter << 4, dZ);

		int validC = 0;
		int validT = 0;
		int inwardDir = outwardDir ^ 0x3F;

		for (int i = 0; i < 9; i++) {
			if (tMaxX < tMaxY) {
				if (tMaxX < tMaxZ) {
					sectionIndex += signX;
					tMaxX += tDeltaX;
				} else {
					sectionIndex += signZ;
					tMaxZ += tDeltaZ;
				}
			} else {
				if (tMaxY < tMaxZ) {
					sectionIndex += signY;
					tMaxY += tDeltaY;
				} else {
					sectionIndex += signZ;
					tMaxZ += tDeltaZ;
				}
			}

			if (cameraIndex == sectionIndex) {
				return false;
			}

			int flag = flags[sectionIndex];

			if ((CompressedFlags.getTraversableFaces(flag) & inwardDir) == 0 && ++validC >= 4) {
				return true;
			}

			int visFact = visSet[sectionIndex];

			if (visFact < TOLERANCE && ++validT >= 4) {
				return true;
			}
		}

		return false;
	}
}
