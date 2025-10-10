package dev.safixo.client.render.meshing;

import dev.safixo.client.render.util.MathExt;
import dev.safixo.client.render.util.math.Vector2i;
import dev.safixo.client.render.util.math.Vector3i;
import net.minecraft.block.Block;
import net.minecraft.util.Icon;
import dev.safixo.client.render.region.RegionRender;
import dev.safixo.client.render.util.data.BlocksFlags;
import dev.safixo.client.render.util.ColorBGRManager;
import dev.safixo.client.render.util.Direction;
import dev.safixo.client.render.vertex.VertexWriterManager;
import dev.safixo.client.render.vertex.writers.TerrainFormat;

import static dev.safixo.client.render.meshing.SectionCache.makeBlockIndex;
import static dev.safixo.client.render.meshing.SectionCache.processSign;
import static dev.safixo.client.render.util.Direction.*;

public class FullBlockMesher {
	private static final int[] SHADE_FULL_COLOR = new int[Direction.COUNT];
	private static final int[] SHADE_FULL_FACTOR = new int[Direction.COUNT];
	private static final float[] VERT_UVS = new float[4];

	private static final Vector2i[] MAP_ID_TO_UV = new Vector2i[4];

	public static final float[] SIDE_LIGHT_MULTIPLIER = new float[] { 0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F };

	public static void renderFaces(Block block, SectionCache cache, int x, int y, int z, boolean ambient, int drawSet, int blockId) {
		if (drawSet == 0) {
			return;
		}

		if (!BlocksFlags.DIRECT_CULL[blockId]) {
			drawSet |= block.shouldSideBeRendered(cache, x, y - 1, z, 0) ? 1 << DOWN : 0;
			drawSet |= block.shouldSideBeRendered(cache, x, y + 1, z, 1) ? 1 << UP : 0;
			drawSet |= block.shouldSideBeRendered(cache, x, y, z - 1, 2) ? 1 << NORTH : 0;

			drawSet |= block.shouldSideBeRendered(cache, x, y, z + 1, 3) ? 1 << SOUTH : 0;
			drawSet |= block.shouldSideBeRendered(cache, x - 1, y, z, 4) ? 1 << WEST : 0;
			drawSet |= block.shouldSideBeRendered(cache, x + 1, y, z, 5) ? 1 << EAST : 0;
		}

		int modelColor = ColorBGRManager.multiplyColor(block.colorMultiplier(cache, x, y, z), 255);
		int meta = cache.getBlockMetadataCenter(x, y, z);

		for (int dir = 0; dir < Direction.COUNT; dir++) {
			if ((drawSet & (1 << dir)) == 0) {
				continue;
			}

			int sideColor = modelColor;
			Icon tex = block.getIcon(dir, meta);

			if (Block.grass.blockID == blockId && dir != UP) {
				sideColor = 0xFFFFFF;
			}

			VertexWriterManager.setCurrentInstance(VertexWriterManager.SOLID[dir]);

			FacingRender render = FACE_RENDER[dir];
			int usedColor = ColorBGRManager.multiplyColor(sideColor, SHADE_FULL_FACTOR[dir]);

			if (ambient) {
				renderFace(render, tex, dir, cache, x, y, z, usedColor);
			} else {
				renderFaceNoSmooth(render, tex, dir, cache, x, y, z, usedColor);
			}
		}
	}

