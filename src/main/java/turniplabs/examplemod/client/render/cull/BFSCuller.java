package turniplabs.examplemod.client.render.cull;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.render.terrain.RenderRegion;
import net.minecraft.core.util.helper.MathHelper;
import turniplabs.examplemod.client.render.SectionManager;
import turniplabs.examplemod.client.render.SectionRender;
import turniplabs.examplemod.client.render.region.RegionManager;
import turniplabs.examplemod.client.render.region.RegionRender;
import turniplabs.examplemod.client.util.Direction;

import java.util.List;
import java.util.Queue;

public class BFSCuller {
	private final ObjectArrayList<SectionRender> bfsQueue = new ObjectArrayList<>();

	private RegionManager regionManager;
	private Queue<SectionRender> updateList;

	private static final int MAX_UPDATE_QUEUES = 5;

	private float renderDistance;
	private int chunksUpdated;
	private int activeFrame;

	public void setRenderingLists(RegionManager regionManager, Queue<SectionRender> updateList) {
		this.regionManager = regionManager;
		this.updateList = updateList;
	}

	public void init(float renderDistance) {
		this.renderDistance = renderDistance;

		this.updateList.clear();
		this.bfsQueue.clear();

		this.chunksUpdated = 0;
		this.activeFrame++;
	}

	public void updateRenderList(Long2ReferenceOpenHashMap<SectionRender> sectionMap, float cameraX, float cameraY, float cameraZ) {
		int playerChunkX = MathHelper.floor(cameraX) >> 4;
		int playerChunkY = MathHelper.clamp(MathHelper.floor(cameraY) >> 4, 0, 15);
		int playerChunkZ = MathHelper.floor(cameraZ) >> 4;

		SectionRender spawn = sectionMap.get(SectionManager.asLong(playerChunkX, playerChunkY, playerChunkZ));

		if (spawn != null) {
			exploreNodes(this.updateList, this.bfsQueue, spawn, spawn.adjacentMask);
			this.updateList.add(spawn);

			this.queueRegionNode(spawn, spawn.region);
		}

		int bfsIndex = 0;

		while (this.bfsQueue.size() > bfsIndex) {
			SectionRender node = this.bfsQueue.get(bfsIndex++);

			float distX = node.blockX - cameraX;
			float distY = node.blockY - cameraY;
			float distZ = node.blockZ - cameraZ;

			if (!(isSectionVisible(distX, distY, distZ, renderDistance))) {
				continue;
			}

			this.queueRegionNode(node, node.region);

			int outwardDirections = getOutwardDirections(playerChunkX, playerChunkY, playerChunkZ, node);

			outwardDirections &= node.adjacentMask;
			outwardDirections &= ~node.solidFaces;

			exploreNodes(this.updateList, this.bfsQueue, node, outwardDirections);
		}
	}

	private void queueRegionNode(SectionRender section, RegionRender region) {
		if (region == null) {
			return;
		}

		if (region.currentFrame != this.activeFrame) {
			if (section.solidDraw != 0 || section.translucentDraw != 0) {
				this.regionManager.addToDrawQueue(region);
			}
			region.currentFrame = this.activeFrame;
		}

		if (section.solidDraw != 0) {
			region.addSolidDraw(section.solidDraw);
		}

		if (section.translucentDraw != 0) {
			region.addTranslucentDraw(section.translucentDraw);
		}
	}

	private void exploreNodes(Queue<SectionRender> updateQueue, ObjectArrayList<SectionRender> queue, SectionRender fatherNode, int directions) {
		if (directions == 0) {
			return;
		}

		if (Direction.hasSet(directions, Direction.DOWN)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.DOWN);
			this.visitNode(updateQueue, queue, adjacent);
		}

		if (Direction.hasSet(directions, Direction.UP)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.UP);
			this.visitNode(updateQueue, queue, adjacent);
		}

		if (Direction.hasSet(directions, Direction.NORTH)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.NORTH);
			this.visitNode(updateQueue, queue, adjacent);
		}

		if (Direction.hasSet(directions, Direction.SOUTH)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.SOUTH);
			this.visitNode(updateQueue, queue, adjacent);
		}

		if (Direction.hasSet(directions, Direction.WEST)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.WEST);
			this.visitNode(updateQueue, queue, adjacent);
		}

		if (Direction.hasSet(directions, Direction.EAST)) {
			SectionRender adjacent = fatherNode.getAdjacent(Direction.EAST);
			this.visitNode(updateQueue, queue, adjacent);
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

	private boolean isSectionVisible(float distX, float distY, float distZ, float renderDistance) {
		return withinRenderDistance(distX, distY, distZ) < renderDistance && FrustumCuller.testAab(distX, distY, distZ);
	}

	private static float withinRenderDistance(float x, float y, float z) {
		x += 8.0F;
		y += 8.0F;
		z += 8.0F;

		return (x * x) + (y * y) + (z * z);
	}

	private void visitNode(Queue<SectionRender> updateQueue, ObjectArrayList<SectionRender> queue, SectionRender adj) {
		if (adj.currentFrame != this.activeFrame && !adj.solidEmptySection) {
			if (adj.dirty && this.chunksUpdated < MAX_UPDATE_QUEUES) {
				updateQueue.add(adj);
				this.chunksUpdated++;
			}

			adj.currentFrame = this.activeFrame;
			queue.add(adj);
		}
	}
}
