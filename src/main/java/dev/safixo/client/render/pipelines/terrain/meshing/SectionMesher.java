package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.render.pipelines.terrain.SectionFlags;
import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.render.pipelines.terrain.meshing.builders.VoxelMesher;
import dev.safixo.client.render.pipelines.terrain.meshing.builders.VoxelMesherCenter;
import dev.safixo.client.render.pipelines.terrain.meshing.data.CullSetGenerator;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.render.pipelines.terrain.region.RegionAllocation;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.render.vertex.writers.TerrainFormat;
import dev.safixo.client.util.MeshDirection;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.data.PrimitivesFlags;
import dev.safixo.client.util.memory.UnsafeUtil;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.Set;

import static dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache.makeBlockIndex;
import static dev.safixo.client.util.Direction.*;

public class SectionMesher {
	private static final int AIR_ID = 0;

	public static boolean buildMesh(SectionRender section, CameraData camera, WorldManager worldManager, World world, Set<TileEntity> tileSet) {
		Chunk.isLit = false;

		SectionCache sectionCache = new SectionCache(world, section.blockX, section.blockY, section.blockZ);
		RenderBlocks renderBlocks = new RenderBlocks(sectionCache);

		VertexWriter translucentWriter = VertexWriter.TRANSLUCENT;
		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			prepareWriterForTerrain(section, VertexWriter.SOLID[dir]);
		}
		prepareWriterForTerrain(section, translucentWriter);

		if (section.tileEntities != null && !section.tileEntities.isEmpty()) {
			for (int i = 0; i < section.tileEntities.size(); i++) {
				tileSet.remove(section.tileEntities.get(i));
			}
			section.tileEntities.clear();
		}

		int cameraChunkX = camera.intX >> 4, cameraChunkY = camera.intY >> 4, cameraChunkZ = camera.intZ >> 4;
		int sectionX = section.blockX >> 4, sectionY = section.blockY >> 4, sectionZ = section.blockZ >> 4;

		boolean ambient = Minecraft.getMinecraft().gameSettings.ambientOcclusion != 0;
		boolean airEmpty = sectionCache.extendedLevelsInChunkCache();
		boolean insideSection = cameraChunkX == sectionX && cameraChunkY == sectionY && cameraChunkZ == sectionZ;

