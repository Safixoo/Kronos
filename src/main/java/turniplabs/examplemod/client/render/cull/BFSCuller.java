package turniplabs.examplemod.client.render.cull;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.core.util.helper.MathHelper;
import turniplabs.examplemod.client.render.SectionFlags;
import turniplabs.examplemod.client.render.SectionManager;
import turniplabs.examplemod.client.render.SectionRender;
import turniplabs.examplemod.client.render.data.CameraData;
import turniplabs.examplemod.client.render.data.FogData;
import turniplabs.examplemod.client.render.region.RegionManager;
import turniplabs.examplemod.client.render.region.RegionRender;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.util.MathExt;

public class BFSCuller {
	public final BFSQueue bfsQueue = new BFSQueue();
	private int activeFrame;

	public void init(RegionManager regionManager, int cameraX, int cameraZ, int renderDistance) {
		UpdateQueue.clear();
		BFSVisArray.start(cameraX >> 4, cameraZ >> 4, renderDistance);

		for (RegionRender render : regionManager.regionMap.values()) {
			render.sectionsToRender = 0;
		}

		this.bfsQueue.prepareRegionArr(renderDistance);
		this.bfsQueue.clear();
		this.activeFrame++;
	}

	private static void bfsSearch(BFSQueue bfsQueue, int playerX, int playerY, int playerZ,
								  int renderDistance, int activeFrame) {
		int bfsIndex = 1;
		SectionRender node;

		while ((node = bfsQueue.get(bfsIndex++)) != null) {
			int flags = node.flags;

			if (isSectionInvisible(node, flags, playerX, playerY, playerZ, renderDistance)) {
				continue;
			}

			if (UpdateQueue.hasSpace() && SectionFlags.isDirty(flags)) {
				UpdateQueue.addToQueueUnsafe(node);
			}

			queueRegionNode(bfsQueue, node, flags);

			int outwardDirections = getOutwardDirections(playerX, playerY, playerZ, node);
			outwardDirections &= SectionFlags.getAdjacentMask(flags);
			// 2398 con - 2420 sin
//			outwardDirections &= getAngleVisibilityMask(playerX, playerY, playerZ, node);

			exploreNodes(bfsQueue, node, outwardDirections, activeFrame);
		}
	}

	public static boolean isSectionInvisible(SectionRender node, int flags, int playerX, int playerY, int playerZ, int fogEnd) {
		int distX = node.blockX - playerX;
		int distY = node.blockY - playerY;
		int distZ = node.blockZ - playerZ;

		BFSVisArray.setVisible(node.blockX >> 4, node.blockY >> 4, node.blockZ >> 4);

		int distance = withinRenderDistance(distX, distY, distZ);

		if (distance > fogEnd || !FrustumCuller.testAab(distX, distY, distZ)) {
			return true;
		}

		if (distance >= MathExt.square(128) && SectionFlags.hasDrawableFaces(flags)) {
			return !visibleByRayCast(node.blockX + 8, node.blockY + 8, node.blockZ + 8, -distX, -distY, -distZ);
		}

		return false;
	}

	public void updateRenderList(Long2ReferenceOpenHashMap<SectionRender> sectionMap, CameraData camera) {
		int chunkX = MathHelper.floor(camera.intX);
		int chunkY = MathHelper.clamp(MathHelper.floor(camera.intY), 0, 255);
		int chunkZ = MathHelper.floor(camera.intZ);

		SectionRender spawn = sectionMap.get(SectionManager.asLong(chunkX >> 4, chunkY >> 4, chunkZ >> 4));

		if (spawn != null) {
			int flags = spawn.flags;

			BFSVisArray.setVisible(spawn.blockX >> 4, spawn.blockY >> 4, spawn.blockZ >> 4);
			exploreNodes(this.bfsQueue, spawn, SectionFlags.getAdjacentMask(flags), this.activeFrame);

			if (SectionFlags.isDirty(flags)) {
				UpdateQueue.addToQueueUnsafe(spawn);
			}

			queueRegionNode(this.bfsQueue, spawn, flags);
		}

		bfsSearch(this.bfsQueue, camera.intX, camera.intY, camera.intZ, (int) MathExt.square(FogData.fogEnd), this.activeFrame);
	}

