package turniplabs.examplemod.client.render.cull;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.core.util.helper.MathHelper;
import turniplabs.examplemod.client.render.SectionManager;
import turniplabs.examplemod.client.render.SectionRender;
import turniplabs.examplemod.client.render.region.RegionManager;
import turniplabs.examplemod.client.render.region.RegionRender;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.util.Mth;

import static org.joml.Math.fma;

public class BFSCuller {
	private final BFSQueue bfsQueue = new BFSQueue();
	private RegionManager regionManager;

	private float fogEnd;
	private int activeFrame;

	public void setRenderingLists(RegionManager regionManager) {
		this.regionManager = regionManager;
	}

	public void init(int cameraX, int cameraZ, float fogEnd, int renderDistance) {
		this.fogEnd = fogEnd;

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

	private static void bfsSearch(BFSQueue bfsQueue, RegionManager regionManager,
								  float cameraX, float cameraY, float cameraZ,
								  int playerChunkX, int playerChunkY, int playerChunkZ,
								  float renderDistance, int activeFrame) {
		int bfsIndex = 0;

		SectionRender node;
		while ((node = bfsQueue.get(bfsIndex++)) != null) {
			float distX = node.blockX - cameraX;
			float distY = node.blockY - cameraY;
			float distZ = node.blockZ - cameraZ;
			float distance = withinRenderDistance(distX, distY, distZ);

			if (distance >= renderDistance || !FrustumCuller.testAab(distX, distY, distZ)) {
				continue;
			}

			BFSVisArray.setVisible(node.blockX >> 4, node.blockY >> 4, node.blockZ >> 4);

			if (distance >= Mth.square(128.0f) && node.blockY < cameraY && node.solidDrawMask != 0) {
				if (!visibleByRayCast(node.blockX + 8, node.blockY + 8, node.blockZ + 8, (int) cameraX, (int) cameraY, (int) cameraZ)) {
					continue;
				}
			}

			int outwardDirections = getOutwardDirections(playerChunkX, playerChunkY, playerChunkZ, node);

			queueRegionNode(node, node.region, regionManager, activeFrame, playerChunkX, playerChunkY, playerChunkZ);

			outwardDirections &= node.adjacentMask;
			outwardDirections &= ~node.solidFaces;

			exploreNodes(bfsQueue, node, outwardDirections, activeFrame);
		}
	}

	public static int sumUpAllDirections(int mask) {
		return (mask & 1) + ((mask >> 1) & 1) + ((mask >> 2) & 1) + ((mask >> 3) & 1) + ((mask >> 4) & 1) + ((mask >> 5) & 1);
	}

	public void updateRenderList(Long2ReferenceOpenHashMap<SectionRender> sectionMap, float cameraX, float cameraY, float cameraZ) {
		int playerX = MathHelper.floor(cameraX);
		int playerY = MathHelper.clamp(MathHelper.floor(cameraY), 0, 255);
		int playerZ = MathHelper.floor(cameraZ);

		SectionRender spawn = sectionMap.get(SectionManager.asLong(playerX >> 4, playerY >> 4, playerZ >> 4));

		if (spawn != null) {
			exploreNodes(this.bfsQueue, spawn, spawn.adjacentMask, this.activeFrame);

			if (spawn.dirty) {
				UpdateQueue.addToQueueUnsafe(spawn);
			}

			queueRegionNode(spawn, spawn.region, this.regionManager, this.activeFrame, playerX, playerY, playerZ);
		}

		bfsSearch(this.bfsQueue, this.regionManager, cameraX, cameraY, cameraZ,
			playerX, playerY, playerZ, this.fogEnd, this.activeFrame);
	}

	private static void queueRegionNode(SectionRender section, RegionRender region, RegionManager regionManager, int activeFrame, int playerX, int playerY, int playerZ) {
		if (region == null) {
			return;
		}

		if (region.currentFrame != activeFrame) {
			if ((section.solidDrawMask | section.translucentDrawData) != 0) {
				regionManager.addToDrawQueue(region);
			}
			region.currentFrame = activeFrame;
		}

		if (section.solidDrawMask != 0) {
			int visibleFaces = getVisibleFaces(playerX, playerY, playerZ, section.blockX, section.blockY, section.blockZ) & section.solidDrawMask;
			SectionManager.getCurrentInstance().drawnSolidRenderers++;

			for (int dir = 0; dir <= Direction.COUNT; dir++) {
				if ((visibleFaces & (1 << dir)) != 0) {
					region.addSolidDraw(section.solidDrawFaces[dir]);
				}
			}
		}

		if (section.translucentDrawData != 0) {
			region.addTranslucentDraw(section.translucentDrawData);
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
			SectionRender adjacent = fatherNode.getAdjacent(Direction.DOWN);
			visitNode(queue, adjacent, activeFrame);
		}

		if (Direction.hasSet(directions, Direction.UP)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.UP);
			visitNode(queue, adjacent, activeFrame);
		}

		if (Direction.hasSet(directions, Direction.NORTH)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.NORTH);
			visitNode(queue, adjacent, activeFrame);
		}