	public static void renderFace(FacingRender facing, Icon tex, int dir, SectionCache cache, int x, int y, int z, int color) {
		int p1X = facing.aoCornerX0;
		int p1Y = facing.aoCornerY0;
		int p1Z = facing.aoCornerZ0;

		int p2X = facing.aoCornerX1;
		int p2Y = facing.aoCornerY1;
		int p2Z = facing.aoCornerZ1;

		int dirX = x + Direction.x(dir);
		int dirY = y + Direction.y(dir);
		int dirZ = z + Direction.z(dir);

		x &= RegionRender.BLOCK_BITS_X;
		y &= RegionRender.BLOCK_BITS_Y;
		z &= RegionRender.BLOCK_BITS_Z;

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

		int cornerPP = getSolidCached(cache, dirX + p12X, dirY + p12Y, dirZ + p12Z);
		int cornerPN = getSolidCached(cache, dirX + pd12X, dirY + pd12Y, dirZ + pd12Z);

		int lightPP = fullFace(posZ | posX) != 0 ? light(cache, dirX + p12X, dirY + p12Y, dirZ + p12Z, cornerPP) : 0;
		int lightPN = fullFace(negZ | posX) != 0 ? light(cache, dirX + pd12X, dirY + pd12Y, dirZ + pd12Z, cornerPN) : 0;

		int cornerNP = getSolidCached(cache, dirX - pd12X, dirY - pd12Y, dirZ - pd12Z);
		int cornerNN = getSolidCached(cache, dirX - p12X, dirY - p12Y, dirZ - p12Z);

		int lightNP = fullFace(posZ | negX) != 0 ? light(cache, dirX - pd12X, dirY - pd12Y, dirZ - pd12Z, cornerNP) : 0;
		int lightNN = fullFace(negZ | negX) != 0 ? light(cache, dirX - p12X, dirY - p12Y, dirZ - p12Z, cornerNN) : 0;

		int shade0 = ao(posZ, posX, cornerPP);
		int shade1 = ao(negZ, posX, cornerPN);
		int shade2 = ao(negZ, negX, cornerNN);
		int shade3 = ao(posZ, negX, cornerNP);

		int lightMap = cache.getLightBrightnessForSkyBlocks(dirX, dirY, dirZ, 0);

		int lightPZ = light(posZ);
		int lightPX = light(posX);
		int lightNZ = light(negZ);
		int lightNX = light(negX);

		int light0 = avg(avg(lightPP, lightMap), avg(lightPZ, lightPX)); // 0 vertex
		int light1 = avg(avg(lightPN, lightMap), avg(lightPX, lightNZ)); // 1 vertex
		int light2 = avg(avg(lightNN, lightMap), avg(lightNZ, lightNX)); // 2 vertex
		int light3 = avg(avg(lightNP, lightMap), avg(lightNX, lightPZ)); // 3 vertex

		VertexWriterManager.getCurrentInstance().ensureCapacity(TerrainFormat.STRIDE * 4);

		final float[] uvs = VERT_UVS;
		uvs[0] = tex.getMinU();
		uvs[1] = tex.getMinV();
		uvs[2] = tex.getMaxU();
		uvs[3] = tex.getMaxV();

		Vector2i uv0 = MAP_ID_TO_UV[facing.uvData[0]];
		Vector2i uv1 = MAP_ID_TO_UV[facing.uvData[1]];
		Vector2i uv2 = MAP_ID_TO_UV[facing.uvData[2]];
		Vector2i uv3 = MAP_ID_TO_UV[facing.uvData[3]];

		int color0 = ColorBGRManager.multiplyColor(color, shade0);
		int color1 = ColorBGRManager.multiplyColor(color, shade1);
		int color2 = ColorBGRManager.multiplyColor(color, shade2);
		int color3 = ColorBGRManager.multiplyColor(color, shade3);

		if ((color0 > color3 || color2 > color1)) {
			addVertex(facing, 0, x, y, z, uvs[uv0.x], uvs[uv0.y], color0, light0);
			addVertex(facing, 1, x, y, z, uvs[uv1.x], uvs[uv1.y], color1, light1);
			addVertex(facing, 2, x, y, z, uvs[uv2.x], uvs[uv2.y], color2, light2);
			addVertex(facing, 3, x, y, z, uvs[uv3.x], uvs[uv3.y], color3, light3);
		} else {
			addVertex(facing, 3, x, y, z, uvs[uv3.x], uvs[uv3.y], color3, light3);
			addVertex(facing, 0, x, y, z, uvs[uv0.x], uvs[uv0.y], color0, light0);
			addVertex(facing, 1, x, y, z, uvs[uv1.x], uvs[uv1.y], color1, light1);
			addVertex(facing, 2, x, y, z, uvs[uv2.x], uvs[uv2.y], color2, light2);
		}
	}

	private static int light(int blockCache) {
		return blockCache >>> 4;
	}

	private static int light(SectionCache cache, int x, int y, int z, int blockCache) {
		if (blockCache == ~1) {
			return cache.getLightBrightnessForSkyBlocks(x, y, z, 0);
		}

		return blockCache >>> 4;
	}

	private static int fullFace(int blockCache) {
		return blockCache & 0b1;
	}

	private static int getSolidCached(SectionCache cache, int x, int y, int z) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		int blockX = x - cache.blockX;
		int blockY = y - cache.blockY;
		int blockZ = z - cache.blockZ;

		int sectionIndex = SectionCache.sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
		boolean solidBlock = BlocksFlags.SOLID[processSign(SectionCache.SECTION_BLOCKS[sectionIndex][blockIndex])];

		if (solidBlock) {
			return 0 << 4 | 1;
		}

