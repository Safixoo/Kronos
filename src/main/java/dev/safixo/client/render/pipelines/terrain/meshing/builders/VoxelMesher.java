package dev.safixo.client.render.pipelines.terrain.meshing.builders;

import dev.safixo.client.render.pipelines.terrain.meshing.data.FacingRender;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.render.pipelines.terrain.meshing.model.ModelColorizer;
import dev.safixo.client.render.pipelines.terrain.meshing.model.ModelHelper;
import dev.safixo.client.util.AtlasSpriteUnsafe;
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
import static dev.safixo.client.util.Direction.*;

public class VoxelMesher {
	public static final ModelColorizer MODEL_COLORIZER = new ModelColorizer();
	public static final int[] MAP_ID_TO_UV = new int[4 * 4];

	public static final int[] SHADE_FULL_COLOR = new int[Direction.COUNT];
	public static final int[] SHADE_FULL_FACTOR = new int[Direction.COUNT];
	private static final float[] TEX_UVS = new float[4];
	private static final float[] OVERLAY_UVS = new float[4];

	private static final Icon SIDE_GRASS_NON_OVERLAY = Block.grass.getIcon(5, 5);
	public static final float[] SIDE_LIGHT_MULTIPLIER = new float[] { 0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F };

	private static final int BLOCK_GRASS_ID = Block.grass.blockID;

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

		int cornerPP = getBlockCacheLazily(cache, dirX + p12X, dirY + p12Y, dirZ + p12Z);
		int cornerPN = getBlockCacheLazily(cache, dirX + pd12X, dirY + pd12Y, dirZ + pd12Z);

		int lightPP = ModelHelper.fullFace(posZ | posX) == 0 ? ModelHelper.light(cache, dirX + p12X, dirY + p12Y, dirZ + p12Z, cornerPP) : 0;
		int lightPN = ModelHelper.fullFace(negZ | posX) == 0 ? ModelHelper.light(cache, dirX + pd12X, dirY + pd12Y, dirZ + pd12Z, cornerPN) : 0;

		int cornerNP = getBlockCacheLazily(cache, dirX - pd12X, dirY - pd12Y, dirZ - pd12Z);
		int cornerNN = getBlockCacheLazily(cache, dirX - p12X, dirY - p12Y, dirZ - p12Z);

		int lightNP = ModelHelper.fullFace(posZ | negX) == 0 ? ModelHelper.light(cache, dirX - pd12X, dirY - pd12Y, dirZ - pd12Z, cornerNP) : 0;
		int lightNN = ModelHelper.fullFace(negZ | negX) == 0 ? ModelHelper.light(cache, dirX - p12X, dirY - p12Y, dirZ - p12Z, cornerNN) : 0;

		int ao0 = ModelHelper.ao(posZ, posX, cornerPP);
		int ao1 = ModelHelper.ao(negZ, posX, cornerPN);
		int ao2 = ModelHelper.ao(negZ, negX, cornerNN);
		int ao3 = ModelHelper.ao(posZ, negX, cornerNP);

		int lightMap = cache.getLightBrightnessForSkyBlocks(dirX, dirY, dirZ, 0);

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

		boolean flip = color0 > color3 || color2 > color1;
		float[] texUv = TEX_UVS;

		if (flip) {
			addVertex(writer, face, 0 * 9, x, y, z, texUv[uv0 & 0xFF], texUv[uv0 >>> 8], color0, light0);
			addVertex(writer, face, 1 * 9, x, y, z, texUv[uv1 & 0xFF], texUv[uv1 >>> 8], color1, light1);
			addVertex(writer, face, 2 * 9, x, y, z, texUv[uv2 & 0xFF], texUv[uv2 >>> 8], color2, light2);
			addVertex(writer, face, 3 * 9, x, y, z, texUv[uv3 & 0xFF], texUv[uv3 >>> 8], color3, light3);
		} else {
			addVertex(writer, face, 3 * 9, x, y, z, texUv[uv3 & 0xFF], texUv[uv3 >>> 8], color3, light3);
			addVertex(writer, face, 0 * 9, x, y, z, texUv[uv0 & 0xFF], texUv[uv0 >>> 8], color0, light0);
			addVertex(writer, face, 1 * 9, x, y, z, texUv[uv1 & 0xFF], texUv[uv1 >>> 8], color1, light1);
			addVertex(writer, face, 2 * 9, x, y, z, texUv[uv2 & 0xFF], texUv[uv2 >>> 8], color2, light2);
		}