		if (Direction.hasSet(directions, Direction.SOUTH)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.SOUTH);
			visitNode(queue, adjacent, activeFrame);
		}

		if (Direction.hasSet(directions, Direction.WEST)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.WEST);
			visitNode(queue, adjacent, activeFrame);
		}

		if (Direction.hasSet(directions, Direction.EAST)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.EAST);
			visitNode(queue, adjacent, activeFrame);
		}
	}

	private static int getOutwardDirections(int playerChunkX, int playerChunkY, int playerChunkZ, SectionRender render) {
		int planes = 0;

		planes |= (render.blockX >> 4) <= (playerChunkX >> 4) ? Direction.set(Direction.WEST)  : 0;
		planes |= (render.blockX >> 4) >= (playerChunkX >> 4) ? Direction.set(Direction.EAST)  : 0;

		planes |= (render.blockY >> 4) <= (playerChunkY >> 4) ? Direction.set(Direction.DOWN)  : 0;
		planes |= (render.blockY >> 4) >= (playerChunkY >> 4) ? Direction.set(Direction.UP)    : 0;

		planes |= (render.blockZ >> 4) <= (playerChunkZ >> 4) ? Direction.set(Direction.NORTH) : 0;
		planes |= (render.blockZ >> 4) >= (playerChunkZ >> 4) ? Direction.set(Direction.SOUTH) : 0;

		return planes;
	}

	private static boolean isSectionVisible(float distX, float distY, float distZ, float renderDistance) {
		return withinRenderDistance(distX, distY, distZ) < renderDistance && FrustumCuller.testAab(distX, distY, distZ);
	}

	private static boolean visibleByRayCast(int x1, int y1, int z1, int x2, int y2, int z2) {
		int dX = x2 - x1;
		int dY = y2 - y1;
		int dZ = z2 - z1;

		int incX = sign(dX);
		int incY = sign(dY);
		int incZ = sign(dZ);

		int lenX = Math.abs(dX);
		int lenY = Math.abs(dY);
		int lenZ = Math.abs(dZ);

		int sumX = lenX << 4;
		int sumY = lenY << 4;
		int sumZ = lenZ << 4;

		int valid = 0;

		if (lenX >= lenY && lenX >= lenZ) {
			int errY = sumY - lenX;
			int errZ = sumZ - lenX;

			for (int i = 0; i < lenX; i++) {
				x1 += incX;

				if (errY > 0) {
					y1 += incY;
					errY -= sumX;
				}
				if (errZ > 0) {
					z1 += incZ;
					errZ -= sumX;
				}

				errY += sumY;
				errZ += sumZ;

				if (!BFSVisArray.getVisible(x1 >> 4, y1 >> 4, z1 >> 4)) {
					return false;
				} else {
					if (++valid > 8) {
						break;
					}
				}
			}
		} else if (lenY >= lenX && lenY >= lenZ) {
			int errX = sumX - lenY;
			int errZ = sumZ - lenY;

			for (int i = 0; i < lenY; i++) {
				y1 += incY;

				if (errX > 0) {
					x1 += incX;
					errX -= sumY;
				}
				if (errZ > 0) {
					z1 += incZ;
					errZ -= sumY;
				}

				errX += sumX;
				errZ += sumZ;

				if (!BFSVisArray.getVisible(x1 >> 4, y1 >> 4, z1 >> 4)) {
					return false;
				} else {
					if (++valid > 8) {
						break;
					}
				}
			}
		} else {
			int errY = sumY - lenZ;
			int errX = sumX - lenZ;

			for (int i = 0; i < lenZ; i++) {
				z1 += incZ;

				if (errY > 0) {
					y1 += incY;
					errY -= sumZ;
				}
				if (errX > 0) {
					x1 += incX;
					errX -= sumZ;
				}

				errY += sumY;
				errX += sumX;

				if (!BFSVisArray.getVisible(x1 >> 4, y1 >> 4, z1 >> 4)) {
					return false;
				} else {
					if (++valid > 8) {
						break;
					}
				}
			}
		}

		return true;
	}

	private static int sign(int num) {
		return (num >> 31) | 1;
	}

	private static float withinRenderDistance(float x, float y, float z) {
		x += 8.0F;
		y += 8.0F;
		z += 8.0F;

		return fma(x, x, fma(y, y, z * z));
	}

	private static void visitNode(BFSQueue queue, SectionRender adj, int activeFrame) {
		if (adj.currentFrame != activeFrame && !adj.solidEmptySection) {
			if (UpdateQueue.hasSpace() && adj.dirty) {
				UpdateQueue.addToQueueUnsafe(adj);
			}

			adj.currentFrame = activeFrame;
			queue.addToQueueUnsafe(adj);
		}
	}
}
