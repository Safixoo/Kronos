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
import turniplabs.examplemod.client.util.Mth;

public class BFSCuller {
	public final BFSQueue bfsQueue = new BFSQueue();
	private RegionManager regionManager;
	private int activeFrame;

	public void setRenderingLists(RegionManager regionManager) {
		this.regionManager = regionManager;
	}

	public void init(int cameraX, int cameraZ, float fogEnd, int renderDistance) {
		UpdateQueue.clear();
		BFSVisArray.start(cameraX >> 4, cameraZ >> 4, renderDistance);

		for (RegionRender render : this.regionManager.regionRenders) {
			render.translucentEmptyDraw = 0;
			render.solidEmptyDraw = 0;
		}

		this.regionManager.regionRenders.clear();

		this.bfsQueue.clear();
		this.activeFrame++;
	}

	private static void bfsSearch(BFSQueue bfsQueue, RegionManager regionManager, int playerX, int playerY, int playerZ,
								  int renderDistance, int activeFrame) {
		int bfsIndex = 0;

		SectionRender node;
		while ((node = bfsQueue.get(bfsIndex++)) != null) {
			int flags = node.flags;

			if (isSectionInvisible(node, flags, playerX, playerY, playerZ, renderDistance)) {
				continue;
			}

			queueRegionNode(node, regionManager, flags, activeFrame, playerX, playerY, playerZ);

			int outwardDirections = getOutwardDirections(playerX, playerY, playerZ, node);
			outwardDirections &= SectionFlags.getAdjacentMask(flags);
			outwardDirections &= ~SectionFlags.getSolidFaces(flags);

			exploreNodes(bfsQueue, node, outwardDirections, activeFrame);
		}
	}

	public static boolean isSectionInvisible(SectionRender node, int flags, int playerX, int playerY, int playerZ, int fogEnd) {
		int distX = node.blockX - playerX;
		int distY = node.blockY - playerY;
		int distZ = node.blockZ - playerZ;

		BFSVisArray.setVisible(node.blockX >> 4, node.blockY >> 4, node.blockZ >> 4);

		float distance = withinRenderDistance(distX, distY, distZ);

		if (distance >= fogEnd || (distance >= Mth.square(16) && !FrustumCuller.testAab(distX, distY, distZ))) {
			return true;
		}

		if (distance >= Mth.square(128) && SectionFlags.hasDrawableFaces(flags)) {
			return !visibleByRayCast(node.blockX + 8, node.blockY + 8, node.blockZ + 8, -distX, -distY, -distZ);
		}

		return false;
	}

	public void updateRenderList(Long2ReferenceOpenHashMap<SectionRender> sectionMap, CameraData camera) {
		int chunkX = MathHelper.floor(camera.cameraX);
		int chunkY = MathHelper.clamp(MathHelper.floor(camera.cameraY), 0, 255);
		int chunkZ = MathHelper.floor(camera.cameraZ);

		SectionRender spawn = sectionMap.get(SectionManager.asLong(chunkX >> 4, chunkY >> 4, chunkZ >> 4));

		if (spawn != null) {
			int flags = spawn.flags;

			exploreNodes(this.bfsQueue, spawn, SectionFlags.getAdjacentMask(flags), this.activeFrame);

			if (SectionFlags.isDirty(flags)) {
				UpdateQueue.addToQueueUnsafe(spawn);
			}

			queueRegionNode(spawn, this.regionManager, flags, this.activeFrame, camera.cameraX, camera.cameraY, camera.cameraZ);
		}

		bfsSearch(this.bfsQueue, this.regionManager, camera.cameraX, camera.cameraY, camera.cameraZ, (int) Mth.square(FogData.fogEnd), this.activeFrame);
	}

