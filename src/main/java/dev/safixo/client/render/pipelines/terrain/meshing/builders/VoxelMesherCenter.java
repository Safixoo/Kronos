package dev.safixo.client.render.pipelines.terrain.meshing.builders;

import dev.safixo.client.render.pipelines.terrain.meshing.data.FacingData;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.render.vertex.writers.TerrainFormat;
import dev.safixo.client.util.ColorBGRManager;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.PrimitivesFlags;
import net.minecraft.block.Block;
import net.minecraft.util.Icon;

import static dev.safixo.client.render.pipelines.terrain.meshing.builders.VoxelMesher.*;
import static dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache.makeBlockIndex;
import static dev.safixo.client.render.pipelines.terrain.meshing.model.ModelHelper.*;
import static dev.safixo.client.util.Direction.*;

public class VoxelMesherCenter  {
	public static void meshVoxel(Block block, SectionCache cache, int x, int y, int z, boolean ambient, int drawSet, int blockId) {
		if (!PrimitivesFlags.DIRECT_CULL[blockId]) {
			drawSet |= block.shouldSideBeRendered(cache, x, y - 1, z, 0) ? 1 << DOWN : 0;
			drawSet |= block.shouldSideBeRendered(cache, x, y + 1, z, 1) ? 1 << UP : 0;
			drawSet |= block.shouldSideBeRendered(cache, x, y, z - 1, 2) ? 1 << NORTH : 0;

			drawSet |= block.shouldSideBeRendered(cache, x, y, z + 1, 3) ? 1 << SOUTH : 0;
			drawSet |= block.shouldSideBeRendered(cache, x - 1, y, z, 4) ? 1 << WEST : 0;
			drawSet |= block.shouldSideBeRendered(cache, x + 1, y, z, 5) ? 1 << EAST : 0;
		}

		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);
		int modelColor = ColorBGRManager.rgbToBgr(MODEL_COLORIZER.getColor(cache, x, y, z, block));

