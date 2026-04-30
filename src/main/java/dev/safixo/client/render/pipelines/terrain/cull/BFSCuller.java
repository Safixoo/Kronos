package dev.safixo.client.render.pipelines.terrain.cull;

import dev.safixo.client.render.pipelines.terrain.SectionManager;
import dev.safixo.core.hooks.GlStateTracker;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import dev.safixo.client.render.pipelines.terrain.SectionFlags;
import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.render.pipelines.terrain.region.RegionManager;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;

public class BFSCuller {
	public final BFSQueue bfsQueue = new BFSQueue();
	private int activeFrame;

	// The max denominator would always be a renderDistance * 3 worst case, and should always
	// be accessed in a linear way, so it shouldn't mean a problem for the cache.
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

		this.bfsQueue.clear();
		this.activeFrame++;
	}

	/**
	 * Gets the first sections of the search, prepares the graph-search max distance and queues the first couple
	 * of sections to start the search afterward.
	 */
	public void updateRenderList(Long2ReferenceOpenHashMap<SectionRender> sectionMap, CameraData camera) {
		int blockX = camera.intX;
		int blockY = MathExt.clamp(camera.intY, 0, 255);
		int blockZ = camera.intZ;

		SectionRender origin = sectionMap.get(MathExt.asLong(blockX >> 4, blockY >> 4, blockZ >> 4));

		int renderDistance = camera.renderDistance;
		int renderDiameter = renderDistance * 2 + 1;
		int sectionIndex = SectionManager.getFlagIndex(renderDistance, blockY >> 4, renderDistance, renderDistance);

		if (origin != null) {
			int flags = origin.flags;

			searchNeighbors(this.bfsQueue, sectionIndex, renderDiameter, SectionFlags.getAdjacentMask(flags));

			if (SectionFlags.isDirty(flags)) {
				RebuildList.addToList(origin.globalSectionPos);
			}

			queueRegionNode(origin, flags);
		}

		double maxDistance = Math.max(3 << 4, Math.min(GlStateTracker.FOG_END, renderDistance << 4));
		search(this.bfsQueue, camera.intX, camera.intY, camera.intZ, (int) MathExt.square(maxDistance), renderDistance);
	}

	/**
	 * Does a BFS search based in the <a href="https://tomcc.github.io/2014/08/31/visibility-1.html">Advanced Cave Culling</a>
	 * by tomcc and Sodium implementation, with many differences as it doesn't try to find connectivity 100% and uses different
	 * ideas to avoid section queueing during the search.
	 */
	private static void search(BFSQueue bfsQueue, int playerX, int playerY, int playerZ, int fogDistance, int renderDistance) {
		SectionManager sectionManager = SectionManager.getCurrentInstance();

		int[] sectionFlags = sectionManager.sectionFlags;
		short[] visibilitySet = sectionManager.visibilitySet;

		int renderDiameter = renderDistance * 2 + 1;

		int readIndex = 0;

		while (readIndex < bfsQueue.bfsIndex) {
			int sectionIndex = bfsQueue.get(readIndex++);
			int flags = sectionFlags[sectionIndex];

			int xi = sectionIndex;
			int yi = xi / renderDiameter;
			int zi = yi >> 4;

			xi %= renderDiameter;
			yi &= 15;

			int distChunkX = xi - renderDistance;
			int distChunkY = yi - (playerY >> 4);
			int distChunkZ = zi - renderDistance;

			int distX = (distChunkX << 4) - (playerX & 15);
			int distY = (distChunkY << 4) - (playerY & 15);
			int distZ = (distChunkZ << 4) - (playerZ & 15);

			int distance = getDistance(distX, distY, distZ);

			if (distance >= fogDistance || !FrustumCuller.withinFrustumBounds(distX, distY, distZ)) {
				continue;
			}

			int directions = getOutwardDirections(distChunkX, distChunkY, distChunkZ);
//			int gridFactor = processGridIndex(visibilitySet, sectionIndex, renderDiameter, distChunkX, distChunkY, distChunkZ);
//
//			visibilitySet[sectionIndex] = (short) Math.max(1, gridFactor);

			if
			(
//				gridFactor < TOLERANCE
				(distance >= 112 * 112 && SectionFlags.hasPassesNonEmpty(flags) && !rayVisible(visibilitySet, sectionIndex, renderDiameter, -distX - 8, -distY - 8, -distZ - 8))
			)
			{
				continue;
			}

			int sectionX = distChunkX + (playerX >> 4);
			int sectionY = distChunkY + (playerY >> 4);
			int sectionZ = distChunkZ + (playerZ >> 4);

			if (SectionFlags.isDirty(flags)) {
				RebuildList.addToList(MathExt.asLong(sectionX, sectionY, sectionZ));
			}

			directions &= SectionFlags.getAdjacentMask(flags);
			directions &= ~SectionFlags.getCullFaces(flags);

			if (SectionFlags.hasPassesNonEmpty(flags)) {
				bfsQueue.addToRenderList(MathExt.asLong(sectionX, sectionY, sectionZ));
			}

			searchNeighbors(bfsQueue, sectionIndex, renderDiameter, directions);
		}

		for (long position : bfsQueue.renderList) {
			int sectionX = MathExt.decodeX(position);
			int sectionY = MathExt.decodeY(position);
			int sectionZ = MathExt.decodeZ(position);

			int regionIndex = RegionRender.regionIndex(sectionX, sectionY, sectionZ);
			RegionRender region = sectionManager.getRegion(sectionX, sectionY, sectionZ);

			region.renderIndices[region.sectionsToRender++] = (short) regionIndex;
		}

		bfsQueue.renderList.clear();
	}

	/**
	 * Generates a mask to discard invariants inward directions early in the search.
	 */
	private static int getOutwardDirections(int diffChunkX, int diffChunkY, int diffChunkZ) {
		int planes = 0;

		planes |= (diffChunkX >> 31) & Direction.EAST_BIT  | (-diffChunkX >> 31) & Direction.WEST_BIT;
		planes |= (diffChunkY >> 31) & Direction.UP_BIT    | (-diffChunkY >> 31) & Direction.DOWN_BIT;
		planes |= (diffChunkZ >> 31) & Direction.SOUTH_BIT | (-diffChunkZ >> 31) & Direction.NORTH_BIT;

		return ~planes;
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
	private static void searchNeighbors(BFSQueue queue, int sectionIndex, int renderDiameter, int directions) {
		if (directions == 0b0) {
			return;
		}

		queue.verifyCapacity(Direction.COUNT);

		SectionManager sectionManager = SectionManager.getCurrentInstance();
		short[] visSet = sectionManager.visibilitySet;

		int offsetX = 1;
		int offsetY = renderDiameter;
		int offsetZ = renderDiameter * 16;

		int index = queue.bfsIndex;

		if (Direction.hasSet(directions, Direction.DOWN) && visSet[sectionIndex - offsetY] == 0) {
			queue.indices[index++] = sectionIndex - offsetY;
			visSet[sectionIndex - offsetY] = MAX_GRID_FACTOR;
		}

		if (Direction.hasSet(directions, Direction.UP) && visSet[sectionIndex + offsetY] == 0) {
			queue.indices[index++] = sectionIndex + offsetY;
			visSet[sectionIndex + offsetY] = MAX_GRID_FACTOR;
		}

		if (Direction.hasSet(directions, Direction.NORTH) && visSet[sectionIndex - offsetZ] == 0) {
			queue.indices[index++] = sectionIndex - offsetZ;
			visSet[sectionIndex - offsetZ] = MAX_GRID_FACTOR;
		}

		if (Direction.hasSet(directions, Direction.SOUTH) && visSet[sectionIndex + offsetZ] == 0) {
			queue.indices[index++] = sectionIndex + offsetZ;
			visSet[sectionIndex + offsetZ] = MAX_GRID_FACTOR;
		}

		if (Direction.hasSet(directions, Direction.WEST) && visSet[sectionIndex - offsetX] == 0) {
			queue.indices[index++] = sectionIndex - offsetX;
			visSet[sectionIndex - offsetX] = MAX_GRID_FACTOR;
		}

		if (Direction.hasSet(directions, Direction.EAST) && visSet[sectionIndex + offsetX] == 0) {
			queue.indices[index++] = sectionIndex + offsetX;
			visSet[sectionIndex + offsetX] = MAX_GRID_FACTOR;
		}

		queue.bfsIndex = index;
	}

	private static boolean renderThisFrame(SectionRender section, int dirSet, int direction, int frame) {
		return (dirSet & (1 << direction)) != 0 && section.currentFrame == frame;
	}

	private static int sign(int a) {
		return a >> 31 | 1;
	}

	/**
	 * Uses the key idea from the article of <a href ="https://towardsdatascience.com/a-quick-and-clear-look-at-grid-based-visibility-bf63769fbc78">Grid Based Visibility</a>
	 * to determine the factor of grid visibility in 3D for the current visited section of the graph. It might not be perfectly
	 * optimized but it helps a ton when there's a lot of occluders.
	 * @return Grid visibility factor
	 */
	private static int processGridIndex(short[] visSet, int sectionIndex, int renderDiameter, int diffChunkX, int diffChunkY, int diffChunkZ) {
		if (diffChunkX == 0 || diffChunkY == 0 || diffChunkZ == 0) {
		    return MAX_GRID_FACTOR;
		}

		int gradInd = 0;

		int signX = sign(diffChunkX);
		int signY = sign(diffChunkY) * renderDiameter;
		int signZ = sign(diffChunkZ) * renderDiameter * 16;

		diffChunkX = Math.abs(diffChunkX);
		diffChunkY = Math.abs(diffChunkY);
		diffChunkZ = Math.abs(diffChunkZ);

		// Y
		gradInd += diffChunkY * visSet[sectionIndex + signY];
		// Z
		gradInd += diffChunkZ * visSet[sectionIndex + signZ];
		// X
		gradInd += diffChunkX * visSet[sectionIndex + signX];

		return (int) ((gradInd * INV_DIVS[diffChunkX + diffChunkY + diffChunkZ]) >> PRECISION_BITS);
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
	private static boolean rayVisible(short[] visSet, int sectionIndex, int renderDiameter, int dx, int dy, int dz) {
		int tMaxX = MAX_SCALE / (Math.abs(dx) | 1);
		int tMaxY = MAX_SCALE / (Math.abs(dy) | 1);
		int tMaxZ = MAX_SCALE / (Math.abs(dz) | 1);

		int tDeltaX = tMaxX << 1;
		int tDeltaY = tMaxY << 1;
		int tDeltaZ = tMaxZ << 1;

		int valid = 0;

		int signX = sign(dx);
		int signY = sign(dy) * renderDiameter;
		int signZ = sign(dz) * renderDiameter * 16;

		for (int i = 0; i < 4; i++) {
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

			if (visSet[sectionIndex] == 0 && valid++ > 2) {
				break;
			}
		}

		return valid <= 2;
	}

	public int getActiveFrame() {
		return this.activeFrame;
	}
}
