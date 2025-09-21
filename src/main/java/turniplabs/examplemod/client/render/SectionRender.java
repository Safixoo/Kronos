package turniplabs.examplemod.client.render;

import net.minecraft.client.render.RenderBlocks;
import net.minecraft.client.render.block.color.BlockColor;
import net.minecraft.client.render.block.color.BlockColorDispatcher;
import net.minecraft.client.render.block.model.BlockModel;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.client.render.block.model.BlockModelGrass;
import net.minecraft.client.render.block.model.BlockModelLeaves;
import net.minecraft.client.render.terrain.ChunkRenderer;
import net.minecraft.client.render.tessellator.Tessellator;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.util.phys.AABB;
import net.minecraft.core.world.World;
import turniplabs.examplemod.client.render.meshing.FullBlockMesher;
import turniplabs.examplemod.client.render.region.RegionRender;
import turniplabs.examplemod.client.render.vertex.VertexWriterManager;
import turniplabs.examplemod.client.render.data.SectionCache;
import turniplabs.examplemod.client.util.BlocksFlags;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.render.vertex.format.DefaultVertexFormats;

// Saves basic info for each section from the world, is used mostly for culling and
// meshing, rendering is almost only managed in the RegionRender in an objectless fashion.
public class SectionRender {
	// Most of the section data, flags is a bit-mask from SectionFlag encoding.
	public int currentFrame, flags;

	// Section position relative to blocks.
	public int blockX, blockY, blockZ;

	public /* ubyte */ int regionIndex;

	// Adjacent nodes that searched during BFS culling, done like this
	// to avoid array dereferences and checks, inspired from Sodium.
	public SectionRender adjacentDown, adjacentUp, adjacentNorth,
                        adjacentSouth, adjacentWest, adjacentEast;

	// Section main data structures.
	private SectionCache sectionCache;
	public RegionRender region = RegionRender.NULL;

	public SectionRender(int blockX, int blockY, int blockZ) {
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;

		this.regionIndex = RegionRender.regionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
	}

	private static final float EPSILON = 6E-4f;
	private static final AABB GRASS = AABB.getPermanentBB(-EPSILON, 0, -EPSILON, 1 + EPSILON, 1, 1 + EPSILON);

