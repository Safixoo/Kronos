package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.render.pipelines.terrain.SectionFlags;
import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.render.pipelines.terrain.meshing.builders.VoxelMesher;
import dev.safixo.client.render.pipelines.terrain.meshing.builders.VoxelMesherCenter;
import dev.safixo.client.render.pipelines.terrain.meshing.data.CullSetGenerator;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.render.pipelines.terrain.region.RegionAllocation;
import dev.safixo.client.render.pipelines.terrain.region.RegionConstants;
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

import java.util.List;

import static dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache.makeBlockIndex;
import static dev.safixo.client.util.Direction.*;

public class SectionMesher {
	private final List<TileEntity> tileEntities = new ReferenceArrayList<>();

	private static final int AIR_ID = 0;

	public boolean buildMesh(SectionRender section, CameraData camera, WorldManager worldManager, World world) {
		Chunk.isLit = false;
		this.tileEntities.clear();

		SectionCache sectionCache = new SectionCache(world, section.blockX, section.blockY, section.blockZ);

		VertexWriter translucentWriter = VertexWriter.TRANSLUCENT;
		prepareWriterForTerrain(section, translucentWriter);

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			prepareWriterForTerrain(section, VertexWriter.SOLID[dir]);
		}

		int cameraChunkX = camera.intX >> 4, cameraChunkY = camera.intY >> 4, cameraChunkZ = camera.intZ >> 4;
		int sectionX = section.blockX >> 4, sectionY = section.blockY >> 4, sectionZ = section.blockZ >> 4;

		boolean ambient = Minecraft.getMinecraft().gameSettings.ambientOcclusion != 0;
		boolean airEmpty = sectionCache.extendedLevelsInChunkCache();
		boolean insideSection = cameraChunkX == sectionX && cameraChunkY == sectionY && cameraChunkZ == sectionZ;

		if (!airEmpty) {
			RenderBlocks renderBlocks = new RenderBlocks(sectionCache);

			WorldRenderer.chunksUpdated++;
			section.setFlags(SectionFlags.setSolidFaces(section.flags, CullSetGenerator.floodFillSection(sectionCache, section, camera)));

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

			if (insideSection || SectionFlags.getSolidFaces(section.flags) != 0b111_111) {
				// 14x14x14 center blocks.
				for (int y = 1; y < 15; y++) {
					for (int z = 1; z < 15; z++) {
						for (int x = 1; x < 15; x++) {
							meshBlockCenter(section, renderBlocks, sectionCache, x, y, z, ambient);
						}
					}
				}
			}
		} else {
			section.setFlags(SectionFlags.setSolidFaces(section.flags, 0b0));
		}

		section.setFlags(SectionFlags.setSolidFaces(section.flags, SectionFlags.getSolidFaces(section.flags) & getAdjacentMask(sectionY)));

		int sumVertices = sumAllSolidVertices();
		int solidDrawMask = nonEmptyFacesMask();
		int translucentDrawMask = translucentWriter.getVertices() != 0 ? 1 : 0;

		int visibleFaces = RegionRender.getSectionVisibleFaces(camera.intX, camera.intY, camera.intZ, section.blockX, section.blockY, section.blockZ);
		int meshDrawOrder = RegionRender.generateMeshDrawOrderMask(solidDrawMask & visibleFaces);

		uploadMeshesToRegion(worldManager, section, translucentWriter, sumVertices, meshDrawOrder);

		byte drawMask = (byte) (solidDrawMask << 1 & 0b1_111_111_0 | translucentDrawMask);
		section.region.setDrawMask(section.regionIndex, drawMask);;

		int nonEmptyTranslucent = (translucentDrawMask << 1) & 0b10;
		int nonEmptySolid = solidDrawMask != 0 ? 0b01 : 0;

		section.setFlags(SectionFlags.setDirty(section.flags, false));
		section.setFlags(SectionFlags.setPassesNonEmpty(section.flags, (nonEmptyTranslucent | nonEmptySolid) != 0 ? 1 : 0));

