package dev.safixo.client.render.pipelines.terrain.meshing.builders;

import dev.safixo.client.render.pipelines.terrain.meshing.task.SectionTask;
import dev.safixo.client.render.pipelines.terrain.meshing.data.FacingData;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.render.pipelines.terrain.meshing.model.ModelColorizer;
import dev.safixo.client.render.pipelines.terrain.region.RegionConstants;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.Icon;
import dev.safixo.client.util.data.PrimitivesFlags;
import dev.safixo.client.util.ColorBGRManager;
import dev.safixo.client.util.Direction;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.render.vertex.writers.TerrainFormat;
import org.joml.Vector3i;

import static dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache.makeBlockIndex;
import static dev.safixo.client.render.pipelines.terrain.meshing.model.ModelHelper.*;
import static dev.safixo.client.util.Direction.*;

public class VoxelMesher {
	public static final ModelColorizer MODEL_COLORIZER = new ModelColorizer();
	public static final int[] MAP_ID_TO_UV = new int[4 * 4];

	public static final int[] SHADE_FULL_COLOR = new int[Direction.COUNT];
	public static final int[] SHADE_FULL_FACTOR = new int[Direction.COUNT];

	public static final int[] OVERLAY_UVS = new int[4];
	protected static final int[] TEX_UVS = new int[4];

	public static Icon SIDE_GRASS_NON_OVERLAY = Block.grass.getIcon(5, 5);
	public static final float[] SIDE_LIGHT_MULTIPLIER = new float[] { 0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F };