	public void rebuild(SectionManager sectionManager, World world) {
		// Shouldn't happen, but it seems that there is a lot of state leak.
		if (!SectionFlags.isDirty(this.flags)) {
			return;
		}

		ChunkRenderer.updates++;
		BlocksFlags.processLeavesSolid();

		int minX = this.blockX;
		int minY = this.blockY;
		int minZ = this.blockZ;

		int maxX = minX + 16;
		int maxY = minY + 16;
		int maxZ = minZ + 16;

		if (this.sectionCache == null) {
			this.sectionCache = new SectionCache(world, minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1);
		} else {
			this.sectionCache.fillData(world, minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1);
		}

		SectionCache sectionCache = this.sectionCache;
		RenderBlocks renderBlocks = new RenderBlocks(sectionCache);
		BlockModel.setRenderBlocks(renderBlocks);

		VertexWriterManager translucentWriter = VertexWriterManager.TRANSLUCENT;

		for (int dir = 0; dir <= Direction.COUNT; dir++) {
			this.prepareWriterForTerrain(VertexWriterManager.SOLID[dir]);
		}

		this.prepareWriterForTerrain(translucentWriter);

		int solidBlocks = 0;
		int[] solidFaces = new int[Direction.COUNT];

		int lastBlockId = -1;
		BlockColor lastBlockColor = null;
		BlockModel<?> lastModel = null;

		long start = System.nanoTime();

		if (!sectionCache.isSectionEmpty()) {
			for (int y = minY; y < maxY; y++) {
				for (int z = minZ; z < maxZ; z++) {
					for (int x = minX; x < maxX; x++) {
						int blockId = this.sectionCache.getBlockIdCenter(x, y, z);

						if (blockId == 0) {
							continue;
						}

						BlockColor blockColor;
						BlockModel<?> blockModel;
						Block<?> block = Blocks.getBlock(blockId);

						if (lastBlockId == blockId) {
							blockModel = lastModel;
							blockColor = lastBlockColor;
						} else {
							blockModel = lastModel = BlockModelDispatcher.getInstance().getDispatch(block);
							blockColor = lastBlockColor = BlockColorDispatcher.getInstance().getDispatch(blockModel.block);
							lastBlockId = blockId;
						}

						BlockModel<?> model = blockModel;
						int blockRenderPass = model.renderLayer();

						if (blockRenderPass != 0) {
							VertexWriterManager.setCurrentInstance(VertexWriterManager.TRANSLUCENT);
						}

						if (BlocksFlags.SOLID[blockId] || model instanceof BlockModelLeaves) {
							solidBlocks++;
							if (y == maxY - 1) solidFaces[Direction.UP]++;
							if (y == minY) solidFaces[Direction.DOWN]++;

							if (x == maxX - 1) solidFaces[Direction.EAST]++;
							if (x == minX) solidFaces[Direction.WEST]++;

							if (z == maxZ - 1) solidFaces[Direction.SOUTH]++;
							if (z == minZ) solidFaces[Direction.NORTH]++;

							FullBlockMesher.renderFaces(model, blockColor, this.sectionCache, x, y, z);

							if (model instanceof BlockModelGrass) {
								VertexWriterManager.setCurrentInstance(VertexWriterManager.SOLID[Direction.COUNT]);

								BlockModelGrass.useOverlay = true;
								blockModel.renderStandardBlock(Tessellator.instance, GRASS, x, y, z);
								BlockModelGrass.useOverlay = false;
							}
						} else {
							if (blockRenderPass == 0) {
								VertexWriterManager.setCurrentInstance(VertexWriterManager.SOLID[Direction.COUNT]);
							}

							this.renderBlock(Tessellator.instance, renderBlocks, model, x, y, z);
						}
					}
				}
			}
		} else {
			this.sectionCache = null;
		}

		long end = System.nanoTime();

		samples++;
		timePassed += end - start;

		if (samples == 1600) {
			System.out.println("Time passed prom: " + ((timePassed / 1600f) / 1_000_000f) + "ms");
			samples = 0;
			timePassed = 0;
		}

//		for (int y = minY; y < maxY; y++) {
//			for (int z = minZ; z < maxZ; z++) {
//				for (int x = minX; x < maxX; x ++) {
//					solidBlocks = queueBlock(x, y, z, blockRenderer, renderBlocks, blockInfo, solidFaces, solidBlocks, false);
//				}
//			}
//		}

		this.processCullFaces(solidFaces);

		int sumVertices = this.sumAllSolidVertices();
		int solidDrawMask = this.nonEmptyFacesMask();
		int translucentDrawMask = translucentWriter.getVertices() != 0 ? 1 : 0;

		this.uploadMeshesToRegion(sectionManager, translucentWriter, sumVertices);

		byte drawMask = (byte) (solidDrawMask << 1 & 0b1_111_111_0 | translucentDrawMask);
		this.region.drawDataMask[this.regionIndex] = drawMask;

		int nonEmptyTranslucent = translucentDrawMask << 1;
		int nonEmptySolid = solidDrawMask != 0 ? 0b01 : 0;

		boolean emptySolid = solidBlocks == 4096 && sumVertices == 0;

		this.flags = SectionFlags.setDirty(this.flags, false);
		this.flags = SectionFlags.setRegion(this.flags, this.region != RegionRender.NULL);
		this.flags = SectionFlags.setEmptySolid(this.flags, emptySolid);
		this.flags = SectionFlags.setPassesNonEmpty(this.flags, nonEmptyTranslucent | nonEmptySolid);
		this.flags = SectionFlags.setDrawableFaces(this.flags, solidDrawMask);

		if (emptySolid) {
			this.currentFrame = Integer.MAX_VALUE;
		} else {
			this.currentFrame = Integer.MIN_VALUE;
		}

		for (int dir = 0; dir <= Direction.COUNT; dir++) {
			VertexWriterManager.SOLID[dir].stopDrawing();
		}

		translucentWriter.stopDrawing();
	}

	static long samples = 0;
	static long timePassed = 0;