		return ~1;
	}

	private static int getBlockCached(SectionCache cache, int x, int y, int z) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		int blockX = x - cache.blockX;
		int blockY = y - cache.blockY;
		int blockZ = z - cache.blockZ;

		int sectionIndex = SectionCache.sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
		int solidBlock = BlocksFlags.SOLID_LIGHT_MASK[processSign(SectionCache.SECTION_BLOCKS[sectionIndex][blockIndex])];

		if (solidBlock == 1) {
			return (0 << 4) | solidBlock;
		}

		int skyLight = SectionCache.getNibble(SectionCache.SKY_LIGHT[sectionIndex], blockIndex);
		int blockLight = SectionCache.getNibble(SectionCache.BLOCK_LIGHT[sectionIndex], blockIndex);

		return MathExt.getLightmapCoord(skyLight, blockLight) << 4;
	}

	public static void renderFaceNoSmooth(FacingRender facing, Icon tex, int dir, SectionCache cache, int x, int y, int z, int color) {
		Vector3i dirVec = Direction.getDirection(dir);

		int lightMap = cache.getLightBrightnessForSkyBlocks(x + dirVec.x, y + dirVec.y, z + dirVec.z, 0);

		x &= RegionRender.BLOCK_BITS_X;
		y &= RegionRender.BLOCK_BITS_Y;
		z &= RegionRender.BLOCK_BITS_Z;

		VertexWriterManager.getCurrentInstance().ensureCapacity(TerrainFormat.STRIDE * 4);

		final float[] uvs = VERT_UVS;
		uvs[0] = tex.getMinU();
		uvs[1] = tex.getMinV();
		uvs[2] = tex.getMaxU();
		uvs[3] = tex.getMaxV();

		Vector2i uv0 = MAP_ID_TO_UV[facing.uvData[0]];
		Vector2i uv1 = MAP_ID_TO_UV[facing.uvData[1]];
		Vector2i uv2 = MAP_ID_TO_UV[facing.uvData[2]];
		Vector2i uv3 = MAP_ID_TO_UV[facing.uvData[3]];

		addVertex(facing, 0, x, y, z, uvs[uv0.x], uvs[uv0.y], color, lightMap);
		addVertex(facing, 1, x, y, z, uvs[uv1.x], uvs[uv1.y], color, lightMap);
		addVertex(facing, 2, x, y, z, uvs[uv2.x], uvs[uv2.y], color, lightMap);
		addVertex(facing, 3, x, y, z, uvs[uv3.x], uvs[uv3.y], color, lightMap);
	}

	private int getBlockId(SectionCache cache, Vector3i pos, Vector3i off) {
		return cache.getBlockId(pos.x + off.x, pos.y + off.y, pos.z + off.z);
	}

	private static int avg(int a, int b) {
		if (b == 0) {
			return a;
		}
		if (a == 0) {
			return b;
		}
		return ((a + b) >>> 1) & 0xFF00_FF0;
	}

	private static void addVertex(FacingRender facing, int vertInd, int x, int y, int z, float u, float v, int color, int lightMap) {
		Vector3i vertOff = facing.quadVerts[vertInd];

		int relX = x + vertOff.x;
		int relY = y + vertOff.y;
		int relZ = z + vertOff.z;

		VertexWriterManager manager = VertexWriterManager.getCurrentInstance();
		long ptr = manager.getTotalOffset();

		TerrainFormat.writeTerrainVertex(ptr, relX, relY, relZ, u, v, color, lightMap);
		manager.addVertexCounter(TerrainFormat.STRIDE);
	}

	public static int br(int full) {
		return -full & LIGHT_REDUCE;
	}

	public static int full(int blockId) {
		return BlocksFlags.SOLID_LIGHT_MASK[blockId];
	}

	public static final int LIGHT_REDUCE = 70;
	public static final int CORNER_LIGHT = 256 - LIGHT_REDUCE;

	public static int ao(int pos1, int pos2, int corner) {
		pos1 &= 1;
		pos2 &= 1;
		corner &= 1;

		int fullXorP = pos1 ^ pos2;

		int factor = 256;

		if (corner == 1 && fullXorP == 0) {
			factor = CORNER_LIGHT - ((pos1 | pos2) << 5);
		}

		factor -= br(pos1);
		factor -= br(pos2);

		return Math.min(Math.max(factor, 90), 255);
	}

//	public static int lightMap(int light1, int light2, int lightCorner) {
//		light1 = light1 != 0 && ?
//
//	}

	private static Vector2i createVec2i(int x, int y) {
		return new Vector2i(x, y);
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
		final float EPSILON = 0;

		NEG_Y.aoCorner0 = NEG_X_DIR;
		NEG_Y.aoCorner1 = POS_Z_DIR;
		NEG_Y.quadVerts[0] = createVec3i(0, 0, 1);
		NEG_Y.quadVerts[1] = createVec3i(0, 0, 0);
		NEG_Y.quadVerts[2] = createVec3i(1, 0, 0);
		NEG_Y.quadVerts[3] = createVec3i(1, 0, 1);
		NEG_Y.setTexInd(2, 3, 0, 1);

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

		MAP_ID_TO_UV[0] = new Vector2i(0, 1);
		MAP_ID_TO_UV[1] = new Vector2i(0, 3);
		MAP_ID_TO_UV[2] = new Vector2i(2, 3);
		MAP_ID_TO_UV[3] = new Vector2i(2, 1);

		NEG_X.processCornersDir();
		POS_X.processCornersDir();

		NEG_Z.processCornersDir();
		POS_Z.processCornersDir();

		NEG_Y.processCornersDir();
		POS_Y.processCornersDir();

		for (int i = 0; i < Direction.COUNT; i++) {
			SHADE_FULL_COLOR[i] = ColorBGRManager.multiplyColor(0xFF_FF_FF, SIDE_LIGHT_MULTIPLIER[i]);
			SHADE_FULL_FACTOR[i] = (int) (SIDE_LIGHT_MULTIPLIER[i] * 256.0f);
		}
	}
}