		if (!airEmpty) {
			WorldRenderer.chunksUpdated++;
			section.setFlags(SectionFlags.setCullFaces(section.flags, CullSetGenerator.floodFillSection(section, camera)));

			// +-X face
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					meshBlock(section, renderBlocks, sectionCache, 15, y, z, ambient);
				}
			}
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					meshBlock(section, renderBlocks, sectionCache, 0, y, z, ambient);
				}
			}

			// -+Y face
			for (int z = 0; z < 16; z++) {
				for (int x = 1; x < 15; x++) {
					meshBlock(section, renderBlocks, sectionCache, x, 15, z, ambient);
				}
			}
			for (int z = 0; z < 16; z++) {
				for (int x = 1; x < 15; x++) {
					meshBlock(section, renderBlocks, sectionCache, x, 0, z, ambient);
				}
			}

			// -+Z face
			for (int y = 1; y < 15; y++) {
				for (int x = 1; x < 15; x++) {
					meshBlock(section, renderBlocks, sectionCache, x, y, 15, ambient);
				}
			}
			for (int y = 1; y < 15; y++) {
				for (int x = 1; x < 15; x++) {
					meshBlock(section, renderBlocks, sectionCache, x, y, 0, ambient);
				}
			}

			if (insideSection || (SectionFlags.getCullFaces(section.flags) & 0b111_111) != 0b111_111) {
				// 14x14x14 center blocks.
				for (int y = 1; y < 15; y++) {
					for (int z = 1; z < 15; z++) {
						for (int x = 1; x < 15; x++) {
							meshBlockCenter(section, renderBlocks, sectionCache, x, y, z, ambient);
						}
					}
				}
			}
			if (section.tileEntities != null && !section.tileEntities.isEmpty()) {
				tileSet.addAll(section.tileEntities);
			}
		} else {
			section.setFlags(SectionFlags.setCullFaces(section.flags, 0b0));
		}

		int sumVertices = sumAllSolidVertices();
		int solidDrawMask = nonEmptyFacesMask();
		int translucentDrawMask = translucentWriter.getVertices() != 0 ? 1 : 0;

		int visibleFaces = RegionRender.getSectionVisibleFaces(camera.intX, camera.intY, camera.intZ, section.blockX, section.blockY, section.blockZ);
		int meshDrawOrder = RegionRender.generateMeshDrawOrderMask(solidDrawMask & visibleFaces);

		uploadMeshesToRegion(worldManager, section, translucentWriter, sumVertices, meshDrawOrder);

		byte drawMask = (byte) (solidDrawMask << 1 & 0b1_111_111_0 | translucentDrawMask);
		section.region.drawDataMask[section.regionIndex] = drawMask;

		int nonEmptyTranslucent = (translucentDrawMask << 1) & 0b10;
		int nonEmptySolid = solidDrawMask != 0 ? 0b01 : 0;

		section.setFlags(SectionFlags.setDirty(section.flags, false));
		section.setFlags(SectionFlags.setPassesNonEmpty(section.flags, nonEmptyTranslucent | nonEmptySolid));

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			VertexWriter.SOLID[dir].stopDrawing();
		}

		VertexWriter.DEFAULT_INSTANCE.stopDrawing();
		translucentWriter.stopDrawing();

		return !airEmpty;
	}

	private static void meshBlockCenter(SectionRender section, RenderBlocks renderBlocks, SectionCache cache, int x, int y, int z, boolean ambient) {
		int blockIndex = makeBlockIndex(x, y, z);
		int blockId = cache.getBlockIdCenter(blockIndex);

		if (blockId == AIR_ID) {
			return;
		}

		int blockRenderPass = PrimitivesFlags.RENDER_PASS[blockId];
		int blockX = x + section.blockX, blockY = y + section.blockY, blockZ = z + section.blockZ;

		if (cache.isVoxelFullFromCenter(blockIndex) == 1 || PrimitivesFlags.SOLID[blockId]) {
			int drawBitSet = 0;

			drawBitSet |= cache.isVoxelFullFromCenter(blockIndex + makeBlockIndex(0,1,0)) << UP;
			drawBitSet |= cache.isVoxelFullFromCenter(blockIndex - makeBlockIndex(0,1,0)) << DOWN;
			drawBitSet |= cache.isVoxelFullFromCenter(blockIndex - makeBlockIndex(0,0,1)) << NORTH;
			drawBitSet |= cache.isVoxelFullFromCenter(blockIndex + makeBlockIndex(0,0,1)) << SOUTH;
			drawBitSet |= cache.isVoxelFullFromCenter(blockIndex - makeBlockIndex(1,0,0)) << WEST;
			drawBitSet |= cache.isVoxelFullFromCenter(blockIndex + makeBlockIndex(1,0,0)) << EAST;
			drawBitSet ^= 0x3F;

			if (drawBitSet != 0b0) {
				VoxelMesherCenter.meshVoxel(Block.blocksList[blockId], cache, blockX, blockY, blockZ, ambient, drawBitSet, blockId);
			}
		} else {
			if (PrimitivesFlags.TILE_ENTITY[blockId]) {
				TileEntity tileEntity = cache.getBlockTileEntity(blockX, blockY, blockZ);

				if (TileEntityRenderer.instance.hasSpecialRenderer(tileEntity)) {
					if (section.tileEntities == null) {
						section.tileEntities = new ReferenceArrayList<>();
					}
					section.tileEntities.add(tileEntity);
				}
			}

			if (blockRenderPass != 0) {
				VertexWriter.setCurrentInstance(VertexWriter.TRANSLUCENT);
			} else {
				VertexWriter.setCurrentInstance(VertexWriter.SOLID[MeshDirection.GENERIC]);
			}

			renderBlocks.renderBlockByRenderType(Block.blocksList[blockId], blockX, blockY, blockZ);
		}
	}

	private static void meshBlock(SectionRender section, RenderBlocks renderBlocks, SectionCache cache, int x, int y, int z, boolean ambient) {
		int blockIndex = makeBlockIndex(x, y, z);
		int blockId = cache.getBlockIdCenter(blockIndex);

		if (blockId == AIR_ID) {
			return;
		}

		int blockRenderPass = PrimitivesFlags.RENDER_PASS[blockId];
		int blockX = x + section.blockX;
		int blockY = y + section.blockY;
		int blockZ = z + section.blockZ;

		if (cache.isVoxelFullFromCenter(blockIndex) == 1 || PrimitivesFlags.SOLID[blockId]) {
			int rX = x + 16;
			int rY = y + 16;
			int rZ = z + 16;
			int drawBitSet = 0;

			drawBitSet |= cache.isVoxelFullRelative(rX, rY + 1, rZ) << UP;
			drawBitSet |= cache.isVoxelFullRelative(rX, rY - 1, rZ) << DOWN;
			drawBitSet |= cache.isVoxelFullRelative(rX, rY, rZ - 1) << NORTH;
			drawBitSet |= cache.isVoxelFullRelative(rX, rY, rZ + 1) << SOUTH;
			drawBitSet |= cache.isVoxelFullRelative(rX - 1, rY, rZ) << WEST;
			drawBitSet |= cache.isVoxelFullRelative(rX + 1, rY, rZ) << EAST;
			drawBitSet ^= 0x3F;

			if (drawBitSet != 0) {
				VoxelMesher.meshVoxel(Block.blocksList[blockId], cache, blockX, blockY, blockZ, ambient, drawBitSet, blockId);
			}
		} else {
			if (PrimitivesFlags.TILE_ENTITY[blockId]) {
				TileEntity tileEntity = cache.getBlockTileEntity(blockX, blockY, blockZ);

				if (TileEntityRenderer.instance.hasSpecialRenderer(tileEntity)) {
					if (section.tileEntities == null) {
						section.tileEntities = new ReferenceArrayList<>();
					}
					section.tileEntities.add(tileEntity);
				}
			}

			if (blockRenderPass != 0) {
				VertexWriter.setCurrentInstance(VertexWriter.TRANSLUCENT);
			} else {
				VertexWriter.setCurrentInstance(VertexWriter.SOLID[MeshDirection.GENERIC]);
			}

			renderBlocks.renderBlockByRenderType(Block.blocksList[blockId], blockX, blockY, blockZ);
		}
	}

	private static void uploadMeshesToRegion(WorldManager manager, SectionRender section, VertexWriter translucentWriter, int sumVertices, int meshDrawOrder) {
		if (sumVertices > 0) {
			if (section.region == RegionRender.NULL) {
				section.region = manager.getRegion(section.blockX >> 4, section.blockY >> 4, section.blockZ >> 4);
			}

			section.region.addMeshOrderMask(section.regionIndex, meshDrawOrder);
			long[] drawData = joinAllSolidBuffers(meshDrawOrder);

			if (JOINER.getOffset() != 0) {
				section.region.addSolidMesh(section, JOINER, drawData);
			}
		}

		if (translucentWriter.getVertices() != 0) {
			if (section.region == RegionRender.NULL) {
				section.region = manager.getRegion(section.blockX >> 4, section.blockY >> 4, section.blockZ >> 4);
			}

			section.region.addTranslucentMesh(section, translucentWriter);
		}
	}

	private static int sumAllSolidVertices() {
		int sumVertices = 0;

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			sumVertices += VertexWriter.SOLID[dir].getVertices();
		}

		return sumVertices;
	}

	private static int nonEmptyFacesMask() {
		int mask = 0;

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			if (VertexWriter.SOLID[dir].getVertices() != 0) {
				mask |= 1 << dir;
			}
		}

		return mask;
	}

	private static VertexWriter JOINER;

	private static long[] joinAllSolidBuffers(int meshDrawOrder) {
		long[] drawData = new long[MeshDirection.COUNT];

		if (JOINER == null || JOINER.getWriterPtr() == UnsafeUtil.NULL) {
			JOINER = new VertexWriter(4096);
		}

		VertexWriter joiner = JOINER;
		joiner.startDrawing();

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			int realMeshDir = meshDrawOrder & 0xF;
			meshDrawOrder >>= 4;

			VertexWriter writer = VertexWriter.SOLID[realMeshDir];

			if (writer == null) {
				continue;
			}

			int writerOffset = writer.getOffset();

			drawData[realMeshDir] = RegionAllocation.packDrawData(writerOffset / TerrainFormat.STRIDE, joiner.getOffset() / TerrainFormat.STRIDE);
			joiner.ensureCapacity(writerOffset);

			UnsafeUtil.memCopy(writer.getWriterPtr(), joiner.getWriterPtr() + joiner.getOffset(), writerOffset);
			joiner.offset += writerOffset;
		}

		joiner.vertices = joiner.offset / TerrainFormat.STRIDE;
		return drawData;
	}

	private static void prepareWriterForTerrain(SectionRender section, VertexWriter writerManager) {
		writerManager.startDrawing();

		// Region translation-offset.
		writerManager.trasX = -(section.blockX & ~RegionRender.BLOCK_BITS_X);
		writerManager.trasY = -(section.blockY & ~RegionRender.BLOCK_BITS_Y);
		writerManager.trasZ = -(section.blockZ & ~RegionRender.BLOCK_BITS_Z);

		writerManager.setVertexFormat(DefaultVertexFormats.TERRAIN_FORMAT);
	}
}
