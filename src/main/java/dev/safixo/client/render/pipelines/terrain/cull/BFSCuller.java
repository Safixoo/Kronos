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
	// be used in a consequential way, so it shouldn't mean a problem to the cache.
	private static final long[] INV_DIVS = new long[512];

	public static final int PRECISION_BITS = 25;
	public static final int MAX_PRECISION = 1 << PRECISION_BITS;

	static {
		for (int i = 0; i < 512; i++) {
			INV_DIVS[i] = (int) Math.ceil(MAX_PRECISION / (double) i);
		}
	}

	public void init(RegionManager regionManager, int renderDistance) {
		for (RegionRender render : regionManager.regionMap.values()) {
			render.sectionsToRender = 0;
		}

		this.bfsQueue.prepareRegionArr(renderDistance);
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
			exploreNodes(this.bfsQueue, origin, SectionFlags.getAdjacentMask(flags), this.activeFrame);

			origin.currentFrame = this.activeFrame;

			if (SectionFlags.isDirty(flags)) {
				RebuildList.addToList(origin);
			}

			queueRegionNode(this.bfsQueue, origin, flags);
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
							   int renderDistance, int activeFrame) {
		int bfsIndex = 0;
		SectionRender node;

		while ((node = bfsQueue.get(bfsIndex++)) != null) {
			int flags = node.flags;
			node.gridInd = MAX_PRECISION;

			int outwardDirections = getOutwardDirections(playerX, playerY, playerZ, node);

			if (isSectionInvisible(node, flags, playerX, playerY, playerZ, renderDistance, outwardDirections, activeFrame)) {
				continue;
			}

			if (SectionFlags.isDirty(flags)) {
				RebuildList.addToList(node);
			}

			queueRegionNode(bfsQueue, node, flags);

			outwardDirections &= SectionFlags.getAdjacentMask(flags);
			outwardDirections &= ~SectionFlags.getCullFaces(flags);

			exploreNodes(bfsQueue, node, outwardDirections, activeFrame);
		}
	}

	/**
	 * The original method comes from the Sodium way of doing BFS culling, only changed to be branch-less,
	 * (removing the branches is only an optimization in Java because it refuses to generate branch-less code)
	 * which helps by avoiding back-tracking and doesn't de-reference already visited sections.
	 */
	private static int getOutwardDirections(int playerX, int playerY, int playerZ, SectionRender render) {
		int planes = 0;

		int diffChunkX = (render.blockX >> 4) - (playerX >> 4);

		planes |= ( diffChunkX >> 31) & Direction.EAST_BIT;
		planes |= (-diffChunkX >> 31) & Direction.WEST_BIT;

		int diffChunkY = (render.blockY >> 4) - (playerY >> 4);

		planes |= ( diffChunkY >> 31) & Direction.UP_BIT;
		planes |= (-diffChunkY >> 31) & Direction.DOWN_BIT;

		int diffChunkZ = (render.blockZ >> 4) - (playerZ >> 4);

		planes |= ( diffChunkZ >> 31) & Direction.SOUTH_BIT;
		planes |= (-diffChunkZ >> 31) & Direction.NORTH_BIT;

		return ~planes;
	}

	/**
	 * For drawable section that has been visited by the graph, their indices are saved in their respective
	 * region, and their region are saved based in the BFS order. In a future should be more favorable save only
	 * the index of section in a branch less manner, and later sort enqueued regions to minimize the work of the BFS
	 * for node.
	 */
	private static void queueRegionNode(BFSQueue bfsQueue, SectionRender section, int flags) {
		if (SectionFlags.hasPassesNonEmpty(flags)) {
			RegionRender region = section.region;

			if (region.sectionsToRender == 0) {
				bfsQueue.regionRenders[bfsQueue.regionPos++] = region;
			}

			region.renderIndices[region.sectionsToRender++] = (short) section.regionIndex;
		}
	}

	/**
	 * Searches outwards from the player possible visitable sections, based in solidness in the section and faces,
	 * also avoids visiting sections if they were already visited in the current frame.
	 */
	private static void exploreNodes(BFSQueue queue, SectionRender fatherNode, int directions, int activeFrame) {
		if (directions == 0) {
			return;
		}

		queue.verifyCapacity(Direction.COUNT);

		SectionRender render;

		if (Direction.hasSet(directions, Direction.DOWN) && (render = fatherNode.adjacentDown).currentFrame < activeFrame) {
			queue.addToQueueUnsafe(render);
			render.currentFrame = activeFrame;
		}

		if (Direction.hasSet(directions, Direction.UP) && (render = fatherNode.adjacentUp).currentFrame < activeFrame) {
			queue.addToQueueUnsafe(render);
			render.currentFrame = activeFrame;
		}

		if (Direction.hasSet(directions, Direction.NORTH) && (render = fatherNode.adjacentNorth).currentFrame < activeFrame) {
			queue.addToQueueUnsafe(render);
			render.currentFrame = activeFrame;
		}

		if (Direction.hasSet(directions, Direction.SOUTH) && (render = fatherNode.adjacentSouth).currentFrame < activeFrame) {
			queue.addToQueueUnsafe(render);
			render.currentFrame = activeFrame;
		}

		if (Direction.hasSet(directions, Direction.WEST) && (render = fatherNode.adjacentWest).currentFrame < activeFrame) {
			queue.addToQueueUnsafe(render);
			render.currentFrame = activeFrame;
		}

		if (Direction.hasSet(directions, Direction.EAST) && (render = fatherNode.adjacentEast).currentFrame < activeFrame) {
			queue.addToQueueUnsafe(render);
			render.currentFrame = activeFrame;
		}
	}

	private static final int MAX_TOLERANCE = (int) (0.15f * MAX_PRECISION);

	/**
	 * First check if the section is within the fog circle, then if it's outside the camera frustum, after
	 * use grid based visibility technique to determine a threshold of visibility for the section, and in cases
	 * where the sections are not empty check whether tracing a ray from the section to the camera finds obstruction
	 * in the process.
	 */
	public static boolean isSectionInvisible(SectionRender node, int flags, int playerX, int playerY, int playerZ, int fogEnd,
											 int outwardDirections, int frame) {
		int distX = node.blockX - playerX;
		int distY = node.blockY - playerY;
		int distZ = node.blockZ - playerZ;

		int distance = withinRenderDistance(distX, distY, distZ);

		if (distance >= fogEnd || !FrustumCuller.withinFrustumBounds(distX, distY, distZ)) {
			return true;
		}

		int gridInd = processGridIndex(node, playerX, playerY, playerZ, outwardDirections, frame);
		node.gridInd = gridInd;

		if (gridInd < MAX_TOLERANCE) {
			return true;
		}

		if (distance >= MathExt.square(112) && SectionFlags.hasDrawableFaces(flags)) {
			return !visibleByRayCast(node, frame, node.blockX + 8, node.blockY + 8, node.blockZ + 8, -distX, -distY, -distZ);
		}

		return false;
	}

	private static boolean renderThisFrame(SectionRender section, int dirSet, int direction, int frame) {
		return (dirSet & (1 << direction)) != 0 && section.currentFrame == frame;
	}

	/**
	 * Uses the key idea from the article of <a href ="https://towardsdatascience.com/a-quick-and-clear-look-at-grid-based-visibility-bf63769fbc78">Grid Based Visibility</a>
	 * to determine the factor of grid visibility in 3D for the current visited section of the graph.
	 * As it stands right now is poorly optimized, but it rewards in all the works it skips are sections that it avoids.
	 * @return Grid visibility factor
	 */
	private static int processGridIndex(SectionRender section, int playerX, int playerY, int playerZ, int outwardDir, int frame) {
		int diffX = Math.abs((section.blockX >> 4) - (playerX >> 4));
		int diffY = Math.abs((section.blockY >> 4) - (playerY >> 4));
		int diffZ = Math.abs((section.blockZ >> 4) - (playerZ >> 4));

		if (diffX == 0 || diffY == 0 || diffZ == 0) {
			return MAX_PRECISION;
		}

		int dirSet = ~outwardDir & SectionFlags.getAdjacentMask(section.flags);
		int gradInd = 0;

		// Y
		if (renderThisFrame(section.adjacentDown, dirSet, Direction.DOWN, frame)) {
			gradInd += diffY * section.adjacentDown.gridInd;
		} else if (renderThisFrame(section.adjacentUp, dirSet, Direction.UP, frame)) {
			gradInd += diffY * section.adjacentUp.gridInd;
		}

		// Z
		if (renderThisFrame(section.adjacentNorth, dirSet, Direction.NORTH, frame)) {
			gradInd += diffZ * section.adjacentNorth.gridInd;
		} else if (renderThisFrame(section.adjacentSouth, dirSet, Direction.SOUTH, frame)) {
			gradInd += diffZ * section.adjacentSouth.gridInd;
		}

		// X
		if (renderThisFrame(section.adjacentWest, dirSet, Direction.WEST, frame)) {
			gradInd += diffX * section.adjacentWest.gridInd;
		} else if (renderThisFrame(section.adjacentEast, dirSet, Direction.EAST, frame)) {
			gradInd += diffX * section.adjacentEast.gridInd;
		}

		return (int) ((gradInd * INV_DIVS[diffX + diffY + diffZ]) >> PRECISION_BITS);
	}

	/**
	 * Squared sphere distance from the nearest corner, which almost always returns the nearest point
	 * in cases where an axis is not intersecting with the player.
	 */
	private static int withinRenderDistance(int distX, int distY, int distZ) {
		distX += (distX >>> 31) << 4;
		distY += (distY >>> 31) << 4;
		distZ += (distZ >>> 31) << 4;
		return (distX * distX) + (distY * distY) + (distZ * distZ);
	}

	/**
	 * Traces a ray from the section to the camera and tries to find obstruction in the way using the visited
	 * section current frame.
	 */
	private static boolean visibleByRayCast(SectionRender node, int frame, int x1, int y1, int z1, int dx, int dy, int dz) {
		dx -= 8;
		dy -= 8;
		dz -= 8;

		int invDx = MAX_PRECISION / (Math.abs(dx) + 1);
		int invDy = MAX_PRECISION / (Math.abs(dy) + 1);
		int invDz = MAX_PRECISION / (Math.abs(dz) + 1);

		int tDeltaX = invDx << 4;
		int tDeltaY = invDy << 4;
		int tDeltaZ = invDz << 4;

		int originOffsetX = (x1 & 15);
		int originOffsetY = (y1 & 15);
		int originOffsetZ = (z1 & 15);

		int tMaxX = (dx > 0 ? (16 - originOffsetX) : originOffsetX + 1) * invDx;
		int tMaxY = (dy > 0 ? (16 - originOffsetY) : originOffsetY + 1) * invDy;
		int tMaxZ = (dz > 0 ? (16 - originOffsetZ) : originOffsetZ + 1) * invDz;

		int valid = 0;

		for (int i = 0; i < 4; i++) {
			if (tMaxX < tMaxY) {
				if (tMaxX < tMaxZ) {
					node = dx < 0 ? node.adjacentWest : node.adjacentEast;
					tMaxX += tDeltaX;
				} else {
					node = dz < 0 ? node.adjacentNorth : node.adjacentSouth;
					tMaxZ += tDeltaZ;
				}
			} else {
				if (tMaxY < tMaxZ) {
					node = dy < 0 ? node.adjacentDown : node.adjacentUp;
					tMaxY += tDeltaY;
				} else {
					node = dz < 0 ? node.adjacentNorth : node.adjacentSouth;
					tMaxZ += tDeltaZ;
				}
			}

			if (node == null || (node.currentFrame != frame && valid++ > 2)) {
				break;
			}
		}

		return valid <= 2;
	}

	public int getActiveFrame() {
		return this.activeFrame;
	}

	private static int sign(int num) {
		return (num >> 31) | 1;
	}
}