		if (tex == SIDE_GRASS_NON_OVERLAY) {
			texUv = OVERLAY_UVS;
			color0 = ColorBGRManager.multiplyColor(overlayColor, ao0);
			color1 = ColorBGRManager.multiplyColor(overlayColor, ao1);
			color2 = ColorBGRManager.multiplyColor(overlayColor, ao2);
			color3 = ColorBGRManager.multiplyColor(overlayColor, ao3);

			if (flip) {
				addVertex(writer, face, 0 * 9, x, y, z, texUv[uv0 & 0xFF], texUv[uv0 >>> 8], color0, light0);
				addVertex(writer, face, 1 * 9, x, y, z, texUv[uv1 & 0xFF], texUv[uv1 >>> 8], color1, light1);
				addVertex(writer, face, 2 * 9, x, y, z, texUv[uv2 & 0xFF], texUv[uv2 >>> 8], color2, light2);
				addVertex(writer, face, 3 * 9, x, y, z, texUv[uv3 & 0xFF], texUv[uv3 >>> 8], color3, light3);
			} else {
				addVertex(writer, face, 3 * 9, x, y, z, texUv[uv3 & 0xFF], texUv[uv3 >>> 8], color3, light3);
				addVertex(writer, face, 0 * 9, x, y, z, texUv[uv0 & 0xFF], texUv[uv0 >>> 8], color0, light0);
				addVertex(writer, face, 1 * 9, x, y, z, texUv[uv1 & 0xFF], texUv[uv1 >>> 8], color1, light1);
				addVertex(writer, face, 2 * 9, x, y, z, texUv[uv2 & 0xFF], texUv[uv2 >>> 8], color2, light2);
			}
		}
	}

	public static void renderFaceNoSmooth(FacingRender facing, int dir, SectionCache cache, int x, int y, int z, int blockColor) {
		Vector3i dirVec = Direction.getDirection(dir);

		int lightMap = cache.getLightBrightnessForSkyBlocks(x + dirVec.x, y + dirVec.y, z + dirVec.z, 0);

		final float[] uvs = TEX_UVS;
		int uv0 = facing.uvData[0];
		int uv1 = facing.uvData[1];
		int uv2 = facing.uvData[2];
		int uv3 = facing.uvData[3];

		x &= RegionRender.BLOCK_BITS_X;
		y &= RegionRender.BLOCK_BITS_Y;
		z &= RegionRender.BLOCK_BITS_Z;

		VertexWriter writer = VertexWriter.getCurrentInstance();
		writer.ensureCapacity(TerrainFormat.STRIDE * 4);

		addVertex(writer, facing, 0, x, y, z, uvs[uv0 & 0xFF], uvs[uv0 >>> 8], blockColor, lightMap);
		addVertex(writer, facing, 1, x, y, z, uvs[uv1 & 0xFF], uvs[uv1 >>> 8], blockColor, lightMap);
		addVertex(writer, facing, 2, x, y, z, uvs[uv2 & 0xFF], uvs[uv2 >>> 8], blockColor, lightMap);
		addVertex(writer, facing, 3, x, y, z, uvs[uv3 & 0xFF], uvs[uv3 >>> 8], blockColor, lightMap);
	}

	private static void addVertex(VertexWriter writer, FacingRender face, int vertInd, int x, int y, int z, float u, float v, int color, int lightMap) {
		int vertOff = (int) (face.quadVert >>> vertInd);

		int relX = x + (vertOff & 0b111);
		vertOff >>>= 3;
		int relY = y + (vertOff & 0b111);
		vertOff >>>= 3;
		int relZ = z + (vertOff & 0b111);

		long ptr = writer.getTotalOffset();

		TerrainFormat.writeTerrainVertex(ptr, relX, relY, relZ, u, v, color, lightMap);
		writer.addVertexCounter(TerrainFormat.STRIDE);
	}

	public static int getBlockCacheLazily(SectionCache cache, int x, int y, int z) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		int blockX = x - cache.blockX;
		int blockY = y - cache.blockY;
		int blockZ = z - cache.blockZ;

		int sectionIndex = SectionCache.sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
		int solidBlock = PrimitivesFlags.SOLID_LIGHT_MASK[MathExt.byteToUnsigned(SectionCache.SECTION_BLOCKS[sectionIndex][blockIndex])];

		if (solidBlock == 1) {
			return solidBlock;
		}

		return ~1;
	}

	public static int getBlockCached(SectionCache cache, int x, int y, int z) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		int blockX = x - cache.blockX;
		int blockY = y - cache.blockY;
		int blockZ = z - cache.blockZ;

		int sectionIndex = SectionCache.sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
		int solidBlock = PrimitivesFlags.SOLID_LIGHT_MASK[MathExt.byteToUnsigned(SectionCache.SECTION_BLOCKS[sectionIndex][blockIndex])];

		if (solidBlock == 1) {
			return solidBlock;
		}

		int skyLight = SectionCache.getNibble(SectionCache.SKY_LIGHT[sectionIndex], blockIndex);
		int blockLight = SectionCache.getNibble(SectionCache.BLOCK_LIGHT[sectionIndex], blockIndex);

		return MathExt.getLightmapCoord(skyLight, blockLight) << 4;
	}

	private static Vector3i createVec3i(int x, int y, int z) {
		return new Vector3i(x, y, z);
	}

	public static final FacingRender NEG_Y = new FacingRender();
	public static final FacingRender POS_Y = new FacingRender();

	public static final FacingRender NEG_X = new FacingRender();
	public static final FacingRender POS_X = new FacingRender();

	public static final FacingRender NEG_Z = new FacingRender();
	public static final FacingRender POS_Z = new FacingRender();

	public static final FacingRender[] FACE_RENDER = new FacingRender[]{
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
		OVERLAY_UVS[0] = BlockGrass.getIconSideOverlay().getMinU();
		OVERLAY_UVS[1] = BlockGrass.getIconSideOverlay().getMinV();
		OVERLAY_UVS[2] = BlockGrass.getIconSideOverlay().getMaxU();
		OVERLAY_UVS[3] = BlockGrass.getIconSideOverlay().getMaxV();

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
