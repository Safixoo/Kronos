package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.render.pipelines.terrain.meshing.builders.VoxelMesher;
import dev.safixo.client.render.pipelines.terrain.meshing.builders.VoxelMesherCenter;
import dev.safixo.client.render.pipelines.terrain.meshing.data.CullSetGenerator;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.render.pipelines.terrain.meshing.task.SectionResult;
import dev.safixo.client.render.pipelines.terrain.meshing.task.SectionTask;
import dev.safixo.client.render.pipelines.terrain.region.RegionConstants;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.MeshDirection;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.data.PrimitivesFlags;
import dev.safixo.core.hooks.AsyncBlockHook;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.tileentity.TileEntity;

import java.util.List;

import static dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache.makeBlockIndex;
import static dev.safixo.client.util.Direction.*;

public class SectionMesher {
	private final CullSetGenerator generator = new CullSetGenerator();
	private final List<TileEntity> tileEntities = new ReferenceArrayList<>();

	private static final int AIR_ID = 0;

	public SectionResult buildMesh(SectionTask task) {
		if (!AsyncBlockHook.isAsync()) {
			throw new RuntimeException("Meshing in incorrect thread!");
		}

		// Thread safe only because meshing happens in one thread.
		for (VertexWriter writer : task.getWriters()) {
			writer.startDrawing();
			writer.setVertexFormat(DefaultVertexFormats.TERRAIN_FORMAT);

			setupTranslation(task.section, writer);
		}

		CameraData camera = task.camera;
		SectionRender section = task.section;
		SectionCache sectionCache = task.cache;

		int cameraChunkX = camera.intX >> 4, cameraChunkY = camera.intY >> 4, cameraChunkZ = camera.intZ >> 4;
		int sectionX = section.blockX >> 4, sectionY = section.blockY >> 4, sectionZ = section.blockZ >> 4;

		boolean ambient = Minecraft.getMinecraft().gameSettings.ambientOcclusion != 0;

		RenderBlocks renderBlocks = new RenderBlocks(sectionCache);
		int solidSides = this.generator.floodFillSection(sectionCache, section, camera);

		// +-X face
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				meshBlock(task, renderBlocks, 15, y, z, ambient);
			}
		}
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				meshBlock(task, renderBlocks, 0, y, z, ambient);
			}
		}

		// -+Y face
		for (int z = 0; z < 16; z++) {
			for (int x = 1; x < 15; x++) {
				meshBlock(task, renderBlocks, x, 15, z, ambient);
			}
		}
		for (int z = 0; z < 16; z++) {
			for (int x = 1; x < 15; x++) {
				meshBlock(task, renderBlocks, x, 0, z, ambient);
			}
		}

		// -+Z face
		for (int y = 1; y < 15; y++) {
			for (int x = 1; x < 15; x++) {
				meshBlock(task, renderBlocks, x, y, 15, ambient);
			}
		}
		for (int y = 1; y < 15; y++) {
			for (int x = 1; x < 15; x++) {
				meshBlock(task, renderBlocks, x, y, 0, ambient);
			}
		}

		boolean insideSection = cameraChunkX == sectionX && cameraChunkY == sectionY && cameraChunkZ == sectionZ;

		if (insideSection || solidSides != 0b111_111) {
			// 14x14x14 center blocks.
			for (int y = 1; y < 15; y++) {
				for (int z = 1; z < 15; z++) {
					for (int x = 1; x < 15; x++) {
						meshBlockCenter(task, renderBlocks, x, y, z, ambient);
					}
				}
			}
		}

		SectionResult result = new SectionResult(this.getTileEntityArray(), task.getWriters(), task, solidSides);
		this.clearTileEntityList();

		return result;
	}

	private static boolean skipBlock(SectionCache cache, int blockId, int blockIndex) {
		return cache.isVoxelFullRel(blockIndex) == CullSetGenerator.NOT_VISITED && !PrimitivesFlags.SOLID[blockId];
	}

	private void meshBlockCenter(SectionTask task, RenderBlocks renderBlocks, int x, int y, int z, boolean ambient) {
		SectionRender section = task.section;
		SectionCache cache = task.cache;

		int blockIndex = makeBlockIndex(x, y, z);
		int blockId = cache.getBlockIdCenter(blockIndex);

		if (blockId == AIR_ID || skipBlock(cache, blockId, blockIndex)) {
			return;
		}

		int blockX = x + section.blockX, blockY = y + section.blockY, blockZ = z + section.blockZ;

		if (PrimitivesFlags.SOLID[blockId]) {
			int drawBitSet = 0;

			drawBitSet |= cache.isVoxelFullRel(blockIndex + makeBlockIndex(0, 1, 0)) << UP;
			drawBitSet |= cache.isVoxelFullRel(blockIndex - makeBlockIndex(0, 1, 0)) << DOWN;
			drawBitSet |= cache.isVoxelFullRel(blockIndex - makeBlockIndex(0, 0, 1)) << NORTH;
			drawBitSet |= cache.isVoxelFullRel(blockIndex + makeBlockIndex(0, 0, 1)) << SOUTH;
			drawBitSet |= cache.isVoxelFullRel(blockIndex - makeBlockIndex(1, 0, 0)) << WEST;
			drawBitSet |= cache.isVoxelFullRel(blockIndex + makeBlockIndex(1, 0, 0)) << EAST;
			drawBitSet ^= 0x3F;

			if (drawBitSet != 0b0) {
				VoxelMesherCenter.meshVoxel(task, Block.blocksList[blockId], blockX, blockY, blockZ, ambient, drawBitSet, blockId);
			}
		} else {
			if (PrimitivesFlags.TILE_ENTITY[blockId]) {
				TileEntity tileEntity = cache.getBlockTileEntity(blockX, blockY, blockZ);

				if (TileEntityRenderer.instance.hasSpecialRenderer(tileEntity)) {
					this.addTileEntity(tileEntity);
				}
			}

			VertexWriter writer;
			int blockRenderPass = PrimitivesFlags.RENDER_PASS[blockId];

			if (blockRenderPass == 1) {
				writer = task.getTranslucentWriter();
			} else {
				writer = task.getSolidWriter(MeshDirection.GENERIC);
			}

			VertexWriter.setCurrentInstance(writer);
			setupTranslation(section, writer);

			renderBlocks.renderBlockByRenderType(Block.blocksList[blockId], blockX, blockY, blockZ);
		}
	}

	private void meshBlock(SectionTask task, RenderBlocks renderBlocks, int x, int y, int z, boolean ambient) {
		SectionRender section = task.section;
		SectionCache cache = task.cache;

		int blockIndex = makeBlockIndex(x, y, z);
		int blockId = cache.getBlockIdCenter(blockIndex);

		if (blockId == AIR_ID || skipBlock(cache, blockId, blockIndex)) {
			return;
		}

		int blockX = x + section.blockX;
		int blockY = y + section.blockY;
		int blockZ = z + section.blockZ;

		if (PrimitivesFlags.SOLID[blockId]) {
			int rX = x + 16;
			int rY = y + 16;
			int rZ = z + 16;
			int drawBitSet = 0;

			drawBitSet |= cache.isVoxelFullRel(rX, rY + 1, rZ) << UP;
			drawBitSet |= cache.isVoxelFullRel(rX, rY - 1, rZ) << DOWN;
			drawBitSet |= cache.isVoxelFullRel(rX, rY, rZ - 1) << NORTH;
			drawBitSet |= cache.isVoxelFullRel(rX, rY, rZ + 1) << SOUTH;
			drawBitSet |= cache.isVoxelFullRel(rX - 1, rY, rZ) << WEST;
			drawBitSet |= cache.isVoxelFullRel(rX + 1, rY, rZ) << EAST;
			drawBitSet ^= 0x3F;

			if (drawBitSet != 0b0) {
				VoxelMesher.meshVoxel(task, Block.blocksList[blockId], blockX, blockY, blockZ, ambient, drawBitSet, blockId);
			}
		} else {
			if (PrimitivesFlags.TILE_ENTITY[blockId]) {
				TileEntity tileEntity = cache.getBlockTileEntity(blockX, blockY, blockZ);

				if (TileEntityRenderer.instance.hasSpecialRenderer(tileEntity)) {
					this.addTileEntity(tileEntity);
				}
			}

			VertexWriter writer;
			int blockRenderPass = PrimitivesFlags.RENDER_PASS[blockId];

			if (blockRenderPass == 1) {
				writer = task.getTranslucentWriter();
			} else {
				writer = task.getSolidWriter(MeshDirection.GENERIC);
			}

			VertexWriter.setCurrentInstance(writer);
			setupTranslation(section, writer);

			renderBlocks.renderBlockByRenderType(Block.blocksList[blockId], blockX, blockY, blockZ);
		}
	}

	private static void setupTranslation(SectionRender section, VertexWriter writer) {
		// Region translation-offset.
		writer.trasX = -(section.blockX & ~RegionConstants.BLOCK_BITS_X);
		writer.trasY = -(section.blockY & ~RegionConstants.BLOCK_BITS_Y);
		writer.trasZ = -(section.blockZ & ~RegionConstants.BLOCK_BITS_Z);
	}

	public void addTileEntity(TileEntity tileEntity) {
		this.tileEntities.add(tileEntity);
	}

	public TileEntity[] getTileEntityArray() {
		return this.tileEntities.isEmpty() ? null : this.tileEntities.toArray(new TileEntity[0]);
	}

	public void clearTileEntityList() {
		this.tileEntities.clear();
	}
}
