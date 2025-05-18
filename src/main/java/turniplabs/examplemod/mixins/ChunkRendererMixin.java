package turniplabs.examplemod.mixins;

import net.minecraft.client.render.RenderBlocks;
import net.minecraft.client.render.block.model.BlockModel;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.client.render.terrain.ChunkRenderer;
import net.minecraft.client.render.terrain.ChunkRendererLegacy;
import net.minecraft.client.render.tessellator.Tessellator;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.entity.TileEntity;
import net.minecraft.core.world.World;
import net.minecraft.core.world.chunk.ChunkCache;
import net.minecraft.core.world.chunk.ChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import turniplabs.examplemod.client.VertexWriterManager;
import turniplabs.examplemod.client.renderer.BlockRenderer;
import turniplabs.examplemod.client.renderer.gl.GlVertexBuffer;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.util.interfaces.mixin.IChunkRenderer;
import turniplabs.examplemod.client.vertex.format.DefaultVertexFormats;

import java.nio.ByteBuffer;
import java.util.List;

@Mixin(value = ChunkRendererLegacy.class, remap = false)
public abstract class ChunkRendererMixin extends ChunkRenderer implements IChunkRenderer {
	public ChunkRendererMixin(World world, List<TileEntity> globalRenderableTileEntities, int x, int y, int z, int size, int lists) {
		super(world, globalRenderableTileEntities, x, y, z, size, lists);
	}

	private static final BlockRenderer blockRenderer = new BlockRenderer();
	private GlVertexBuffer solidBuffer;
	private GlVertexBuffer translucentBuffer;
	private int solidVertices, translucentVertices;
	private boolean emptySection;
	private boolean solidSection;

	private int solidFaces;

	/**
	 * @author Safixo
	 * @reason Faster
	 */
	@Overwrite
	public void rebuild() {
		int minX = this.posX;
		int minY = this.posY;
		int minZ = this.posZ;

		int maxX = this.posX + 16;
		int maxY = this.posY + 16;
		int maxZ = this.posZ + 16;

		updates++;

		this.empty[0] = true;
		this.empty[1] = true;

		ChunkSection chunkSection = this.world.getChunkFromBlockCoords(this.posX, this.posZ).getSection(this.posY >> 4);

		if (chunkSection.blocks == null) {
			this.solidSection = false;
			this.translucentVertices = 0;
			this.solidVertices = 0;
			this.emptySection = true;
			return;
		}

		ChunkCache chunkcache = new ChunkCache(this.world, minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1);

		RenderBlocks renderBlocks = new RenderBlocks(chunkcache);
		BlockModel.setRenderBlocks(renderBlocks);

		blockRenderer.setChunkCache(chunkcache);

		VertexWriterManager solidWriter = VertexWriterManager.SOLID;
		VertexWriterManager translucentWriter = VertexWriterManager.TRANSLUCENT;

		this.prepareWriterForTerrain(solidWriter);
		this.prepareWriterForTerrain(translucentWriter);

		this.solidFaces = 0;

		int solidBlocks = 0;
		int[] solidFaces = new int[Direction.COUNT];

		for (int y = minY; y < maxY; ++y) {
			for (int z = minZ; z < maxZ; ++z) {
				for (int x = minX; x < maxX; ++x) {
					int blockId = chunkcache.getBlockId(x, y, z);

					if (blockId == 0) {
						continue;
					}

					if (Blocks.solid[blockId]) {
						solidBlocks++;

						if (y == maxY - 1) solidFaces[Direction.UP]++;
						if (y == minY) solidFaces[Direction.DOWN]++;

						if (x == maxX - 1) solidFaces[Direction.EAST]++;
						if (x == minX) solidFaces[Direction.WEST]++;

						if (z == maxZ - 1) solidFaces[Direction.SOUTH]++;
						if (z == minZ) solidFaces[Direction.NORTH]++;
					}

					BlockModel<?> model = BlockModelDispatcher.getInstance().getDispatch(Blocks.blocksList[blockId]);
					int blockRenderPass = model.renderLayer();

					if (blockRenderPass == 0) {
						VertexWriterManager.setCurrentInstance(solidWriter);
					} else {
						VertexWriterManager.setCurrentInstance(translucentWriter);
					}

					this.renderBlock(Tessellator.instance, renderBlocks, model, x, y, z);
					//renderBlockModel(model, x, y, z);
				}
			}
		}

		for (int dir = 0; dir < Direction.COUNT; dir++) {
			if (solidFaces[dir] == 256) {
				this.solidFaces |= 1 << dir;
			}
		}

		this.solidSection = solidBlocks == 4096 && solidWriter.getVertices() == 0;

		this.solidVertices = solidWriter.getVertices();
		if (solidWriter.getVertices() != 0) {
			fillSolidBuffer(solidWriter.getVertexData(), solidWriter.getVertices());
			this.empty[0] = false;
		}

		this.translucentVertices = translucentWriter.getVertices();
		if (translucentWriter.getVertices() != 0) {
			fillTranslucentBuffer(translucentWriter.getVertexData(), translucentWriter.getVertices());
			this.empty[1] = false;
		}

		this.emptySection = translucentWriter.getVertices() == 0 && solidWriter.getVertices() == 0;

		translucentWriter.stopDrawing();
		solidWriter.stopDrawing();

		this.compiled = true;
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

	@Override
	public int getSolidFaces() {
		return this.solidFaces;
	}

	private void prepareWriterForTerrain(VertexWriterManager writerManager) {
		writerManager.startDrawing();
		writerManager.setVertexFormat(DefaultVertexFormats.TERRAIN_FORMAT);
		writerManager.setPos(-this.posX, -this.posY, -this.posZ);
	}

	private static void renderBlockModel(BlockModel<?> model, int x, int y, int z) {
		blockRenderer.renderStandardBlock(model, model.block.getBoundsRaw(), x, y, z);

//		if (model.hasOverbright()) {
//			//blockRenderer.renderStandardBlock(model, model.block.getBoundsRaw(), x, y, z);
//		}
	}

	@Override
	public GlVertexBuffer solidBuffer() {
		return this.solidBuffer;
	}

	@Override
	public GlVertexBuffer translucentBuffer() {
		return this.translucentBuffer;
	}

	@Override
	public int solidVertices() {
		return this.solidVertices;
	}

	@Override
	public boolean emptySection() {
		return this.emptySection;
	}


	@Override
	public int translucentVertices() {
		return this.translucentVertices;
	}

	@Override
	public boolean solidSection() {
		return this.solidSection && this.compiled;
	}

	@Override
	public boolean isDirty() {
		return this.dirty;
	}

	@Override
	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	@Override
	public void queueRebuild() {
		this.rebuild();
	}
}





