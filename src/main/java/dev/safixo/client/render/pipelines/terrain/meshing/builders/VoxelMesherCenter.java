package dev.safixo.client.render.pipelines.terrain.meshing.builders;

import dev.safixo.client.render.pipelines.terrain.meshing.data.FacingRender;
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

		int modelColor = ColorBGRManager.rgbToBgr(MODEL_COLORIZER.getColor(cache, x, y, z, block));

		for (int dir = 0; dir < Direction.COUNT; dir++) {
			if ((drawSet & (1 << dir)) == 0) {
				continue;
			}

			Icon tex = block.getBlockTexture(cache, x, y, z, dir);

			int blockColor = SHADE_FULL_COLOR[dir];
			int overlayColor = blockColor;

			if (modelColor != 0xFFFFFF) {
				overlayColor = ColorBGRManager.multiplyColor(modelColor, SHADE_FULL_FACTOR[dir]);

				if (blockId != BLOCK_GRASS_ID || dir == UP) {
					blockColor = overlayColor;
				}
			}

			final float[] uvs = TEX_UVS;
			uvs[0] = tex.getMinU();
			uvs[1] = tex.getMinV();
			uvs[2] = tex.getMaxU();
			uvs[3] = tex.getMaxV();

			VertexWriter writer = VertexWriter.SOLID[dir];
			FacingRender render = FACE_RENDER[dir];

			if (ambient) {
				renderFace(writer, render, tex, cache, x, y, z, blockColor, overlayColor);
			} else {
				renderFaceNoSmooth(render, dir, cache, x, y, z, blockColor);
			}
		}
	}

	public static void renderFace(VertexWriter writer, FacingRender face, Icon tex, SectionCache cache, int x, int y, int z, int blockColor, int overlayColor) {
		int p1 = face.aoCorner0Packed;
		int p2 = face.aoCorner1Packed;

		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15) + face.dirPacked;

		int posZ = getBlockCached(blockIndex + p2);
		int negZ = getBlockCached(blockIndex - p2);
		int posX = getBlockCached(blockIndex + p1);
		int negX = getBlockCached(blockIndex - p1);

		int p12 = p1 + p2;
		int pd12 = p1 - p2;

		int cornerPP = isFullVoxel(blockIndex + p12);
		int cornerPN = isFullVoxel(blockIndex + pd12);

		int lightMap = cache.getLightCenter(blockIndex);

		int lightPP = fullFace((posZ | posX) & ~cornerPP) == 0 ? (lightMap + cache.getLightCenter(blockIndex + p12)) >> 1 : lightMap;
		int lightPN = fullFace((negZ | posX) & ~cornerPN) == 0 ? (lightMap + cache.getLightCenter(blockIndex + pd12)) >> 1 : lightMap;

		int cornerNP = isFullVoxel(blockIndex - pd12);
		int cornerNN = isFullVoxel(blockIndex - p12);

		int lightNP = fullFace((posZ | negX) & ~cornerNP) == 0 ? (lightMap + cache.getLightCenter(blockIndex - pd12)) >> 1 : lightMap;
		int lightNN = fullFace((negZ | negX) & ~cornerNN) == 0 ? (lightMap + cache.getLightCenter(blockIndex - p12)) >> 1 : lightMap;

		int ao0 = ao(posZ, posX, cornerPP);
		int ao1 = ao(negZ, posX, cornerPN);
		int ao2 = ao(negZ, negX, cornerNN);
		int ao3 = ao(posZ, negX, cornerNP);

		int lightPZ = extractLight(posZ);
		int lightPX = extractLight(posX);

		int lightNZ = extractLight(negZ);
		int lightNX = extractLight(negX);

		int light0 = avg(lightPP, avg(lightPZ, lightPX)); // 0 vertex
		int light1 = avg(lightPN, avg(lightPX, lightNZ)); // 1 vertex
		int light2 = avg(lightNN, avg(lightNZ, lightNX)); // 2 vertex
		int light3 = avg(lightNP, avg(lightNX, lightPZ)); // 3 vertex

		int uv0 = face.uvData[0];
		int uv1 = face.uvData[1];
		int uv2 = face.uvData[2];
		int uv3 = face.uvData[3];

		int color0 = ColorBGRManager.multiplyColor(blockColor, ao0);
		int color1 = ColorBGRManager.multiplyColor(blockColor, ao1);
		int color2 = ColorBGRManager.multiplyColor(blockColor, ao2);
		int color3 = ColorBGRManager.multiplyColor(blockColor, ao3);

		x &= RegionRender.BLOCK_BITS_X;
		y &= RegionRender.BLOCK_BITS_Y;
		z &= RegionRender.BLOCK_BITS_Z;

		writer.ensureCapacity(TerrainFormat.STRIDE * 4);

		boolean flip = ao0 > ao3 || ao2 > ao1;
		float[] texUv = TEX_UVS;

		if (flip) {
			addVertex(writer, face, 0 * 12, x, y, z, texUv[uv0 & 0xFF], texUv[uv0 >>> 8], color0, light0);
			addVertex(writer, face, 1 * 12, x, y, z, texUv[uv1 & 0xFF], texUv[uv1 >>> 8], color1, light1);
			addVertex(writer, face, 2 * 12, x, y, z, texUv[uv2 & 0xFF], texUv[uv2 >>> 8], color2, light2);
			addVertex(writer, face, 3 * 12, x, y, z, texUv[uv3 & 0xFF], texUv[uv3 >>> 8], color3, light3);
		} else {
			addVertex(writer, face, 3 * 12, x, y, z, texUv[uv3 & 0xFF], texUv[uv3 >>> 8], color3, light3);
			addVertex(writer, face, 0 * 12, x, y, z, texUv[uv0 & 0xFF], texUv[uv0 >>> 8], color0, light0);
			addVertex(writer, face, 1 * 12, x, y, z, texUv[uv1 & 0xFF], texUv[uv1 >>> 8], color1, light1);
			addVertex(writer, face, 2 * 12, x, y, z, texUv[uv2 & 0xFF], texUv[uv2 >>> 8], color2, light2);
		}

		if (tex == SIDE_GRASS_NON_OVERLAY) {
			color0 = ColorBGRManager.multiplyColor(overlayColor, ao0);
			color1 = ColorBGRManager.multiplyColor(overlayColor, ao1);
			color2 = ColorBGRManager.multiplyColor(overlayColor, ao2);
			color3 = ColorBGRManager.multiplyColor(overlayColor, ao3);
			texUv = OVERLAY_UVS;

			if (flip) {
				addVertex(writer, face, 0 * 12, x, y, z, texUv[uv0 & 0xFF], texUv[uv0 >>> 8], color0, light0);
				addVertex(writer, face, 1 * 12, x, y, z, texUv[uv1 & 0xFF], texUv[uv1 >>> 8], color1, light1);
				addVertex(writer, face, 2 * 12, x, y, z, texUv[uv2 & 0xFF], texUv[uv2 >>> 8], color2, light2);
				addVertex(writer, face, 3 * 12, x, y, z, texUv[uv3 & 0xFF], texUv[uv3 >>> 8], color3, light3);
			} else {
				addVertex(writer, face, 3 * 12, x, y, z, texUv[uv3 & 0xFF], texUv[uv3 >>> 8], color3, light3);
				addVertex(writer, face, 0 * 12, x, y, z, texUv[uv0 & 0xFF], texUv[uv0 >>> 8], color0, light0);
				addVertex(writer, face, 1 * 12, x, y, z, texUv[uv1 & 0xFF], texUv[uv1 >>> 8], color1, light1);
				addVertex(writer, face, 2 * 12, x, y, z, texUv[uv2 & 0xFF], texUv[uv2 >>> 8], color2, light2);
			}
		}
	}

	private static final double SOLID_OCC_FACTOR = 0.2;

	private static final int EMPTY_BLOCK_OCC_FACTOR = 255;
	private static final int FULL_BLOCK_REDUCE = EMPTY_BLOCK_OCC_FACTOR - (int) (SOLID_OCC_FACTOR * EMPTY_BLOCK_OCC_FACTOR);

	public static int ao(int side1, int side2, int corner) {
		side1 = -fullFace(side1) & FULL_BLOCK_REDUCE;
		side2 = -fullFace(side2) & FULL_BLOCK_REDUCE;
		corner = -fullFace(corner) & FULL_BLOCK_REDUCE;

		corner |= side1 & side2;

		return EMPTY_BLOCK_OCC_FACTOR - ((side1 + side2 + corner) >> 2);
	}

	private static int avg(int a, int b) {
		if (a == 0) return b;
		if (b == 0) return a;

		return (a + b) >>> 1;
	}

	public static int getBlockCached(int blockIndex) {
		int solidBlock = SectionCache.VISITED_CENTER_BLOCKS[blockIndex];

		if (solidBlock == 1) {
			return 1;
		}

		int skyLight = SectionCache.getNibble(SectionCache.CENTER_SKYLIGHT, blockIndex);
		int blockLight = SectionCache.getNibble(SectionCache.CENTER_BLOCKLIGHT, blockIndex);

		return MathExt.getLightmapCoord(skyLight, blockLight) << 4;
	}

	private static int extractLight(int light) {
		return light >> 4;
	}

	public static int isFullVoxel(int blockIndex) {
		return SectionCache.VISITED_CENTER_BLOCKS[blockIndex];
	}
}