	private static void queueRegionNode(SectionRender section, RegionManager regionManager, int flags, int activeFrame, int playerX, int playerY, int playerZ) {
		if (!SectionFlags.hasRegion(flags)) {
			return;
		}

		RegionRender region = section.region;

		if (region.currentFrame != activeFrame) {
			region.currentFrame = activeFrame;

			if (SectionFlags.hasPassesNonEmpty(flags)) {
				regionManager.addToDrawQueue(region);
			}
		}

		if (SectionFlags.hasSolidPass(flags)) {
			int visibleFaces = getVisibleFaces(playerX, playerY, playerZ, section.blockX, section.blockY, section.blockZ) & SectionFlags.getDrawableFaces(flags);
			SectionManager.getCurrentInstance().drawnSolidRenderers++;

			if ((visibleFaces & (1 << 0)) != 0) {
				region.addSolidDraw(section.solidDrawFaces[0]);
			}
			if ((visibleFaces & (1 << 1)) != 0) {
				region.addSolidDraw(section.solidDrawFaces[1]);
			}
			if ((visibleFaces & (1 << 2)) != 0) {
				region.addSolidDraw(section.solidDrawFaces[2]);
			}
			if ((visibleFaces & (1 << 3)) != 0) {
				region.addSolidDraw(section.solidDrawFaces[3]);
			}
			if ((visibleFaces & (1 << 4)) != 0) {
				region.addSolidDraw(section.solidDrawFaces[4]);
			}
			if ((visibleFaces & (1 << 5)) != 0) {
				region.addSolidDraw(section.solidDrawFaces[5]);
			}
			if ((visibleFaces & (1 << 6)) != 0) {
				region.addSolidDraw(section.solidDrawFaces[6]);
			}
		}

		if (SectionFlags.hasTranslucentPass(flags)) {
			region.addTranslucentDraw(section.transDrawData);
		}
	}

	public static int getVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
		int planes = (1 << Direction.COUNT);

		planes |= greaterThan(originX, (chunkX - 3)) << Direction.EAST;
		planes |= greaterThan(originY, (chunkY - 3)) << Direction.UP;
		planes |= greaterThan(originZ, (chunkZ - 3)) << Direction.SOUTH;

		planes |= lessThan(originX, (chunkX + 19)) << Direction.WEST;
		planes |= lessThan(originY, (chunkY + 19)) << Direction.DOWN;
		planes |= lessThan(originZ, (chunkZ + 19)) << Direction.NORTH;

		return planes;
	}

	public static int lessThan(int a, int b) {
		return (a - b) >>> 31;
	}

	public static int greaterThan(int a, int b) {
		return (b - a) >>> 31;
	}

	private static void exploreNodes(BFSQueue queue, SectionRender fatherNode, int directions, int activeFrame) {
		if (directions == 0) {
			return;
		}

		queue.verifyCapacity(Direction.COUNT);

		if (Direction.hasSet(directions, Direction.DOWN)) {
			visitNode(queue, fatherNode.adjacentDown, activeFrame);
		}

		if (Direction.hasSet(directions, Direction.UP)) {
			visitNode(queue, fatherNode.adjacentUp, activeFrame);
		}

		if (Direction.hasSet(directions, Direction.NORTH)) {
			visitNode(queue, fatherNode.adjacentNorth, activeFrame);
		}

		if (Direction.hasSet(directions, Direction.SOUTH)) {
			visitNode(queue, fatherNode.adjacentSouth, activeFrame);
		}

		if (Direction.hasSet(directions, Direction.WEST)) {
			visitNode(queue, fatherNode.adjacentWest, activeFrame);
		}

		if (Direction.hasSet(directions, Direction.EAST)) {
			visitNode(queue, fatherNode.adjacentEast, activeFrame);
		}
	}

	private static int getOutwardDirections(int playerX, int playerY, int playerZ, SectionRender render) {
		int planes = 0;

		int chunkX = (render.blockX >> 4);
		int playerChunkX = playerX >> 4;

		planes |= ((chunkX - playerChunkX - 1) >> 31) & Direction.set(Direction.WEST);
		planes |= ((playerChunkX - chunkX - 1) >> 31) & Direction.set(Direction.EAST);

		int chunkY = (render.blockY >> 4);
		int playerChunkY = playerY >> 4;

		planes |= ((chunkY - playerChunkY - 1) >> 31) & Direction.set(Direction.DOWN);
		planes |= ((playerChunkY - chunkY - 1) >> 31) & Direction.set(Direction.UP);

		int chunkZ = (render.blockZ >> 4);
		int playerChunkZ = playerZ >> 4;

		planes |= ((chunkZ - playerChunkZ - 1) >> 31) & Direction.set(Direction.NORTH);
		planes |= ((playerChunkZ - chunkZ - 1) >> 31) & Direction.set(Direction.SOUTH);

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

	private static float withinRenderDistance(int x, int y, int z) {
		x += 8;
		y += 8;
		z += 8;

		return (x * x) + (y * y) + (z * z);
	}

	private static void visitNode(BFSQueue queue, SectionRender adj, int activeFrame) {
		int flags;

		if (adj.currentFrame != activeFrame && !SectionFlags.isEmptySolid(flags = adj.flags)) {
			adj.currentFrame = activeFrame;

			if (UpdateQueue.hasSpace() && SectionFlags.isDirty(flags)) {
				UpdateQueue.addToQueueUnsafe(adj);
			}

			queue.addToQueueUnsafe(adj);
		}
	}
}