	private int queueBlock(int x, int y, int z, RenderBlocks renderBlocks,
							BlockInfo blockInfo, int[] solidFaces, int solidBlocks) {


		return solidBlocks;
	}

	private void uploadMeshesToRegion(SectionManager sectionManager, VertexWriterManager translucentWriter, int sumVertices) {
		if (sumVertices > 0) {
			if (this.region == RegionRender.NULL) {
				this.region = sectionManager.getRegion(this.blockX >> 4, this.blockY >> 4, this.blockZ >> 4);
			}

			for (int dir = 0; dir <= Direction.COUNT; dir++) {
				if (VertexWriterManager.SOLID[dir].getVertices() != 0) {
					this.region.addSolidMesh(this, VertexWriterManager.SOLID[dir], dir);
				}
			}
		}

		if (translucentWriter.getVertices() != 0) {
			if (this.region == RegionRender.NULL) {
				this.region = sectionManager.getRegion(this.blockX >> 4, this.blockY >> 4, this.blockZ >> 4);
			}

			this.region.addTranslucentMesh(this, translucentWriter);
		}
	}

	private void processCullFaces(int[] cullFaces) {
		int solidFacesMask = 0;

		for (int dir = 0; dir < Direction.COUNT; dir++) {
			if (cullFaces[dir] == 256) {
				solidFacesMask |= 1 << dir;
			}
		}

		this.flags = SectionFlags.setAdjacentMask(this.flags, ~solidFacesMask & SectionFlags.getAdjacentMask(this.flags));
		this.flags = SectionFlags.setCullFaces(this.flags, solidFacesMask);
	}

	private int sumAllSolidVertices() {
		int sumVertices = 0;

		for (int dir = 0; dir <= Direction.COUNT; dir++) {
			sumVertices += VertexWriterManager.SOLID[dir].getVertices();
		}

		return sumVertices;
	}

	private int nonEmptyFacesMask() {
		int mask = 0;

		for (int dir = 0; dir <= Direction.COUNT; dir++) {
			if (VertexWriterManager.SOLID[dir].getVertices() != 0) {
				mask |= 1 << dir;
			}
		}

		return mask;
	}

	private void prepareWriterForTerrain(VertexWriterManager writerManager) {
		writerManager.startDrawing();
		writerManager.setVertexFormat(DefaultVertexFormats.TERRAIN_FORMAT);
	}

	// Default vanilla pipeline.
	public void renderBlock(Tessellator tessellator, RenderBlocks renderBlocks, BlockModel<?> model, int x, int y, int z) {
		// Default model,
		model.render(tessellator, x, y, z);

		// Overlay.
		if (model.hasOverbright()) {
			renderBlocks.overbright = true;
			model.render(tessellator, x, y, z);
			renderBlocks.overbright = false;
		}
	}

	public void setAdjacentNeighbor(SectionRender render, int direction) {
		int adjacentMask = SectionFlags.getAdjacentMask(this.flags);

		if (render == null) {
			adjacentMask &= ~(1 << direction);
		} else {
			adjacentMask |= (1 << direction);
		}

		this.flags = SectionFlags.setAdjacentMask(this.flags, adjacentMask);

		if (direction == Direction.DOWN) {
			this.adjacentDown = render;
		} else if (direction == Direction.UP) {
			this.adjacentUp = render;
		} else if (direction == Direction.WEST) {
			this.adjacentWest = render;
		} else if (direction == Direction.EAST) {
			this.adjacentEast = render;
		} else if (direction == Direction.NORTH) {
			this.adjacentNorth = render;
		} else {
			this.adjacentSouth = render;
		}
	}

	public SectionRender getAdjacent(int direction) {
		if (direction == Direction.DOWN) {
			return this.adjacentDown;
		} else if (direction == Direction.UP) {
			return this.adjacentUp;
		} else if (direction == Direction.WEST) {
			return this.adjacentWest;
		} else if (direction == Direction.EAST) {
			return this.adjacentEast;
		} else if (direction == Direction.NORTH) {
			return this.adjacentNorth;
		} else {
			return this.adjacentSouth;
		}
	}

	public static class BlockInfo {
		public BlockModel<?> lastModel;
		public BlockColor lastBlockColor;
		public int lastBlockId = -1;
	}
}
