package dev.safixo.client.render.pipelines.terrain.meshing.builders;

import dev.safixo.client.render.pipelines.terrain.meshing.data.FacingData;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.render.pipelines.terrain.meshing.model.ModelColorizer;
import dev.safixo.client.util.MathExt;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.util.Icon;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;
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
	protected static final float[] TEX_UVS = new float[4];
	public static final float[] OVERLAY_UVS = new float[4];

	public static Icon SIDE_GRASS_NON_OVERLAY = Block.grass.getIcon(5, 5);
	public static final float[] SIDE_LIGHT_MULTIPLIER = new float[] { 0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F };

	protected static final int BLOCK_GRASS_ID = Block.grass.blockID;

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


			int blockColor = SHADE_FULL_COLOR[dir];
			int overlayColor = blockColor;

			if (modelColor != 0xFFFFFF) {
				overlayColor = ColorBGRManager.multiplyColor(modelColor, SHADE_FULL_FACTOR[dir]);

				if (blockId != BLOCK_GRASS_ID || dir == UP) {
					blockColor = overlayColor;
				}
			}

			Icon tex = block.getBlockTexture(cache, x, y, z, dir);
			final float[] uvs = TEX_UVS;
			boolean sideGrass = tex == SIDE_GRASS_NON_OVERLAY;

			uvs[0] = tex.getMinU();
			uvs[1] = tex.getMinV();
			uvs[2] = tex.getMaxU();
			uvs[3] = tex.getMaxV();

			FacingData render = FACE_RENDER[dir];
			VertexWriter writer = VertexWriter.SOLID[dir];

			if (ambient) {
				renderFace(writer, render, cache, x, y, z, blockColor, overlayColor, sideGrass);
			} else {
				renderFaceNoSmooth(writer, render, cache, x, y, z, dir, blockColor, overlayColor, sideGrass);
			}
		}
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
		int light0 = avg(avgF(lightMap, cornerPP), avg(posZ, posX)); // 0 vertex
		int light1 = avg(avgF(lightMap, cornerPN), avg(posX, negZ)); // 1 vertex
		int light2 = avg(avgF(lightMap, cornerNN), avg(negZ, negX)); // 2 vertex
		int light3 = avg(avgF(lightMap, cornerNP), avg(negX, posZ)); // 3 vertex

		int uv0 = face.uv0;
		int uv1 = face.uv1;
		int uv2 = face.uv2;
		int uv3 = face.uv3;

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

		if (sideGrass) {
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

	public static void renderFaceNoSmooth(VertexWriter writer, FacingData face, SectionCache cache, int x, int y, int z, int dir, int blockColor, int overlayColor, boolean sideGrass) {
		Vector3i dirVec = Direction.getDirection(dir);
		int lightMap = cache.getLightBrightnessForSkyBlocks(x + dirVec.x, y + dirVec.y, z + dirVec.z, 0);

		float[] texUv = TEX_UVS;
		int uv0 = face.uv0;
		int uv1 = face.uv1;
		int uv2 = face.uv2;
		int uv3 = face.uv3;

		x &= RegionRender.BLOCK_BITS_X;
		y &= RegionRender.BLOCK_BITS_Y;
		z &= RegionRender.BLOCK_BITS_Z;

		writer.ensureCapacity(TerrainFormat.STRIDE * 4);

		addVertex(writer, face, 0 * 12, x, y, z, texUv[uv0 & 0xFF], texUv[uv0 >>> 8], blockColor, lightMap);
		addVertex(writer, face, 1 * 12, x, y, z, texUv[uv1 & 0xFF], texUv[uv1 >>> 8], blockColor, lightMap);
		addVertex(writer, face, 2 * 12, x, y, z, texUv[uv2 & 0xFF], texUv[uv2 >>> 8], blockColor, lightMap);
		addVertex(writer, face, 3 * 12, x, y, z, texUv[uv3 & 0xFF], texUv[uv3 >>> 8], blockColor, lightMap);

		if (sideGrass) {
			texUv = OVERLAY_UVS;

			addVertex(writer, face, 0 * 12, x, y, z, texUv[uv0 & 0xFF], texUv[uv0 >>> 8], overlayColor, lightMap);
			addVertex(writer, face, 1 * 12, x, y, z, texUv[uv1 & 0xFF], texUv[uv1 >>> 8], overlayColor, lightMap);
			addVertex(writer, face, 2 * 12, x, y, z, texUv[uv2 & 0xFF], texUv[uv2 >>> 8], overlayColor, lightMap);
			addVertex(writer, face, 3 * 12, x, y, z, texUv[uv3 & 0xFF], texUv[uv3 >>> 8], overlayColor, lightMap);
		}
	}

	public static void addVertex(VertexWriter writer, FacingData face, int vertInd, int x, int y, int z, float u, float v, int color, int lightMap) {
		int vertOff = (int) (face.quadVert >>> vertInd);

		int relX = x + (vertOff & 0xF);
		vertOff >>>= 4;
		int relY = y + (vertOff & 0xF);
		vertOff >>>= 4;
		int relZ = z + (vertOff & 0xF);

		long ptr = writer.getTotalOffset();

		TerrainFormat.writeTerrainVertex(ptr, relX, relY, relZ, u, v, color, lightMap);
		writer.addVertexCounter(TerrainFormat.STRIDE);
	}

	public static int getBlockCached(SectionCache cache, int x, int y, int z) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		int blockX = x - cache.blockX;
		int blockY = y - cache.blockY;
		int blockZ = z - cache.blockZ;

		int sectionIndex = SectionCache.sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
		int solidBlock = PrimitivesFlags.SOLID_LIGHT_MASK[MathExt.byteToUnsigned(SectionCache.SECTION_BLOCKS[sectionIndex][blockIndex])];

		if (solidBlock == 1) {
			return 1;
		}

		int skyLight = SectionCache.getNibble(SectionCache.SKY_LIGHT[sectionIndex], blockIndex);
		int blockLight = SectionCache.getNibble(SectionCache.BLOCK_LIGHT[sectionIndex], blockIndex);

		return MathExt.getLightmapCoord(skyLight, blockLight);
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
		NEG_Y.setTexInd(0, 1, 2, 3);

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
