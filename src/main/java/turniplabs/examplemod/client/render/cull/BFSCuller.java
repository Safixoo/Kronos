package turniplabs.examplemod.client.render.cull;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import net.minecraft.core.util.helper.MathHelper;
import turniplabs.examplemod.client.render.SectionManager;
import turniplabs.examplemod.client.render.SectionRender;
import turniplabs.examplemod.client.render.region.RegionManager;
import turniplabs.examplemod.client.render.region.RegionRender;
import turniplabs.examplemod.client.util.Direction;

import java.util.List;

public class BFSCuller {
	private final BFSQueue bfsQueue = new BFSQueue();

	private RegionManager regionManager;
	private ReferenceList<SectionRender> updateList;

	private float renderDistance;
	private int activeFrame;

	public void setRenderingLists(RegionManager regionManager, ReferenceList<SectionRender> updateList) {
		this.regionManager = regionManager;
		this.updateList = updateList;
	}

	public void init(float renderDistance) {
		this.renderDistance = renderDistance;

		this.updateList.clear();
		this.bfsQueue.clear();

		this.activeFrame++;
	}

	private static void bfsSearch(ReferenceList<SectionRender> updateList, BFSQueue bfsQueue, RegionManager regionManager,
								  float cameraX, float cameraY, float cameraZ,
								  int playerChunkX, int playerChunkY, int playerChunkZ,
								  float renderDistance, int activeFrame) {
		int bfsIndex = 0;

		SectionRender node;
		while ((node = bfsQueue.get(bfsIndex++)) != null) {
			float distX = node.blockX - cameraX;
			float distY = node.blockY - cameraY;
			float distZ = node.blockZ - cameraZ;

			if (!(isSectionVisible(distX, distY, distZ, renderDistance))) {
				continue;
			}

			queueRegionNode(node, node.region, regionManager, activeFrame);

			int outwardDirections = getOutwardDirections(playerChunkX, playerChunkY, playerChunkZ, node);

			outwardDirections &= node.adjacentMask;
			outwardDirections &= ~node.solidFaces;

			exploreNodes(updateList, bfsQueue, node, outwardDirections, activeFrame);
		}
	}

	public void updateRenderList(Long2ReferenceOpenHashMap<SectionRender> sectionMap, float cameraX, float cameraY, float cameraZ) {
		int playerChunkX = MathHelper.floor(cameraX) >> 4;
		int playerChunkY = MathHelper.clamp(MathHelper.floor(cameraY) >> 4, 0, 15);
		int playerChunkZ = MathHelper.floor(cameraZ) >> 4;

		SectionRender spawn = sectionMap.get(SectionManager.asLong(playerChunkX, playerChunkY, playerChunkZ));

		if (spawn != null) {
			exploreNodes(this.updateList, this.bfsQueue, spawn, spawn.adjacentMask, this.activeFrame);

			if (spawn.dirty) {
				this.updateList.add(spawn);
			}

			queueRegionNode(spawn, spawn.region, this.regionManager, this.activeFrame);
		}

		bfsSearch(this.updateList, this.bfsQueue, this.regionManager, cameraX, cameraY, cameraZ,
			playerChunkX, playerChunkY, playerChunkZ, this.renderDistance, this.activeFrame);
	}

	private static void queueRegionNode(SectionRender section, RegionRender region, RegionManager regionManager, int activeFrame) {
		if (region == null) {
			return;
		}

		if (region.currentFrame != activeFrame) {
			if ((section.solidDraw | section.translucentDraw) != 0) {
				regionManager.addToDrawQueue(region);
			}
			region.currentFrame = activeFrame;
		}

		if (section.solidDraw != 0) {
			region.addSolidDraw(section.solidDraw);
		}

		if (section.translucentDraw != 0) {
			region.addTranslucentDraw(section.translucentDraw);
		}
	}

	private static void exploreNodes(List<SectionRender> updateQueue, BFSQueue queue, SectionRender fatherNode, int directions, int activeFrame) {
		if (directions == 0) {
			return;
		}

		queue.verifyCapacity(6);

		if (Direction.hasSet(directions, Direction.DOWN)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.DOWN);
			visitNode(updateQueue, queue, adjacent, activeFrame);
		}

		if (Direction.hasSet(directions, Direction.UP)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.UP);
			visitNode(updateQueue, queue, adjacent, activeFrame);
		}

		if (Direction.hasSet(directions, Direction.NORTH)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.NORTH);
			visitNode(updateQueue, queue, adjacent, activeFrame);
		}

		if (Direction.hasSet(directions, Direction.SOUTH)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.SOUTH);
			visitNode(updateQueue, queue, adjacent, activeFrame);
		}

		if (Direction.hasSet(directions, Direction.WEST)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.WEST);
			visitNode(updateQueue, queue, adjacent, activeFrame);
		}

		if (Direction.hasSet(directions, Direction.EAST)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.EAST);
			visitNode(updateQueue, queue, adjacent, activeFrame);
		}
	}

	private static int getOutwardDirections(int playerChunkX, int playerChunkY, int playerChunkZ, SectionRender render) {
		int planes = 0;

		planes |= (render.blockX >> 4) <= playerChunkX ? Direction.set(Direction.WEST)  : 0;
		planes |= (render.blockX >> 4) >= playerChunkX ? Direction.set(Direction.EAST)  : 0;

		planes |= (render.blockY >> 4) <= playerChunkY ? Direction.set(Direction.DOWN)  : 0;
		planes |= (render.blockY >> 4) >= playerChunkY ? Direction.set(Direction.UP)    : 0;

		planes |= (render.blockZ >> 4) <= playerChunkZ ? Direction.set(Direction.NORTH) : 0;
		planes |= (render.blockZ >> 4) >= playerChunkZ ? Direction.set(Direction.SOUTH) : 0;

		return planes;
	}

	private static boolean isSectionVisible(float distX, float distY, float distZ, float renderDistance) {
		return withinRenderDistance(distX, distY, distZ) < renderDistance && FrustumCuller.testAab(distX, distY, distZ);
	}

	private static float withinRenderDistance(float x, float y, float z) {
		x += 8.0F;
		y += 8.0F;
		z += 8.0F;

		return (x * x) + (y * y) + (z * z);
	}

	private static void visitNode(List<SectionRender> updateQueue, BFSQueue queue, SectionRender adj, int activeFrame) {
		if (adj.currentFrame != activeFrame && !adj.solidEmptySection) {
			if (adj.dirty) {
				updateQueue.add(adj);
			}

			adj.currentFrame = activeFrame;
			queue.addToQueueUnsafe(adj);
		}
	}
}
