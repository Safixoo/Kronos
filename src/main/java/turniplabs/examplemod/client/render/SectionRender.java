package turniplabs.examplemod.client.render;

import net.minecraft.client.render.RenderBlocks;
import net.minecraft.client.render.block.color.BlockColor;
import net.minecraft.client.render.block.color.BlockColorDispatcher;
import net.minecraft.client.render.block.model.BlockModel;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.client.render.block.model.BlockModelLeaves;
import net.minecraft.client.render.terrain.ChunkRenderer;
import net.minecraft.client.render.tessellator.Tessellator;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.world.World;
import turniplabs.examplemod.client.render.region.RegionRender;
import turniplabs.examplemod.client.vertex.VertexWriterManager;
import turniplabs.examplemod.client.render.data.SectionCache;
import turniplabs.examplemod.client.render.meshing.BlockRenderer;
import turniplabs.examplemod.client.util.BlocksFlags;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.vertex.format.DefaultVertexFormats;

public class SectionRender {
	public int blockX, blockY, blockZ;
	public int flags;

	public int currentFrame;

	public long transDrawData;
	public long[] solidDrawFaces = new long[Direction.COUNT + 1];

	public SectionRender adjacentDown, adjacentUp, adjacentNorth,
                        adjacentSouth, adjacentWest, adjacentEast;

	private SectionCache sectionCache;
	public RegionRender region;

	public SectionRender(int blockX, int blockY, int blockZ) {
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;
	}

	public void rebuild(SectionManager sectionManager, BlockRenderer blockRenderer, World world) {
		ChunkRenderer.updates++;

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

		SectionCache sectionCache = new SectionCache(world, minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1);
		RenderBlocks renderBlocks = new RenderBlocks(sectionCache);
		BlockModel.setRenderBlocks(renderBlocks);
		blockRenderer.setChunkCache(sectionCache);

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

		for (int y = minY; y < maxY; ++y) {
			for (int z = minZ; z < maxZ; ++z) {
				for (int x = minX; x < maxX; ++x) {
					int blockId = sectionCache.getBlockIdMain(x, y, z);

					if (blockId == 0) {
						continue;
					}

					if (BlocksFlags.SOLID[blockId]) {
						solidBlocks++;
						if (y == maxY - 1) solidFaces[Direction.UP]++;
						if (y == minY) solidFaces[Direction.DOWN]++;

						if (x == maxX - 1) solidFaces[Direction.EAST]++;
						if (x == minX) solidFaces[Direction.WEST]++;

						if (z == maxZ - 1) solidFaces[Direction.SOUTH]++;
						if (z == minZ) solidFaces[Direction.NORTH]++;
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
						VertexWriterManager.setCurrentInstance(translucentWriter);
					}

					if (BlocksFlags.SOLID[blockId] || model instanceof BlockModelLeaves) {
						blockRenderer.renderStandardBlock(block, blockColor, model, model.block.getBoundsRaw(), x, y, z);
					} else {
						if (blockRenderPass == 0) {
							VertexWriterManager.setCurrentInstance(VertexWriterManager.SOLID[Direction.COUNT]);
						}

						this.renderBlock(Tessellator.instance, renderBlocks, model, x, y, z);
					}
				}
			}
		}

		int solidFacesMask = 0;

		for (int dir = 0; dir < Direction.COUNT; dir++) {
			if (solidFaces[dir] == 256) {
				solidFacesMask |= 1 << dir;
			}
		}

		this.flags = SectionFlags.setSolidFaces(this.flags, solidFacesMask);

		int sumVertices = 0;

		for (int dir = 0; dir <= Direction.COUNT; dir++) {
			sumVertices += VertexWriterManager.SOLID[dir].getVertices();
		}

		this.flags = SectionFlags.setEmptySolid(this.flags, solidBlocks == 4096 && sumVertices == 0);

		int solidDrawMask = 0;

		if (sumVertices != 0) {
			if (this.region == null) {
				this.flags = SectionFlags.setRegion(this.flags, true);
			 	this.region = sectionManager.getRegion(this.blockX >> 4, this.blockY >> 4, this.blockZ >> 4);
			}

			for (int dir = 0; dir <= Direction.COUNT; dir++) {
				if (VertexWriterManager.SOLID[dir].getVertices() != 0) {
					this.region.addSolidMesh(this, VertexWriterManager.SOLID[dir], dir);
					solidDrawMask |= 1 << dir;
				}
			}
		}

		this.flags = SectionFlags.setDrawableFaces(this.flags, solidDrawMask);

		if (translucentWriter.getVertices() != 0) {
			if (this.region == null) {
				this.flags = SectionFlags.setRegion(this.flags, true);
				this.region = sectionManager.getRegion(this.blockX >> 4, this.blockY >> 4, this.blockZ >> 4);
			}

			this.region.addTranslucentMesh(this, translucentWriter);
		}

		int nonEmptyTranslucent = translucentWriter.getVertices() != 0 ? 0b10 : 0;
		int nonEmptySolid = solidDrawMask != 0 ? 0b01 : 0;

		this.flags = SectionFlags.setPassesNonEmpty(this.flags, nonEmptyTranslucent | nonEmptySolid);
		translucentWriter.stopDrawing();

		for (int dir = 0; dir <= Direction.COUNT; dir++) {
			VertexWriterManager.SOLID[dir].stopDrawing();
		}

		this.flags = SectionFlags.setDirty(this.flags, false);
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
}