		for (int dir = 0; dir < Direction.COUNT; dir++) {
			if ((drawSet & (1 << dir)) == 0) {
				continue;
			}

			int blockColor = SHADE_FULL_COLOR[dir];
			int overlayColor = blockColor;

			if (modelColor != 0xFFFFFF) {
				overlayColor = ColorBGRManager.multiplyColor(modelColor, SHADE_FULL_FACTOR[dir]);

				if (blockId != BLOCK_GRASS_ID || dir == UP) {
					blockColor = overlayColor;
				}
			}

			Icon tex = block.getBlockTexture(cache, x, y, z, dir);

			boolean sideGrass = tex == SIDE_GRASS_NON_OVERLAY;

			TEX_UVS[0] = TerrainFormat.deNormalizeTexCoordinate(tex.getMinU());
			TEX_UVS[1] = TerrainFormat.deNormalizeTexCoordinate(tex.getMinV());
			TEX_UVS[2] = TerrainFormat.deNormalizeTexCoordinate(tex.getMaxU());
			TEX_UVS[3] = TerrainFormat.deNormalizeTexCoordinate(tex.getMaxV());

			FacingData render = FACE_RENDER[dir];
			VertexWriter writer = VertexWriter.SOLID[dir];

			writer.ensureCapacity(TerrainFormat.STRIDE * 8);

			if (ambient) {
				renderFace(writer, render, cache, x, y, z, blockIndex, blockColor, overlayColor, sideGrass);
			} else {
				renderFaceNoSmooth(writer, render, cache, x, y, z, dir, blockColor, overlayColor, sideGrass);
			}
		}
	}

	public static void renderFace(VertexWriter writer, FacingData face, SectionCache cache, int x, int y, int z, int blockIndex, int blockColor, int overlayColor, boolean sideGrass) {
		int p1 = face.aoCorner0Packed;
		int p2 = face.aoCorner1Packed;

		blockIndex += face.dirPacked;

		int posZ = getBlockCached(blockIndex + p2);
		int negZ = getBlockCached(blockIndex - p2);
		int posX = getBlockCached(blockIndex + p1);
		int negX = getBlockCached(blockIndex - p1);

		int p12 = p1 + p2;
		int pd12 = p1 - p2;

		int cornerPP = fullFace(posZ & posX) == 0 ? getBlockCached(blockIndex + p12) : 1;
		int cornerPN = fullFace(negZ & posX) == 0 ? getBlockCached(blockIndex + pd12) : 1;
		int cornerNP = fullFace(posZ & negX) == 0 ? getBlockCached(blockIndex - pd12) : 1;
		int cornerNN = fullFace(negZ & negX) == 0 ? getBlockCached(blockIndex - p12) : 1;

		int lightMap = cache.getLightmapCenter(blockIndex);
		int light0 = avg(avgU(cornerPP, lightMap), avg(posZ, posX)); // 0 vertex
		int light1 = avg(avgU(cornerPN, lightMap), avg(posX, negZ)); // 1 vertex
		int light2 = avg(avgU(cornerNN, lightMap), avg(negZ, negX)); // 2 vertex
		int light3 = avg(avgU(cornerNP, lightMap), avg(negX, posZ)); // 3 vertex

		int ao0 = ao(posZ, posX, cornerPP);
		int ao1 = ao(negZ, posX, cornerPN);
		int ao2 = ao(negZ, negX, cornerNN);
		int ao3 = ao(posZ, negX, cornerNP);

		int color0 = ColorBGRManager.multiplyColor(blockColor, ao0);
		int color1 = ColorBGRManager.multiplyColor(blockColor, ao1);
		int color2 = ColorBGRManager.multiplyColor(blockColor, ao2);
		int color3 = ColorBGRManager.multiplyColor(blockColor, ao3);

		int uv0 = face.uv0;
		int uv1 = face.uv1;
		int uv2 = face.uv2;
		int uv3 = face.uv3;

		x &= RegionRender.BLOCK_BITS_X;
		y &= RegionRender.BLOCK_BITS_Y;
		z &= RegionRender.BLOCK_BITS_Z;

		long ptr = writer.getTotalOffset();
		long quadOffs = face.quadVert;

		boolean flip = ao0 > ao3 || ao2 > ao1;

		if (flip) {
			ptr = addVertex(ptr, quadOffs >> (3 * 12), x, y, z, uv3, color3, light3);
			ptr = addVertex(ptr, quadOffs >> (0 * 12), x, y, z, uv0, color0, light0);
			ptr = addVertex(ptr, quadOffs >> (1 * 12), x, y, z, uv1, color1, light1);
			ptr = addVertex(ptr, quadOffs >> (2 * 12), x, y, z, uv2, color2, light2);
		} else {
			ptr = addVertex(ptr, quadOffs >> (0 * 12), x, y, z, uv0, color0, light0);
			ptr = addVertex(ptr, quadOffs >> (1 * 12), x, y, z, uv1, color1, light1);
			ptr = addVertex(ptr, quadOffs >> (2 * 12), x, y, z, uv2, color2, light2);
			ptr = addVertex(ptr, quadOffs >> (3 * 12), x, y, z, uv3, color3, light3);
		}

		writer.offset += TerrainFormat.STRIDE * 4;
		writer.vertices += 4;

		if (sideGrass) {
			TEX_UVS[0] = OVERLAY_UVS[0];
			TEX_UVS[1] = OVERLAY_UVS[1];
			TEX_UVS[2] = OVERLAY_UVS[2];
			TEX_UVS[3] = OVERLAY_UVS[3];

			color0 = ColorBGRManager.multiplyColor(overlayColor, ao0);
			color1 = ColorBGRManager.multiplyColor(overlayColor, ao1);
			color2 = ColorBGRManager.multiplyColor(overlayColor, ao2);
			color3 = ColorBGRManager.multiplyColor(overlayColor, ao3);

			if (flip) {
				ptr = addVertex(ptr, quadOffs >> (3 * 12), x, y, z, uv3, color3, light3);
				ptr = addVertex(ptr, quadOffs >> (0 * 12), x, y, z, uv0, color0, light0);
				ptr = addVertex(ptr, quadOffs >> (1 * 12), x, y, z, uv1, color1, light1);
				addVertex(ptr, quadOffs >> (2 * 12), x, y, z, uv2, color2, light2);
			} else {
				ptr = addVertex(ptr, quadOffs >> (0 * 12), x, y, z, uv0, color0, light0);
				ptr = addVertex(ptr, quadOffs >> (1 * 12), x, y, z, uv1, color1, light1);
				ptr = addVertex(ptr, quadOffs >> (2 * 12), x, y, z, uv2, color2, light2);
				addVertex(ptr, quadOffs >> (3 * 12), x, y, z, uv3, color3, light3);
			}
			writer.offset += TerrainFormat.STRIDE * 4;
			writer.vertices += 4;
		}

	}

	public static int getBlockCached(int blockIndex) {
		int solidBlock = PrimitivesFlags.SOLID_LIGHT_MASK[SectionCache.CENTER_BLOCKS[blockIndex] & 0xFF];

		if (solidBlock == 1) {
			return 1;
		}

		int skyLight = SectionCache.getNibble(SectionCache.CENTER_SKYLIGHT, blockIndex);
		int blockLight = SectionCache.getNibble(SectionCache.CENTER_BLOCKLIGHT, blockIndex);

		return MathExt.getLightmapCoord(skyLight, blockLight);
	}

	public static int getBlockCached(int blockIndex, int lightMap) {
		int solidBlock = PrimitivesFlags.SOLID_LIGHT_MASK[SectionCache.CENTER_BLOCKS[blockIndex] & 0xFF];

		if (solidBlock == 1) {
			return lightMap;
		}

		int skyLight = SectionCache.getNibble(SectionCache.CENTER_SKYLIGHT, blockIndex);
		int blockLight = SectionCache.getNibble(SectionCache.CENTER_BLOCKLIGHT, blockIndex);

		return (MathExt.getLightmapCoord(skyLight, blockLight) + lightMap) >> 1;
	}

	public static int fullVoxel(int blockIndex) {
		return PrimitivesFlags.SOLID_LIGHT_MASK[SectionCache.CENTER_BLOCKS[blockIndex] & 0xFF];
	}
}