	private static void queueRegionNode(BFSQueue bfsQueue, SectionRender section, int flags) {
		if (SectionFlags.hasPassesNonEmpty(flags)) {
			RegionRender region = section.region;

			if (region.sectionsToRender == 0) {
				bfsQueue.regionRenders[bfsQueue.regionPos++] = region;
			}

			region.renderIndices[region.sectionsToRender++ & 0xFF] = (byte) section.regionIndex;
		}
	}

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

	private static int getOutwardDirections(int playerX, int playerY, int playerZ, SectionRender render) {
		int planes = 0;

		int diffChunkX = (render.blockX >> 4) - (playerX >> 4);

		planes |= (~ diffChunkX >> 31) & Direction.set(Direction.EAST);
		planes |= (~-diffChunkX >> 31) & Direction.set(Direction.WEST);

		int diffChunkY = (render.blockY >> 4) - (playerY >> 4);

		planes |= (~ diffChunkY >> 31) & Direction.set(Direction.UP);
		planes |= (~-diffChunkY >> 31) & Direction.set(Direction.DOWN);

		int diffChunkZ = (render.blockZ >> 4) - (playerZ >> 4);

		planes |= (~ diffChunkZ >> 31) & Direction.set(Direction.SOUTH);
		planes |= (~-diffChunkZ >> 31) & Direction.set(Direction.NORTH);

		return planes;
	}

	private static final int MAX_PRECISION = 1 << 25;

	private static boolean visibleByRayCast(int x1, int y1, int z1, int dx, int dy, int dz) {
		dx -= 8;
		dy -= 8;
		dz -= 8;

		int voxelX = x1 >> 4;
		int voxelY = y1 >> 4;
		int voxelZ = z1 >> 4;

		int stepX = sign(dx);
		int stepY = sign(dy);
		int stepZ = sign(dz);

		int invDx = MAX_PRECISION / (Math.abs(dx) + 1);
		int invDy = MAX_PRECISION / (Math.abs(dy) + 1);
		int invDz = MAX_PRECISION / (Math.abs(dz) + 1);

		int tDeltaX = invDx << 4;
		int tDeltaY = invDy << 4;
		int tDeltaZ = invDz << 4;

		int originOffsetX = (x1 & 15);
		int originOffsetY = (y1 & 15);
		int originOffsetZ = (z1 & 15);

		int tMaxX = (stepX > 0 ? (16 - originOffsetX) : originOffsetX + 1) * invDx;
		int tMaxY = (stepY > 0 ? (16 - originOffsetY) : originOffsetY + 1) * invDy;
		int tMaxZ = (stepZ > 0 ? (16 - originOffsetZ) : originOffsetZ + 1) * invDz;

		int invalid = 0;

		for (int i = 0; i < 5; i++) {
			if (tMaxX < tMaxY) {
				if (tMaxX < tMaxZ) {
					voxelX += stepX;
					tMaxX += tDeltaX;
				} else {
					voxelZ += stepZ;
					tMaxZ += tDeltaZ;
				}
			} else {
				if (tMaxY < tMaxZ) {
					voxelY += stepY;
					tMaxY += tDeltaY;
				} else {
					voxelZ += stepZ;
					tMaxZ += tDeltaZ;
				}
			}

			if (BFSVisArray.notVisible(voxelX, voxelY, voxelZ)) {
				if (invalid++ > 1) break;
			}
		}

		return invalid <= 1;
	}

	private static int sign(int num) {
		return (num >> 31) | 1;
	}

	private static int withinRenderDistance(int x, int y, int z) {
		x += 8;
		y += 8;
		z += 8;

		return (x * x) + (y * y) + (z * z);
	}
}