		this.uploadAllTileEntities(worldManager, section);
		this.clearTileEntityList();

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			VertexWriter.SOLID[dir].stopDrawing();
		}

		VertexWriter.DEFAULT_INSTANCE.stopDrawing();
		translucentWriter.stopDrawing();

		section.sendFlagsToSet();

		return !airEmpty;
	}

	private static boolean skipBlock(SectionCache cache, int blockId, int blockIndex) {
		return cache.isVoxelFullRel(blockIndex) == CullSetGenerator.NOT_VISITED && !PrimitivesFlags.SOLID[blockId];
	}

	private void meshBlockCenter(SectionRender section, RenderBlocks renderBlocks, SectionCache cache, int x, int y, int z, boolean ambient) {
		int blockIndex = makeBlockIndex(x, y, z);
		int blockId = cache.getBlockIdCenter(blockIndex);

		if (blockId == AIR_ID || skipBlock(cache, blockId, blockIndex)) {
			return;
		}

		int blockX = x + section.blockX, blockY = y + section.blockY, blockZ = z + section.blockZ;

		if (PrimitivesFlags.SOLID[blockId]) {
			int drawBitSet = 0;

			drawBitSet |= cache.isVoxelFullRel(blockIndex + makeBlockIndex(0,1,0)) << UP;
			drawBitSet |= cache.isVoxelFullRel(blockIndex - makeBlockIndex(0,1,0)) << DOWN;
			drawBitSet |= cache.isVoxelFullRel(blockIndex - makeBlockIndex(0,0,1)) << NORTH;
			drawBitSet |= cache.isVoxelFullRel(blockIndex + makeBlockIndex(0,0,1)) << SOUTH;
			drawBitSet |= cache.isVoxelFullRel(blockIndex - makeBlockIndex(1,0,0)) << WEST;
			drawBitSet |= cache.isVoxelFullRel(blockIndex + makeBlockIndex(1,0,0)) << EAST;
			drawBitSet ^= 0x3F;

			if (drawBitSet != 0b0) {
				VoxelMesherCenter.meshVoxel(Block.blocksList[blockId], cache, blockX, blockY, blockZ, ambient, drawBitSet, blockId);
			}
		} else {
			if (PrimitivesFlags.TILE_ENTITY[blockId]) {
				TileEntity tileEntity = cache.getBlockTileEntity(blockX, blockY, blockZ);

				if (TileEntityRenderer.instance.hasSpecialRenderer(tileEntity)) {
					this.addTileEntity(tileEntity);
				}
			}

			int blockRenderPass = PrimitivesFlags.RENDER_PASS[blockId];
			if (blockRenderPass == 1) {
				VertexWriter.setCurrentInstance(VertexWriter.TRANSLUCENT);
			} else {
				VertexWriter.setCurrentInstance(VertexWriter.SOLID[MeshDirection.GENERIC]);
			}

			setupTranslation(section, VertexWriter.getCurrentInstance());
			renderBlocks.renderBlockByRenderType(Block.blocksList[blockId], blockX, blockY, blockZ);
		}
	}

	private void meshBlock(SectionRender section, RenderBlocks renderBlocks, SectionCache cache, int x, int y, int z, boolean ambient) {
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
				VoxelMesher.meshVoxel(Block.blocksList[blockId], cache, blockX, blockY, blockZ, ambient, drawBitSet, blockId);
			}
		} else {
			if (PrimitivesFlags.TILE_ENTITY[blockId]) {
				TileEntity tileEntity = cache.getBlockTileEntity(blockX, blockY, blockZ);

				if (TileEntityRenderer.instance.hasSpecialRenderer(tileEntity)) {
					this.addTileEntity(tileEntity);
				}
			}

			int blockRenderPass = PrimitivesFlags.RENDER_PASS[blockId];
			if (blockRenderPass == 1) {
				VertexWriter.setCurrentInstance(VertexWriter.TRANSLUCENT);
			} else {
				VertexWriter.setCurrentInstance(VertexWriter.SOLID[MeshDirection.GENERIC]);
			}

			setupTranslation(section, VertexWriter.getCurrentInstance());
			renderBlocks.renderBlockByRenderType(Block.blocksList[blockId], blockX, blockY, blockZ);
		}
	}

	private static void uploadMeshesToRegion(WorldManager manager, SectionRender section, VertexWriter translucentWriter, int sumVertices, int meshDrawOrder) {
		if (sumVertices > 0) {
			if (section.region == RegionConstants.NULL) {
				section.region = manager.getRegion(section.blockX >> 4, section.blockY >> 4, section.blockZ >> 4);
			}

			section.region.addMeshOrderMask(section.regionIndex, meshDrawOrder);
			long[] drawData = joinAllSolidBuffers(meshDrawOrder);

			if (JOINER.getOffset() != 0) {
				section.region.addSolidMesh(section, JOINER, drawData);
			}
		}

		if (translucentWriter.getVertices() != 0) {
			if (section.region == RegionConstants.NULL) {
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

	private void uploadAllTileEntities(WorldManager manager, SectionRender section) {
		TileEntity[] tileEntities = this.getTileEntityArray();

		if (tileEntities == null) {
			if (section.region != RegionConstants.NULL) {
				section.region.getTileEntityManager().removeTileEntities(section.regionIndex);
			}
			return;
		}

		if (section.region == RegionConstants.NULL) {
			section.region = manager.getRegion(section.blockX >> 4, section.blockY >> 4, section.blockZ >> 4);
		}
		section.region.getTileEntityManager().addTileEntities(tileEntities, section.regionIndex);
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

	private static void prepareWriterForTerrain(SectionRender section, VertexWriter writer) {
		writer.startDrawing();
		writer.setVertexFormat(DefaultVertexFormats.TERRAIN_FORMAT);

		setupTranslation(section, writer);
	}

	private static void setupTranslation(SectionRender section, VertexWriter writer) {
		// Region translation-offset.
		writer.trasX = -(section.blockX & ~RegionConstants.BLOCK_BITS_X);
		writer.trasY = -(section.blockY & ~RegionConstants.BLOCK_BITS_Y);
		writer.trasZ = -(section.blockZ & ~RegionConstants.BLOCK_BITS_Z);
	}

	private static int getAdjacentMask(int sectionY) {
		int adjacentMask = 0x3F;

		if (sectionY == 0) {
			adjacentMask &= ~DOWN_BIT;
		} else if (sectionY == 15) {
			adjacentMask &= ~UP_BIT;
		}

		return adjacentMask;
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
