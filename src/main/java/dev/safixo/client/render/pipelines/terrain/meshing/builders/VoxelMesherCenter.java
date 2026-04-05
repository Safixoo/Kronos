package dev.safixo.client.render.pipelines.terrain.meshing.builders;

import dev.safixo.client.render.pipelines.terrain.meshing.data.FacingRender;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.render.pipelines.terrain.meshing.model.ModelHelper;
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

			VertexWriter.setCurrentInstance(VertexWriter.SOLID[dir]);
			Icon tex = block.getBlockTexture(cache, x, y, z, dir);

			int blockColor;
			int overlayColor;

			if (modelColor != 0xFFFFFF && blockId != BLOCK_GRASS_ID || dir == UP) {
				blockColor = ColorBGRManager.multiplyColor(modelColor, SHADE_FULL_FACTOR[dir]);
				overlayColor = blockColor;
			} else {
				blockColor = SHADE_FULL_COLOR[dir];
				overlayColor = tex == SIDE_GRASS_NON_OVERLAY ? ColorBGRManager.multiplyColorByColor(modelColor, blockColor) : blockColor;
			}

			final float[] uvs = TEX_UVS;

			uvs[0] = tex.getMinU();
			uvs[1] = tex.getMinV();
			uvs[2] = tex.getMaxU();
			uvs[3] = tex.getMaxV();

			FacingRender render = FACE_RENDER[dir];

			if (ambient) {
				renderFace(render, tex, cache, x, y, z, blockColor, overlayColor);
			} else {
				renderFaceNoSmooth(render, dir, cache, x, y, z, blockColor);
			}
		}
	}

	public static void renderFace(FacingRender face, Icon tex, SectionCache cache, int x, int y, int z, int blockColor, int overlayColor) {
		int p1X = face.aoCornerX0;
		int p1Y = face.aoCornerY0;
		int p1Z = face.aoCornerZ0;

		int p2X = face.aoCornerX1;
		int p2Y = face.aoCornerY1;
		int p2Z = face.aoCornerZ1;

		int dirX = x + face.dirX;
		int dirY = y + face.dirY;
		int dirZ = z + face.dirZ;

		int posZ = getBlockCached(dirX + p2X, dirY + p2Y, dirZ + p2Z);
		int negZ = getBlockCached(dirX - p2X, dirY - p2Y, dirZ - p2Z);
		int posX = getBlockCached(dirX + p1X, dirY + p1Y, dirZ + p1Z);
		int negX = getBlockCached(dirX - p1X, dirY - p1Y, dirZ - p1Z);

		int p12X = p1X + p2X;
		int p12Y = p1Y + p2Y;
		int p12Z = p1Z + p2Z;

		int pd12X = p1X - p2X;
		int pd12Y = p1Y - p2Y;
		int pd12Z = p1Z - p2Z;

		int cornerPP = getBlockCacheLazily(dirX + p12X, dirY + p12Y, dirZ + p12Z);
		int cornerPN = getBlockCacheLazily(dirX + pd12X, dirY + pd12Y, dirZ + pd12Z);

		int lightPP = ModelHelper.fullFace(posZ | posX) == 0 ? ModelHelper.lightCenter(cache, dirX + p12X, dirY + p12Y, dirZ + p12Z, cornerPP) : 0;
		int lightPN = ModelHelper.fullFace(negZ | posX) == 0 ? ModelHelper.lightCenter(cache, dirX + pd12X, dirY + pd12Y, dirZ + pd12Z, cornerPN) : 0;

		int cornerNP = getBlockCacheLazily(dirX - pd12X, dirY - pd12Y, dirZ - pd12Z);
		int cornerNN = getBlockCacheLazily(dirX - p12X, dirY - p12Y, dirZ - p12Z);

		int lightNP = ModelHelper.fullFace(posZ | negX) == 0 ? ModelHelper.lightCenter(cache, dirX - pd12X, dirY - pd12Y, dirZ - pd12Z, cornerNP) : 0;
		int lightNN = ModelHelper.fullFace(negZ | negX) == 0 ? ModelHelper.lightCenter(cache, dirX - p12X, dirY - p12Y, dirZ - p12Z, cornerNN) : 0;

		int ao0 = ModelHelper.ao(posZ, posX, cornerPP);
		int ao1 = ModelHelper.ao(negZ, posX, cornerPN);
		int ao2 = ModelHelper.ao(negZ, negX, cornerNN);
		int ao3 = ModelHelper.ao(posZ, negX, cornerNP);

		int lightMap = cache.getLightCenter(dirX, dirY, dirZ, 0);

		int lightPZ = ModelHelper.light(posZ);
		int lightPX = ModelHelper.light(posX);

		int lightNZ = ModelHelper.light(negZ);
		int lightNX = ModelHelper.light(negX);

		int light0 = ModelHelper.avg(ModelHelper.avg(lightPP, lightMap), ModelHelper.avg(lightPZ, lightPX)); // 0 vertex
		int light1 = ModelHelper.avg(ModelHelper.avg(lightPN, lightMap), ModelHelper.avg(lightPX, lightNZ)); // 1 vertex
		int light2 = ModelHelper.avg(ModelHelper.avg(lightNN, lightMap), ModelHelper.avg(lightNZ, lightNX)); // 2 vertex
		int light3 = ModelHelper.avg(ModelHelper.avg(lightNP, lightMap), ModelHelper.avg(lightNX, lightPZ)); // 3 vertex

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

		VertexWriter writer = VertexWriter.getCurrentInstance();
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
			texUv = OVERLAY_UVS;
			color0 = ColorBGRManager.multiplyColor(overlayColor, ao0);
			color1 = ColorBGRManager.multiplyColor(overlayColor, ao1);
			color2 = ColorBGRManager.multiplyColor(overlayColor, ao2);
			color3 = ColorBGRManager.multiplyColor(overlayColor, ao3);

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

	public static int getBlockCached(int x, int y, int z) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);
		int solidBlock = SectionCache.VISITED_CENTER_BLOCKS[blockIndex];

		if (solidBlock == 1) {
			return solidBlock;
		}

		int skyLight = SectionCache.getNibble(SectionCache.CENTER_SKYLIGHT, blockIndex);
		int blockLight = SectionCache.getNibble(SectionCache.CENTER_BLOCKLIGHT, blockIndex);

		return MathExt.getLightmapCoord(skyLight, blockLight) << 4;
	}

	public static int getBlockCacheLazily(int x, int y, int z) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);
		int solidBlock = SectionCache.VISITED_CENTER_BLOCKS[blockIndex];

		if (solidBlock == 1) {
			return solidBlock;
		}

		return ~1;
	}
}
