package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.render.pipelines.terrain.meshing.data.FacingRender;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.render.pipelines.terrain.meshing.vanilla.ModelHelper;
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

public class FullBlockMesher {
	public static final ModelColorizer MODEL_COLORIZER = new ModelColorizer();
	public static final int[] MAP_ID_TO_UV = new int[4 * 4];

	public static final int[] SHADE_FULL_COLOR = new int[Direction.COUNT];
	public static final int[] SHADE_FULL_FACTOR = new int[Direction.COUNT];
	private static final float[] TEX_UVS = new float[4];
	private static final float[] OVERLAY_UVS = new float[4];

	private static final Icon SIDE_GRASS_NON_OVERLAY = Block.grass.getIcon(5, 5);
	public static final float[] SIDE_LIGHT_MULTIPLIER = new float[] { 0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F };

	public static void renderSolidCube(Block block, SectionCache cache, int x, int y, int z, boolean ambient, int drawSet, int blockId) {
		if (drawSet == 0) {
			return;
		}

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
			FacingRender render = FACE_RENDER[dir];

			boolean shouldColor = modelColor != 0xFFFFFF && blockId != Block.grass.blockID || dir == UP;

			int blockColor = shouldColor ? ColorBGRManager.multiplyColor(modelColor, SHADE_FULL_FACTOR[dir]) : SHADE_FULL_COLOR[dir];
			int overlayColor = tex == SIDE_GRASS_NON_OVERLAY && !shouldColor ? ColorBGRManager.multiplyColorByColor(modelColor, blockColor) : blockColor;

			final float[] uvs = TEX_UVS;
			uvs[0] = tex.getMinU();
			uvs[1] = tex.getMinV();
			uvs[2] = tex.getMaxU();
			uvs[3] = tex.getMaxV();

			if (ambient) {
				renderFace(render, tex, dir, cache, x, y, z, blockColor, overlayColor);
			} else {
				renderFaceNoSmooth(render, dir, cache, x, y, z, blockColor);
			}
		}
	}

	public static void renderFace(FacingRender facing, Icon tex, int dir, SectionCache cache, int x, int y, int z, int blockColor, int overlayColor) {
		int p1X = facing.aoCornerX0;
		int p1Y = facing.aoCornerY0;
		int p1Z = facing.aoCornerZ0;

		int p2X = facing.aoCornerX1;
		int p2Y = facing.aoCornerY1;
		int p2Z = facing.aoCornerZ1;

		int dirX = x + x(dir);
		int dirY = y + y(dir);
		int dirZ = z + z(dir);

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

		int shade0 = ModelHelper.ao(posZ, posX, cornerPP);
		int shade1 = ModelHelper.ao(negZ, posX, cornerPN);
		int shade2 = ModelHelper.ao(negZ, negX, cornerNN);
		int shade3 = ModelHelper.ao(posZ, negX, cornerNP);

		int lightMap = cache.getLightBrightnessForSkyBlocks(dirX, dirY, dirZ, 0);

		int lightPZ = ModelHelper.light(posZ);
		int lightPX = ModelHelper.light(posX);

		int lightNZ = ModelHelper.light(negZ);
		int lightNX = ModelHelper.light(negX);

		int light0 = ModelHelper.avg(ModelHelper.avg(lightPP, lightMap), ModelHelper.avg(lightPZ, lightPX)); // 0 vertex
		int light1 = ModelHelper.avg(ModelHelper.avg(lightPN, lightMap), ModelHelper.avg(lightPX, lightNZ)); // 1 vertex
		int light2 = ModelHelper.avg(ModelHelper.avg(lightNN, lightMap), ModelHelper.avg(lightNZ, lightNX)); // 2 vertex
		int light3 = ModelHelper.avg(ModelHelper.avg(lightNP, lightMap), ModelHelper.avg(lightNX, lightPZ)); // 3 vertex

		int uv0 = facing.uvData[0];
		int uv1 = facing.uvData[1];
		int uv2 = facing.uvData[2];
		int uv3 = facing.uvData[3];

		int color0 = ColorBGRManager.multiplyColor(blockColor, shade0);
		int color1 = ColorBGRManager.multiplyColor(blockColor, shade1);
		int color2 = ColorBGRManager.multiplyColor(blockColor, shade2);
		int color3 = ColorBGRManager.multiplyColor(blockColor, shade3);

		x &= RegionRender.BLOCK_BITS_X;
		y &= RegionRender.BLOCK_BITS_Y;
		z &= RegionRender.BLOCK_BITS_Z;

		VertexWriter writer = VertexWriter.getCurrentInstance();
		writer.ensureCapacity(TerrainFormat.STRIDE * 4);

		boolean flip = color0 > color3 || color2 > color1;
		float[] texUv = TEX_UVS;

		if (flip) {
			addVertex(writer, facing, 0, x, y, z, texUv[uv0 & 0xFFFF], texUv[uv0 >>> 16], color0, light0);
			addVertex(writer, facing, 1, x, y, z, texUv[uv1 & 0xFFFF], texUv[uv1 >>> 16], color1, light1);
			addVertex(writer, facing, 2, x, y, z, texUv[uv2 & 0xFFFF], texUv[uv2 >>> 16], color2, light2);
			addVertex(writer, facing, 3, x, y, z, texUv[uv3 & 0xFFFF], texUv[uv3 >>> 16], color3, light3);
		} else {
			addVertex(writer, facing, 3, x, y, z, texUv[uv3 & 0xFFFF], texUv[uv3 >>> 16], color3, light3);
			addVertex(writer, facing, 0, x, y, z, texUv[uv0 & 0xFFFF], texUv[uv0 >>> 16], color0, light0);
			addVertex(writer, facing, 1, x, y, z, texUv[uv1 & 0xFFFF], texUv[uv1 >>> 16], color1, light1);
			addVertex(writer, facing, 2, x, y, z, texUv[uv2 & 0xFFFF], texUv[uv2 >>> 16], color2, light2);
		}

		if (tex == SIDE_GRASS_NON_OVERLAY) {
			texUv = OVERLAY_UVS;
			color0 = ColorBGRManager.multiplyColor(overlayColor, shade0);
			color1 = ColorBGRManager.multiplyColor(overlayColor, shade1);
			color2 = ColorBGRManager.multiplyColor(overlayColor, shade2);
			color3 = ColorBGRManager.multiplyColor(overlayColor, shade3);

			if (flip) {
				addVertex(writer, facing, 0, x, y, z, texUv[uv0 & 0xFFFF], texUv[uv0 >>> 16], color0, light0);
				addVertex(writer, facing, 1, x, y, z, texUv[uv1 & 0xFFFF], texUv[uv1 >>> 16], color1, light1);
				addVertex(writer, facing, 2, x, y, z, texUv[uv2 & 0xFFFF], texUv[uv2 >>> 16], color2, light2);
				addVertex(writer, facing, 3, x, y, z, texUv[uv3 & 0xFFFF], texUv[uv3 >>> 16], color3, light3);
			} else {
				addVertex(writer, facing, 3, x, y, z, texUv[uv3 & 0xFFFF], texUv[uv3 >>> 16], color3, light3);
				addVertex(writer, facing, 0, x, y, z, texUv[uv0 & 0xFFFF], texUv[uv0 >>> 16], color0, light0);
				addVertex(writer, facing, 1, x, y, z, texUv[uv1 & 0xFFFF], texUv[uv1 >>> 16], color1, light1);
				addVertex(writer, facing, 2, x, y, z, texUv[uv2 & 0xFFFF], texUv[uv2 >>> 16], color2, light2);
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

		addVertex(writer, facing, 0, x, y, z, uvs[uv0 & 0xFFFF], uvs[uv0 >> 16], blockColor, lightMap);
		addVertex(writer, facing, 1, x, y, z, uvs[uv1 & 0xFFFF], uvs[uv1 >> 16], blockColor, lightMap);
		addVertex(writer, facing, 2, x, y, z, uvs[uv2 & 0xFFFF], uvs[uv2 >> 16], blockColor, lightMap);
		addVertex(writer, facing, 3, x, y, z, uvs[uv3 & 0xFFFF], uvs[uv3 >> 16], blockColor, lightMap);
	}

	private static void addVertex(VertexWriter writer, FacingRender facing, int vertInd, int x, int y, int z, float u, float v, int color, int lightMap) {
		Vector3i vertOff = facing.quadVerts[vertInd];
		int relX = x + vertOff.x;
		int relY = y + vertOff.y;
		int relZ = z + vertOff.z;

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
		NEG_Y.quadVerts[0] = createVec3i(0, 0, 1);
		NEG_Y.quadVerts[1] = createVec3i(0, 0, 0);
		NEG_Y.quadVerts[2] = createVec3i(1, 0, 0);
		NEG_Y.quadVerts[3] = createVec3i(1, 0, 1);
		NEG_Y.setTexInd(0, 1, 2, 3);

		POS_Y.aoCorner0 = POS_X_DIR;
		POS_Y.aoCorner1 = POS_Z_DIR;
		POS_Y.quadVerts[0] = createVec3i(1, 1, 1);
		POS_Y.quadVerts[1] = createVec3i(1, 1, 0);
		POS_Y.quadVerts[2] = createVec3i(0, 1, 0);
		POS_Y.quadVerts[3] = createVec3i(0, 1, 1);
		POS_Y.setTexInd(2, 3, 0, 1);

		POS_X.aoCorner0 = NEG_Y_DIR;
		POS_X.aoCorner1 = POS_Z_DIR;
		POS_X.quadVerts[0] = createVec3i(1 , 0, 1);
		POS_X.quadVerts[1] = createVec3i(1 , 0, 0);
		POS_X.quadVerts[2] = createVec3i(1 , 1, 0);
		POS_X.quadVerts[3] = createVec3i(1 , 1, 1);
		POS_X.setTexInd(1, 2, 3, 0);

		NEG_X.aoCorner0 = POS_Y_DIR;
		NEG_X.aoCorner1 = POS_Z_DIR;
		NEG_X.quadVerts[0] = createVec3i(0 , 1, 1);
		NEG_X.quadVerts[1] = createVec3i(0 , 1, 0);
		NEG_X.quadVerts[2] = createVec3i(0 , 0, 0);
		NEG_X.quadVerts[3] = createVec3i(0 , 0, 1);
		NEG_X.setTexInd(3, 0, 1, 2);

		POS_Z.aoCorner0 = NEG_X_DIR;
		POS_Z.aoCorner1 = POS_Y_DIR;
		POS_Z.quadVerts[0] = createVec3i(0, 1, 1 );
		POS_Z.quadVerts[1] = createVec3i(0, 0, 1 );
		POS_Z.quadVerts[2] = createVec3i(1, 0, 1 );
		POS_Z.quadVerts[3] = createVec3i(1, 1, 1 );
		POS_Z.setTexInd(0, 1, 2, 3);

		NEG_Z.aoCorner0 = POS_Y_DIR;
		NEG_Z.aoCorner1 = NEG_X_DIR;
		NEG_Z.quadVerts[0] = createVec3i(0, 1, 0);
		NEG_Z.quadVerts[1] = createVec3i(1, 1, 0 );
		NEG_Z.quadVerts[2] = createVec3i(1, 0, 0 );
		NEG_Z.quadVerts[3] = createVec3i(0, 0, 0 );
		NEG_Z.setTexInd(3, 0, 1, 2);

		NEG_X.processCornersDir();
		POS_X.processCornersDir();

		NEG_Z.processCornersDir();
		POS_Z.processCornersDir();

		NEG_Y.processCornersDir();
		POS_Y.processCornersDir();

		for (int i = 0; i < Direction.COUNT; i++) {
			SHADE_FULL_COLOR[i] = ColorBGRManager.multiplyColor(0xFF_FF_FF, SIDE_LIGHT_MULTIPLIER[i]);
			SHADE_FULL_FACTOR[i] = ColorBGRManager.normToFactor(SIDE_LIGHT_MULTIPLIER[i]);
		}
	}

	private static int compactId(int u, int v) {
		return u | v << 16;
	}
}
