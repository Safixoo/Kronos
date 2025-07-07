package turniplabs.examplemod.client.render;

import net.minecraft.client.render.RenderBlocks;
import net.minecraft.client.render.block.model.BlockModel;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.client.render.tessellator.Tessellator;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.world.World;
import net.minecraft.core.world.chunk.ChunkCache;
import turniplabs.examplemod.client.VertexWriterManager;
import turniplabs.examplemod.client.render.gl.GlVertexBuffer;
import turniplabs.examplemod.client.render.meshing.BlockRenderer;
import turniplabs.examplemod.client.util.BlocksFlags;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.vertex.format.DefaultVertexFormats;

import java.nio.ByteBuffer;

public class SectionRender {
	public int posX, posY, posZ;

	public int solidFaces = 0;

	public int adjacentMask = 0;
	public int currentFrame;

	public final SectionRender[] adjacentSections = new SectionRender[Direction.COUNT];
	public boolean dirty, built, solidEmptySection;

	public GlVertexBuffer solidBuffer;
	public GlVertexBuffer translucentBuffer;

	public SectionRender(int posX, int posY, int posZ) {
		this.posX = posX;
		this.posY = posY;
		this.posZ = posZ;
	}

	public void rebuild(BlockRenderer blockRenderer, World world) {
		int minX = this.posX;
		int minY = this.posY;
		int minZ = this.posZ;

		int maxX = this.posX + 16;
		int maxY = this.posY + 16;
		int maxZ = this.posZ + 16;

		ChunkCache chunkcache = new ChunkCache(world, minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1);

		RenderBlocks renderBlocks = new RenderBlocks(chunkcache);
		BlockModel.setRenderBlocks(renderBlocks);

		blockRenderer.setChunkCache(chunkcache);

		VertexWriterManager solidWriter = VertexWriterManager.SOLID;
		VertexWriterManager translucentWriter = VertexWriterManager.TRANSLUCENT;

		this.prepareWriterForTerrain(solidWriter);
		this.prepareWriterForTerrain(translucentWriter);

		int solidBlocks = 0;
		int[] solidFaces = new int[Direction.COUNT];

		for (int y = minY; y < maxY; ++y) {
			for (int z = minZ; z < maxZ; ++z) {
				for (int x = minX; x < maxX; ++x) {
					int blockId = chunkcache.getBlockId(x, y, z);

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

					BlockModel<?> model = BlockModelDispatcher.getInstance().getDispatch(Blocks.getBlock(blockId));
					int blockRenderPass = model.renderLayer();

					if (blockRenderPass == 0) {
						VertexWriterManager.setCurrentInstance(solidWriter);
					} else {
						VertexWriterManager.setCurrentInstance(translucentWriter);
					}

					if (Blocks.solid[blockId]) {
						blockRenderer.renderStandardBlock(model, model.block.getBoundsRaw(), x, y, z);
					} else {
						this.renderBlock(Tessellator.instance, renderBlocks, model, x, y, z);
					}
				}
			}
		}

		for (int dir = 0; dir < Direction.COUNT; dir++) {
			if (solidFaces[dir] == 256) {
				this.solidFaces |= 1 << dir;
			}
		}

		this.solidEmptySection = solidBlocks == 4096 && solidWriter.getVertices() == 0;

		if (solidWriter.getVertices() != 0) {
			fillSolidBuffer(solidWriter.getVertexData(), solidWriter.getVertices());
		}

		if (translucentWriter.getVertices() != 0) {
			fillTranslucentBuffer(translucentWriter.getVertexData(), translucentWriter.getVertices());
		}

		translucentWriter.stopDrawing();
		solidWriter.stopDrawing();

		this.built = true;
		this.dirty = false;
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

	public void deleteRenderer() {
		if (this.solidBuffer != null) {
			this.solidBuffer.clearVertexData();
			this.solidBuffer.clear();
		}

		if (this.translucentBuffer != null) {
			this.translucentBuffer.clearVertexData();
			this.translucentBuffer.clear();
		}
	}

	public void setAdjacentNeighbor(SectionRender render, int direction) {
		if (render == null) {
			this.adjacentMask &= ~(1 << direction);
		} else {
			this.adjacentMask |= (1 << direction);
		}

		this.adjacentSections[direction] = render;
	}

	private void fillSolidBuffer(ByteBuffer vertexData, int vertices) {
		if (this.solidBuffer == null) {
			this.solidBuffer = new GlVertexBuffer(DefaultVertexFormats.TERRAIN_FORMAT);
		}
		this.solidBuffer.upload(vertexData, vertices);
	}

	private void fillTranslucentBuffer(ByteBuffer vertexData, int vertices) {
		if (this.translucentBuffer == null) {
			this.translucentBuffer = new GlVertexBuffer(DefaultVertexFormats.TERRAIN_FORMAT);
		}
		this.translucentBuffer.upload(vertexData, vertices);
	}

	public SectionRender getAdjacent(int direction) {
		return this.adjacentSections[direction];
	}

	public void clearRenderer() {
		if (this.solidBuffer != null) {
			this.solidBuffer.clearVertexData();
			this.solidBuffer.clear();

			this.solidBuffer = null;
		}

		if (this.translucentBuffer != null) {
			this.translucentBuffer.clearVertexData();
			this.translucentBuffer.clear();

			this.translucentBuffer = null;
		}
	}
}
