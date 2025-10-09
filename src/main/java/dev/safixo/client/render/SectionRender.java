package dev.safixo.client.render;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.WorldRenderer;
import dev.safixo.client.render.meshing.FullBlockMesher;
import dev.safixo.client.render.region.RegionRender;
import dev.safixo.client.render.util.MeshDirection;
import dev.safixo.client.render.util.data.CameraData;
import dev.safixo.client.render.vertex.VertexWriterManager;
import dev.safixo.client.render.meshing.SectionCache;
import dev.safixo.client.render.util.data.BlocksFlags;
import dev.safixo.client.render.util.Direction;
import dev.safixo.client.render.vertex.format.DefaultVertexFormats;
import net.minecraft.world.World;

import static dev.safixo.client.render.meshing.SectionCache.makeBlockIndex;
import static dev.safixo.client.render.util.Direction.*;
import static dev.safixo.client.render.util.Direction.EAST;

// Saves basic info for each section from the world, is used mostly for culling and
// meshing, rendering is almost only managed in the RegionRender in an objectless fashion.
public class SectionRender {
	// Most of the section data, flags is a bit-mask from SectionFlag encoding.
	public int currentFrame, flags = SectionFlags.setDirty(0, true);

	// Section position relative to blocks.
	public int blockX, blockY, blockZ;

	public int regionIndex;

	// Adjacent nodes that searched during BFS culling, done like this
	// to avoid array dereferences and checks, inspired from Sodium.
	public SectionRender adjacentDown, adjacentUp, adjacentNorth,
                        adjacentSouth, adjacentWest, adjacentEast;

	// Section main data structures.
	public RegionRender region = RegionRender.NULL;

	private static final int AIR_ID = 0;

	public SectionRender(int blockX, int blockY, int blockZ) {
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;

		this.regionIndex = RegionRender.regionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
	}

	public void rebuild(CameraData camera, SectionManager sectionManager, World world) {
		WorldRenderer.chunksUpdated++;

		int minX = this.blockX;
		int minY = this.blockY;
		int minZ = this.blockZ;

		int maxX = minX + 16;
		int maxY = minY + 16;
		int maxZ = minZ + 16;

		SectionCache sectionCache = new SectionCache(world, minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1);
		RenderBlocks renderBlocks = new RenderBlocks(sectionCache);

		VertexWriterManager translucentWriter = VertexWriterManager.TRANSLUCENT;

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			this.prepareWriterForTerrain(VertexWriterManager.SOLID[dir]);
		}

		this.prepareWriterForTerrain(translucentWriter);

		int[] solidFaces = new int[Direction.COUNT + 1];
		boolean ambient = Minecraft.getMinecraft().gameSettings.ambientOcclusion == 1;