	protected static final int BLOCK_GRASS_ID = Block.grass.blockID;
	protected static final Icon MISSING = ((TextureMap) Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture)).getAtlasSprite("missingno");

	public static void meshVoxel(SectionTask task, Block block, int x, int y, int z, boolean ambient, int drawSet, int blockId) {
		SectionCache cache = task.cache;

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

			int blockColor = SHADE_FULL_COLOR[dir];
			int overlayColor = blockColor;

			if (modelColor != 0xFFFFFF) {
				overlayColor = ColorBGRManager.multiplyColor(modelColor, SHADE_FULL_FACTOR[dir]);

				if (blockId != BLOCK_GRASS_ID || dir == UP) {
					blockColor = overlayColor;
				}
			}

			Icon tex = getIconSafe(block.getBlockTexture(cache, x, y, z, dir));
			boolean sideGrass = tex == SIDE_GRASS_NON_OVERLAY;

			TEX_UVS[0] = TerrainFormat.deNormalizeTexCoordinate(tex.getMinU());
			TEX_UVS[1] = TerrainFormat.deNormalizeTexCoordinate(tex.getMinV());
			TEX_UVS[2] = TerrainFormat.deNormalizeTexCoordinate(tex.getMaxU());
			TEX_UVS[3] = TerrainFormat.deNormalizeTexCoordinate(tex.getMaxV());

			FacingData render = FACE_RENDER[dir];
			VertexWriter writer = task.getSolidWriter(dir);

			writer.ensureCapacity(TerrainFormat.STRIDE * 8);

			if (ambient) {
				renderFace(writer, render, cache, x, y, z, blockColor, overlayColor, sideGrass);
			} else {
				renderFaceNoSmooth(writer, render, cache, x, y, z, blockColor, overlayColor, sideGrass);
			}
		}
	}

	public static Icon getIconSafe(Icon texture) {
		return texture == null ? MISSING : texture;
	}

	public static void renderFace(VertexWriter writer, FacingData face, SectionCache cache, int x, int y, int z, int blockColor, int overlayColor, boolean sideGrass) {
		int p1X = face.aoCornerX0;
		int p1Y = face.aoCornerY0;
		int p1Z = face.aoCornerZ0;

		int p2X = face.aoCornerX1;
		int p2Y = face.aoCornerY1;
		int p2Z = face.aoCornerZ1;

		int dirX = x + face.dirX;
		int dirY = y + face.dirY;
		int dirZ = z + face.dirZ;

		int posZ = getBlockCached(cache, dirX + p2X, dirY + p2Y, dirZ + p2Z);
		int negZ = getBlockCached(cache, dirX - p2X, dirY - p2Y, dirZ - p2Z);
		int posX = getBlockCached(cache, dirX + p1X, dirY + p1Y, dirZ + p1Z);
		int negX = getBlockCached(cache, dirX - p1X, dirY - p1Y, dirZ - p1Z);

		int p12X = p1X + p2X;
		int p12Y = p1Y + p2Y;
		int p12Z = p1Z + p2Z;

		int pd12X = p1X - p2X;
		int pd12Y = p1Y - p2Y;
		int pd12Z = p1Z - p2Z;

		int cornerPP = fullFace(posZ & posX) == 0 ? getBlockCached(cache, dirX + p12X, dirY + p12Y, dirZ + p12Z) : 1;
		int cornerPN = fullFace(negZ & posX) == 0 ? getBlockCached(cache, dirX + pd12X, dirY + pd12Y, dirZ + pd12Z) : 1;
		int cornerNP = fullFace(posZ & negX) == 0 ? getBlockCached(cache, dirX - pd12X, dirY - pd12Y, dirZ - pd12Z) : 1;
		int cornerNN = fullFace(negZ & negX) == 0 ? getBlockCached(cache, dirX - p12X, dirY - p12Y, dirZ - p12Z) : 1;

		int ao0 = ao(posZ, posX, cornerPP);
		int ao1 = ao(negZ, posX, cornerPN);
		int ao2 = ao(negZ, negX, cornerNN);
		int ao3 = ao(posZ, negX, cornerNP);

		int color0 = ColorBGRManager.multiplyColor(blockColor, ao0);
		int color1 = ColorBGRManager.multiplyColor(blockColor, ao1);
		int color2 = ColorBGRManager.multiplyColor(blockColor, ao2);
		int color3 = ColorBGRManager.multiplyColor(blockColor, ao3);

		int lightMap = cache.getLightmap(dirX, dirY, dirZ);
		int light0 = avg(avgU(cornerPP, lightMap), avg(posZ, posX)); // 0 vertex
		int light1 = avg(avgU(cornerPN, lightMap), avg(posX, negZ)); // 1 vertex
		int light2 = avg(avgU(cornerNN, lightMap), avg(negZ, negX)); // 2 vertex
		int light3 = avg(avgU(cornerNP, lightMap), avg(negX, posZ)); // 3 vertex

		int uv0 = face.uv0;
		int uv1 = face.uv1;
		int uv2 = face.uv2;
		int uv3 = face.uv3;

		x &= RegionConstants.BLOCK_BITS_X;
		y &= RegionConstants.BLOCK_BITS_Y;
		z &= RegionConstants.BLOCK_BITS_Z;

		long ptr = writer.getPtr() + writer.getOffset();
		long quadOffs = face.quadVert;

		boolean flip = ao0 + ao2 > ao3 + ao1 || light0 + light2 <= light3 + light1;

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

	public static void renderFaceNoSmooth(VertexWriter writer, FacingData face, SectionCache cache, int x, int y, int z, int blockColor, int overlayColor, boolean sideGrass) {
		int lightMap = cache.getLightmap(x + face.dirX, y + face.dirY, z + face.dirZ);

		int uv0 = face.uv0;
		int uv1 = face.uv1;
		int uv2 = face.uv2;
		int uv3 = face.uv3;

		x &= RegionConstants.BLOCK_BITS_X;
		y &= RegionConstants.BLOCK_BITS_Y;
		z &= RegionConstants.BLOCK_BITS_Z;

		long ptr = writer.getPtr() + writer.getOffset();
		long quadOffs = face.quadVert;

		ptr = addVertex(ptr, quadOffs >> (0 * 12), x, y, z, uv0, blockColor, lightMap);
		ptr = addVertex(ptr, quadOffs >> (1 * 12), x, y, z, uv1, blockColor, lightMap);
		ptr = addVertex(ptr, quadOffs >> (2 * 12), x, y, z, uv2, blockColor, lightMap);
		ptr = addVertex(ptr, quadOffs >> (3 * 12), x, y, z, uv3, blockColor, lightMap);

		if (sideGrass) {
			ptr = addVertex(ptr, quadOffs >> (0 * 12), x, y, z, uv0, overlayColor, lightMap);
			ptr = addVertex(ptr, quadOffs >> (1 * 12), x, y, z, uv1, overlayColor, lightMap);
			ptr = addVertex(ptr, quadOffs >> (2 * 12), x, y, z, uv2, overlayColor, lightMap);
			addVertex(ptr, quadOffs >> (3 * 12), x, y, z, uv3, overlayColor, lightMap);
		}

		writer.vertices += 4;
		writer.offset += TerrainFormat.STRIDE * 4;
	}

	public static long addVertex(long ptr, long quadVert, int x, int y, int z, int uvData, int color, int lightMap) {
		int vertOff = (int) quadVert;

		int relX = x + (vertOff & 0xF);
		vertOff >>>= 4;
		int relY = y + (vertOff & 0xF);
		vertOff >>>= 4;
		int relZ = z + (vertOff & 0xF);

		TerrainFormat.writeTerrainVertex(ptr, relX, relY, relZ, TEX_UVS[uvData & 0xFF], TEX_UVS[uvData >>> 8], color, lightMap);
		return ptr + TerrainFormat.STRIDE;
	}

	public static int getBlockCached(SectionCache cache, int x, int y, int z) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		int blockX = x - cache.blockX;
		int blockY = y - cache.blockY;
		int blockZ = z - cache.blockZ;

		int sectionIndex = SectionCache.sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
		int solidBlock = PrimitivesFlags.SOLID_LIGHT_MASK[cache.getBlockId(sectionIndex, blockIndex)];

		if (solidBlock == 1) {
			return 1;
		}

		return cache.getLightmap(sectionIndex, blockIndex);
	}

	private static Vector3i createVec3i(int x, int y, int z) {
		return new Vector3i(x, y, z);
	}

	public static final FacingData NEG_Y = new FacingData();
	public static final FacingData POS_Y = new FacingData();

	public static final FacingData NEG_X = new FacingData();
	public static final FacingData POS_X = new FacingData();

	public static final FacingData NEG_Z = new FacingData();
	public static final FacingData POS_Z = new FacingData();

	public static final FacingData[] FACE_RENDER = new FacingData[]{
		NEG_Y,
		POS_Y,

		NEG_Z,
		POS_Z,

		NEG_X,
		POS_X
	};

	public static final int NEG_Y_DIR = Direction.DOWN;
	public static final int POS_Y_DIR = Direction.UP;

	public static final int NEG_X_DIR = Direction.WEST;
	public static final int POS_X_DIR = Direction.EAST;

	public static final int NEG_Z_DIR = Direction.NORTH;
	public static final int POS_Z_DIR = Direction.SOUTH;

	// minU - 0, minV - 1, maxU - 2, maxV - 3.
    static {
		int minU = 0;
		int minV = 1;
		int maxU = 2;
		int maxV = 3;
		MAP_ID_TO_UV[0] = compactId(minU, minV);
		MAP_ID_TO_UV[1] = compactId(minU, maxV);
		MAP_ID_TO_UV[2] = compactId(maxU, maxV);
		MAP_ID_TO_UV[3] = compactId(maxU, minV);

		MAP_ID_TO_UV[4] = compactId(minU, maxV);
		MAP_ID_TO_UV[5] = compactId(maxU, maxV);
		MAP_ID_TO_UV[6] = compactId(maxU, minV);
		MAP_ID_TO_UV[7] = compactId(minU, minV);

		MAP_ID_TO_UV[8]  = compactId(maxU, maxV);
		MAP_ID_TO_UV[9]  = compactId(maxU, minV);
		MAP_ID_TO_UV[10] = compactId(minU, minV);
		MAP_ID_TO_UV[11] = compactId(minU, maxV);

		MAP_ID_TO_UV[12] = compactId(maxU, minV);
		MAP_ID_TO_UV[13] = compactId(minU, minV);
		MAP_ID_TO_UV[14] = compactId(minU, maxV);
		MAP_ID_TO_UV[15] = compactId(maxU, maxV);

		NEG_Y.aoCorner0 = NEG_X_DIR;
		NEG_Y.aoCorner1 = POS_Z_DIR;
		NEG_Y.setQuadVerts(0, createVec3i(0, 0, 1));
		NEG_Y.setQuadVerts(1, createVec3i(0, 0, 0));
		NEG_Y.setQuadVerts(2, createVec3i(1, 0, 0));
		NEG_Y.setQuadVerts(3, createVec3i(1, 0, 1));
		NEG_Y.setTexInd(1, 0, 3, 2);

		POS_Y.aoCorner0 = POS_X_DIR;
		POS_Y.aoCorner1 = POS_Z_DIR;
		POS_Y.setQuadVerts(0, createVec3i(1, 1, 1));
		POS_Y.setQuadVerts(1, createVec3i(1, 1, 0));
		POS_Y.setQuadVerts(2, createVec3i(0, 1, 0));
		POS_Y.setQuadVerts(3, createVec3i(0, 1, 1));
		POS_Y.setTexInd(2, 3, 0, 1);

		POS_X.aoCorner0 = NEG_Y_DIR;
		POS_X.aoCorner1 = POS_Z_DIR;
		POS_X.setQuadVerts(0, createVec3i(1, 0, 1));
		POS_X.setQuadVerts(1, createVec3i(1, 0, 0));
		POS_X.setQuadVerts(2, createVec3i(1, 1, 0));
		POS_X.setQuadVerts(3, createVec3i(1, 1, 1));
		POS_X.setTexInd(1, 2, 3, 0);

		NEG_X.aoCorner0 = POS_Y_DIR;
		NEG_X.aoCorner1 = POS_Z_DIR;
		NEG_X.setQuadVerts(0, createVec3i(0, 1, 1));
		NEG_X.setQuadVerts(1, createVec3i(0, 1, 0));
		NEG_X.setQuadVerts(2, createVec3i(0, 0, 0));
		NEG_X.setQuadVerts(3, createVec3i(0, 0, 1));
		NEG_X.setTexInd(3, 0, 1, 2);

		POS_Z.aoCorner0 = NEG_X_DIR;
		POS_Z.aoCorner1 = POS_Y_DIR;
		POS_Z.setQuadVerts(0, createVec3i(0, 1, 1));
		POS_Z.setQuadVerts(1, createVec3i(0, 0, 1));
		POS_Z.setQuadVerts(2, createVec3i(1, 0, 1));
		POS_Z.setQuadVerts(3, createVec3i(1, 1, 1));
		POS_Z.setTexInd(0, 1, 2, 3);

		NEG_Z.aoCorner0 = POS_Y_DIR;
		NEG_Z.aoCorner1 = NEG_X_DIR;
		NEG_Z.setQuadVerts(0, createVec3i(0, 1, 0));
		NEG_Z.setQuadVerts(1, createVec3i(1, 1, 0));
		NEG_Z.setQuadVerts(2, createVec3i(1, 0, 0));
		NEG_Z.setQuadVerts(3, createVec3i(0, 0, 0));
		NEG_Z.setTexInd(3, 0, 1, 2);

		NEG_X.processCornersDir(WEST);
		POS_X.processCornersDir(EAST);

		NEG_Z.processCornersDir(NORTH);
		POS_Z.processCornersDir(SOUTH);

		NEG_Y.processCornersDir(DOWN);
		POS_Y.processCornersDir(UP);

		for (int i = 0; i < Direction.COUNT; i++) {
			SHADE_FULL_COLOR[i] = ColorBGRManager.multiplyColor(0xFF_FF_FF, SIDE_LIGHT_MULTIPLIER[i]);
			SHADE_FULL_FACTOR[i] = ColorBGRManager.normToFactor(SIDE_LIGHT_MULTIPLIER[i]);
		}
	}

	private static int compactId(int u, int v) {
		return u | v << 8;
	}
}
