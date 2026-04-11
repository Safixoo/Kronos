package dev.safixo.client.render.pipelines.terrain.cull;

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

	private static final int MAX_GRID_FACTOR = 1 << 12;
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
		int chunkX = camera.intX;
		int chunkY = MathExt.clamp(camera.intY, 0, 255);
		int chunkZ = camera.intZ;

		SectionRender origin = sectionMap.get(MathExt.asLong(chunkX >> 4, chunkY >> 4, chunkZ >> 4));

		if (origin != null) {
			int flags = origin.flags;
			searchNeighbors(this.bfsQueue, origin, SectionFlags.getAdjacentMask(flags), this.activeFrame);

			origin.currentFrame = this.activeFrame;
			origin.gridFactor = MAX_GRID_FACTOR;

			if (SectionFlags.isDirty(flags)) {
				RebuildList.addToList(origin);
			}

			queueRegionNode(origin, flags);
		}

		double maxDistance = Math.min(GlStateTracker.FOG_END, camera.renderDistance << 4);
		search(this.bfsQueue, camera.intX, camera.intY, camera.intZ, (int) MathExt.square(maxDistance), this.activeFrame);
	}

	/**
	 * Does a BFS search based in the <a href="https://tomcc.github.io/2014/08/31/visibility-1.html">Advanced Cave Culling</a>
	 * by tomcc, with many differences, as it doesn't try to find connectivity 100% and uses some more ideas to avoid section
	 * queueing during the search.
	 */
	private static void search(BFSQueue bfsQueue, int playerX, int playerY, int playerZ,
							   int renderDistance, int frame) {
		int bfsIndex = 0;
		SectionRender node;

		while ((node = bfsQueue.get(bfsIndex++)) != null) {
			int flags = node.flags;
			node.gridFactor = MAX_GRID_FACTOR;

			int distX = node.blockX - playerX;
			int distY = node.blockY - playerY;
			int distZ = node.blockZ - playerZ;

			int distance = getDistance(distX, distY, distZ);

			if (distance >= renderDistance || !FrustumCuller.withinFrustumBounds(distX, distY, distZ)) {
				continue;
			}

			int distChunkX = (node.blockX >> 4) - (playerX >> 4);
			int distChunkY = (node.blockY >> 4) - (playerY >> 4);
			int distChunkZ = (node.blockZ >> 4) - (playerZ >> 4);

			int outwardMask = getOutwardDirections(distChunkX, distChunkY, distChunkZ);
			int gridFactor = node.gridFactor = processGridIndex(node, flags, distChunkX, distChunkY, distChunkZ, outwardMask, frame);

			if (gridFactor < TOLERANCE ||
				(distance >= 112 * 112 && SectionFlags.hasDrawableFaces(flags) && !rayVisible(node, frame, -distX - 8, -distY - 8, -distZ - 8))) {
				continue;
			}

			if (SectionFlags.isDirty(flags)) {
				RebuildList.addToList(node);
			}

			outwardMask &= SectionFlags.getAdjacentMask(flags);
			outwardMask &= ~SectionFlags.getCullFaces(flags);

			queueRegionNode(node, flags);
			searchNeighbors(bfsQueue, node, outwardMask, frame);
		}
	}

	/**
	 * Generates a mask to discard invariants directions early in the search.
	 */
	private static int getOutwardDirections(int diffChunkX, int diffChunkY, int diffChunkZ) {
		int planes = 0;

		planes |= (diffChunkX >> 31) & Direction.EAST_BIT  | (-diffChunkX >> 31) & Direction.WEST_BIT;
		planes |= (diffChunkY >> 31) & Direction.UP_BIT    | (-diffChunkY >> 31) & Direction.DOWN_BIT;
		planes |= (diffChunkZ >> 31) & Direction.SOUTH_BIT | (-diffChunkZ >> 31) & Direction.NORTH_BIT;

		return ~planes;
	}

	/**
	 * For non-empty sections that has been visited by the graph, their indices are saved in their respective
	 * region.
	 */
	private static void queueRegionNode(SectionRender section, int flags) {
		if (SectionFlags.hasPassesNonEmpty(flags)) {
			RegionRender region = section.region;
			region.renderIndices[region.sectionsToRender++] = (short) section.regionIndex;
		}
	}

	/**
	 * Searches outwards from the player possible visitable sections, based in solidness in the section and faces,
	 * also avoids visiting sections if they were already visited in the current frame.
	 */
	private static void searchNeighbors(BFSQueue queue, SectionRender fatherNode, int directions, int activeFrame) {
		if (directions == 0b0) {
			return;
		}

		queue.verifyCapacity(Direction.COUNT);

		SectionRender render;

		if (Direction.hasSet(directions, Direction.DOWN) && (render = fatherNode.adjacentDown).currentFrame < activeFrame) {
			queue.addSectionToQueue(render);
			render.currentFrame = activeFrame;
		}

		if (Direction.hasSet(directions, Direction.UP) && (render = fatherNode.adjacentUp).currentFrame < activeFrame) {
			queue.addSectionToQueue(render);
			render.currentFrame = activeFrame;
		}

		if (Direction.hasSet(directions, Direction.NORTH) && (render = fatherNode.adjacentNorth).currentFrame < activeFrame) {
			queue.addSectionToQueue(render);
			render.currentFrame = activeFrame;
		}

		if (Direction.hasSet(directions, Direction.SOUTH) && (render = fatherNode.adjacentSouth).currentFrame < activeFrame) {
			queue.addSectionToQueue(render);
			render.currentFrame = activeFrame;
		}

		if (Direction.hasSet(directions, Direction.WEST) && (render = fatherNode.adjacentWest).currentFrame < activeFrame) {
			queue.addSectionToQueue(render);
			render.currentFrame = activeFrame;
		}

		if (Direction.hasSet(directions, Direction.EAST) && (render = fatherNode.adjacentEast).currentFrame < activeFrame) {
			queue.addSectionToQueue(render);
			render.currentFrame = activeFrame;
		}
	}


	private static boolean renderThisFrame(SectionRender section, int dirSet, int direction, int frame) {
		return (dirSet & (1 << direction)) != 0 && section.currentFrame == frame;
	}

	private static boolean isIntersectingAxis(int dir) {
		int dir0 = dir & 0b110011;
		int dir1 = dir & 0b001100;
		return (dir0 & (dir0 >> 1)) != 0 || dir1 == 0b001100;
	}

	/**
	 * Uses the key idea from the article of <a href ="https://towardsdatascience.com/a-quick-and-clear-look-at-grid-based-visibility-bf63769fbc78">Grid Based Visibility</a>
	 * to determine the factor of grid visibility in 3D for the current visited section of the graph.
	 * As it stands right now is poorly optimized, but it rewards in all the works it skips are sections that it avoids.
	 * @return Grid visibility factor
	 */
	private static int processGridIndex(SectionRender section, int flags, int diffX, int diffY, int diffZ, int outwardDir, int frame) {
		if (diffX == 0 || diffY == 0 || diffZ == 0) {
		    return MAX_GRID_FACTOR;
		}

		int gradInd = 0;

		diffX = Math.abs(diffX);
		diffY = Math.abs(diffY);
		diffZ = Math.abs(diffZ);

		int dirSet = ~outwardDir & SectionFlags.getAdjacentMask(flags);

		// Y
		if (renderThisFrame(section.adjacentDown, dirSet, Direction.DOWN, frame)) {
			gradInd += diffY * section.adjacentDown.gridFactor;
		} else if (renderThisFrame(section.adjacentUp, dirSet, Direction.UP, frame)) {
			gradInd += diffY * section.adjacentUp.gridFactor;
		}

		// Z
		if (renderThisFrame(section.adjacentNorth, dirSet, Direction.NORTH, frame)) {
			gradInd += diffZ * section.adjacentNorth.gridFactor;
		} else if (renderThisFrame(section.adjacentSouth, dirSet, Direction.SOUTH, frame)) {
			gradInd += diffZ * section.adjacentSouth.gridFactor;
		}

		// X
		if (renderThisFrame(section.adjacentWest, dirSet, Direction.WEST, frame)) {
			gradInd += diffX * section.adjacentWest.gridFactor;
		} else if (renderThisFrame(section.adjacentEast, dirSet, Direction.EAST, frame)) {
			gradInd += diffX * section.adjacentEast.gridFactor;
		}

		return (int) ((gradInd * INV_DIVS[diffX + diffY + diffZ]) >> PRECISION_BITS);
	}

	/**
	 * Squared sphere distance from the nearest corner, which almost always returns the nearest point
	 * in cases where an axis is not intersecting with the player.
	 */
	private static int getDistance(int distX, int distY, int distZ) {
		distX += (distX >>> 27); // ... >>> 31) << 4
		distY += (distY >>> 27); // ... >>> 31) << 4
		distZ += (distZ >>> 27); // ... >>> 31) << 4
		return MathExt.square(distX) + MathExt.square(distY) + MathExt.square(distZ);
	}

	private static final int MAX_SCALE = (1 << 25);

	/**
	 * Traces a ray from the section to the camera and tries to find obstruction in the way using the visited
	 * section current frame.
	 */
	private static boolean rayVisible(SectionRender section, int frame, int dx, int dy, int dz) {
		int tMaxX = MAX_SCALE / (Math.abs(dx) | 1);
		int tMaxY = MAX_SCALE / (Math.abs(dy) | 1);
		int tMaxZ = MAX_SCALE / (Math.abs(dz) | 1);

		int tDeltaX = tMaxX << 1;
		int tDeltaY = tMaxY << 1;
		int tDeltaZ = tMaxZ << 1;

		int valid = 0;

		for (int i = 0; i < 4; i++) {
			if (tMaxX < tMaxY) {
				if (tMaxX < tMaxZ) {
					section = dx < 0 ? section.adjacentWest : section.adjacentEast;
					tMaxX += tDeltaX;
				} else {
					section = dz < 0 ? section.adjacentNorth : section.adjacentSouth;
					tMaxZ += tDeltaZ;
				}
			} else {
				if (tMaxY < tMaxZ) {
					section = dy < 0 ? section.adjacentDown : section.adjacentUp;
					tMaxY += tDeltaY;
				} else {
					section = dz < 0 ? section.adjacentNorth : section.adjacentSouth;
					tMaxZ += tDeltaZ;
				}
			}

			if (section == null || (section.currentFrame != frame && valid++ > 2)) {
				break;
			}
		}

		return valid <= 2;
	}

	public int getActiveFrame() {
		return this.activeFrame;
	}
}