		if (!sectionCache.isSectionEmpty()) {
			// 15x15x15 center blocks.
			for (int y = 1; y < 15; y++) {
				for (int z = 1; z < 15; z++) {
					for (int x = 1; x < 15; x++) {
						this.meshBlockCenter(renderBlocks, sectionCache, x, y, z, solidFaces, ambient);
					}
				}
			}

			// +-X face
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					this.meshBlock(renderBlocks, sectionCache, 15, y, z, solidFaces, ambient);
				}
			}
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					this.meshBlock(renderBlocks, sectionCache, 0, y, z, solidFaces, ambient);
				}
			}

			// -+Y face
			for (int z = 0; z < 16; z++) {
				for (int x = 1; x < 15; x++) {
					this.meshBlock(renderBlocks, sectionCache, x, 15, z, solidFaces, ambient);
				}
			}
			for (int z = 0; z < 16; z++) {
				for (int x = 1; x < 15; x++) {
					this.meshBlock(renderBlocks, sectionCache, x, 0, z, solidFaces, ambient);
				}
			}

			// -+Z face
			for (int y = 1; y < 15; y++) {
				for (int x = 1; x < 15; x++) {
					this.meshBlock(renderBlocks, sectionCache, x, y, 15, solidFaces, ambient);
				}
			}
			for (int y = 1; y < 15; y++) {
				for (int x = 1; x < 15; x++) {
					this.meshBlock(renderBlocks, sectionCache, x, y, 0, solidFaces, ambient);
				}
			}
		}

		this.processCullFaces(solidFaces);

		int sumVertices = this.sumAllSolidVertices();
		int solidDrawMask = this.nonEmptyFacesMask();
		int translucentDrawMask = translucentWriter.getVertices() != 0 ? 1 : 0;

		int visibleFaces = RegionRender.getSectionVisibleFaces(camera.intX, camera.intY, camera.intZ, this.blockX, this.blockY, this.blockZ);
		int meshDrawOrder = RegionRender.generateMeshDrawOrderMask(solidDrawMask & visibleFaces);

		this.uploadMeshesToRegion(sectionManager, translucentWriter, sumVertices, meshDrawOrder);

		byte drawMask = (byte) (solidDrawMask << 1 & 0b1_111_111_0 | translucentDrawMask);
		this.region.drawDataMask[this.regionIndex] = drawMask;

		int nonEmptyTranslucent = (translucentDrawMask << 1) & 0b10;
		int nonEmptySolid = solidDrawMask != 0 ? 0b01 : 0;

		int solidBlocks = solidFaces[COUNT];
		boolean emptySolid = solidBlocks == 4096 && sumVertices == 0;

		this.flags = SectionFlags.setDirty(this.flags, false);
		this.flags = SectionFlags.setEmptySolid(this.flags, emptySolid);
		this.flags = SectionFlags.setPassesNonEmpty(this.flags, nonEmptyTranslucent | nonEmptySolid);
		this.flags = SectionFlags.setDrawableFaces(this.flags, solidDrawMask);

		if (emptySolid) {
			this.currentFrame = Integer.MAX_VALUE;
		} else {
			this.currentFrame = Integer.MIN_VALUE;
		}

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			VertexWriterManager.SOLID[dir].stopDrawing();
		}

		translucentWriter.stopDrawing();
	}

	private void meshBlockCenter(RenderBlocks renderBlocks, SectionCache cache, int x, int y, int z, int[] solidBlocks, boolean ambient) {
		int blockId = cache.getBlockIdCenter(x, y, z);

		if (blockId <= AIR_ID) {
			return;
		}

		Block block = Block.blocksList[blockId];
		int blockRenderPass = block.getRenderBlockPass();

		int blockX = x + this.blockX, blockY = y + this.blockY, blockZ = z + this.blockZ;

		if (BlocksFlags.SOLID_LIGHT_MASK[blockId] != 0 || block instanceof BlockLeavesBase) {
			solidBlocks[Direction.COUNT]++;

			int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);
			int drawBitSet = 0;

			drawBitSet |= cache.isBlockOpaqueCubeCenter(blockIndex + makeBlockIndex(0,1,0)) << UP;
			drawBitSet |= cache.isBlockOpaqueCubeCenter(blockIndex - makeBlockIndex(0,1,0)) << DOWN;
			drawBitSet |= cache.isBlockOpaqueCubeCenter(blockIndex - makeBlockIndex(0,0,1)) << NORTH;
			drawBitSet |= cache.isBlockOpaqueCubeCenter(blockIndex + makeBlockIndex(0,0,1)) << SOUTH;
			drawBitSet |= cache.isBlockOpaqueCubeCenter(blockIndex - makeBlockIndex(1,0,0)) << WEST;
			drawBitSet |= cache.isBlockOpaqueCubeCenter(blockIndex + makeBlockIndex(1,0,0)) << EAST;

			FullBlockMesher.renderFaces(block, cache, blockX, blockY, blockZ, ambient, ~drawBitSet, blockId);
		} else {
			if (blockRenderPass != 0) {
				VertexWriterManager.setCurrentInstance(VertexWriterManager.TRANSLUCENT);
			} else {
				VertexWriterManager.setCurrentInstance(VertexWriterManager.SOLID[MeshDirection.GENERIC]);
			}

//			renderBlocks.renderBlockByRenderType(block, blockX, blockY, blockZ);
		}
	}

	private void meshBlock(RenderBlocks renderBlocks, SectionCache cache, int x, int y, int z, int[] solidBlocks, boolean ambient) {
		int blockId = cache.getBlockIdCenter(x, y, z);

		if (blockId <= AIR_ID) {
			return;
		}

		Block block = Block.blocksList[blockId];
		int blockRenderPass = block.getRenderBlockPass();

		int blockX = x + this.blockX;
		int blockY = y + this.blockY;
		int blockZ = z + this.blockZ;

		if (BlocksFlags.SOLID_LIGHT_MASK[blockId] != 0 || block instanceof BlockLeavesBase) {
			if (y == 0 || y == 15) {
				solidBlocks[Direction.DOWN + (y & 1)]++;
			}
			if (x == 0 || x == 15) {
				solidBlocks[Direction.WEST + (x & 1)]++;
			}
			if (z == 0 || z == 15) {
				solidBlocks[Direction.NORTH + (z & 1)]++;
			}

			int rX = x + 16;
			int rY = y + 16;
			int rZ = z + 16;
			int drawBitSet = 0;

			drawBitSet |= cache.isBlockOpaqueCubeRel(rX, rY + 1, rZ) << UP;
			drawBitSet |= cache.isBlockOpaqueCubeRel(rX, rY - 1, rZ) << DOWN;
			drawBitSet |= cache.isBlockOpaqueCubeRel(rX, rY, rZ - 1) << NORTH;
			drawBitSet |= cache.isBlockOpaqueCubeRel(rX, rY, rZ + 1) << SOUTH;
			drawBitSet |= cache.isBlockOpaqueCubeRel(rX - 1, rY, rZ) << WEST;
			drawBitSet |= cache.isBlockOpaqueCubeRel(rX + 1, rY, rZ) << EAST;

			solidBlocks[Direction.COUNT]++;
			FullBlockMesher.renderFaces(block, cache, blockX, blockY, blockZ, ambient, ~drawBitSet, blockId);
		} else {
			if (blockRenderPass != 0) {
				VertexWriterManager.setCurrentInstance(VertexWriterManager.TRANSLUCENT);
			} else {
				VertexWriterManager.setCurrentInstance(VertexWriterManager.SOLID[MeshDirection.GENERIC]);
			}

//			renderBlocks.renderBlockByRenderType(block, blockX, blockY, blockZ);
		}
	}


	static long samples = 0;
	static long timePassed = 0;

	private void uploadMeshesToRegion(SectionManager sectionManager, VertexWriterManager translucentWriter, int sumVertices, int meshDrawOrder) {
		if (sumVertices > 0) {
			if (this.region == RegionRender.NULL) {
				this.region = sectionManager.getRegion(this.blockX >> 4, this.blockY >> 4, this.blockZ >> 4);
			}

			this.region.addMeshOrderMask(this.regionIndex, meshDrawOrder);

			for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
				int realMeshDir = meshDrawOrder & 0xF;
				meshDrawOrder >>= 4;

				if (VertexWriterManager.SOLID[realMeshDir].getVertices() != 0) {
					this.region.addSolidMesh(this, VertexWriterManager.SOLID[realMeshDir], realMeshDir);
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

		this.flags = SectionFlags.setAdjacentMask(this.flags, SectionFlags.getAdjacentMask(this.flags));
		this.flags = SectionFlags.setCullFaces(this.flags, solidFacesMask);
	}

	private int sumAllSolidVertices() {
		int sumVertices = 0;

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			sumVertices += VertexWriterManager.SOLID[dir].getVertices();
		}

		return sumVertices;
	}

	private int nonEmptyFacesMask() {
		int mask = 0;

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			if (VertexWriterManager.SOLID[dir].getVertices() != 0) {
				mask |= 1 << dir;
			}
		}

		return mask;
	}

	private void prepareWriterForTerrain(VertexWriterManager writerManager) {
		writerManager.startDrawing();

		// Region translation-offset.
		writerManager.trasX = -(this.blockX & ~RegionRender.BLOCK_BITS_X);
		writerManager.trasY = -(this.blockY & ~RegionRender.BLOCK_BITS_Y);
		writerManager.trasZ = -(this.blockZ & ~RegionRender.BLOCK_BITS_Z);

		writerManager.setVertexFormat(DefaultVertexFormats.TERRAIN_FORMAT);
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

	public void clearAllocations() {
		RegionRender region = this.region;
		this.region = RegionRender.NULL;

		if (region == RegionRender.NULL) {
			return;
		}

		region.deleteRenderAllocation(this);
	}
}
