package turniplabs.examplemod.client.render.meshing;

import net.minecraft.client.render.block.color.BlockColor;
import net.minecraft.client.render.block.model.BlockModel;
import net.minecraft.client.render.texture.stitcher.IconCoordinate;
import net.minecraft.core.util.helper.Side;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.render.data.SectionCache;
import turniplabs.examplemod.client.util.BlocksFlags;
import turniplabs.examplemod.client.util.ColorBGRManager;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.render.vertex.VertexWriterManager;
import turniplabs.examplemod.client.render.vertex.writer.TerrainFormat;

public class FullBlockMesher {
	private static final int[] SHADE_FULL_COLOR = new int[Direction.COUNT];
	private static final int[] SHADE_FULL_FACTOR = new int[Direction.COUNT];
	private static final float[] VERT_UVS = new float[4];

	private static final Vector2i[] MAP_ID_TO_UV = new Vector2i[4];

	public static final float[] SIDE_LIGHT_MULTIPLIER = new float[] { 0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F };

	public static void renderFaces(BlockModel<?> model, BlockColor blockColor, SectionCache cache, int x, int y, int z) {
		int meta = cache.getBlockMetadataCenter(x, y, z);
		int color = 0xffffffff;

		for (int dir = 0; dir < Direction.COUNT; dir++) {
			int dirX = x + Direction.x(dir);
			int dirY = y + Direction.y(dir);
			int dirZ = z + Direction.z(dir);

			if (cache.isBlockOpaqueCube(dirX, dirY, dirZ)) {
				continue;
			}

			IconCoordinate tex = model.getBlockTexture(cache, x, y, z, Side.sides[dir]);
			VertexWriterManager.setCurrentInstance(VertexWriterManager.SOLID[dir]);

			boolean colorized = model.shouldSideBeColored(cache, x, y, z, dir, meta);
			FacingRender render = FACE_RENDER[dir];

			if (colorized) {
				if (color == 0xffffffff) {
					color = ColorBGRManager.rgbToBgr(blockColor.getWorldColor(cache, x, y, z));
				}

				renderFace(render, tex, dir, cache, x, y, z, ColorBGRManager.multiplyColor(color, SHADE_FULL_FACTOR[dir]));
			} else {
				renderFace(render, tex, dir, cache, x, y, z, SHADE_FULL_COLOR[dir]);
			}
		}
	}


	public static void renderFace(FacingRender facing, IconCoordinate tex, int dir, SectionCache cache, int x, int y, int z, int color) {
		int p1X = facing.aoCornerX0;
		int p1Y = facing.aoCornerY0;
		int p1Z = facing.aoCornerZ0;

		int p2X = facing.aoCornerX1;
		int p2Y = facing.aoCornerY1;
		int p2Z = facing.aoCornerZ1;

		int dirX = x + Direction.x(dir);
		int dirY = y + Direction.y(dir);
		int dirZ = z + Direction.z(dir);

		int posZ = cache.getBlockId(dirX + p2X, dirY + p2Y, dirZ + p2Z);
		int negZ = cache.getBlockId(dirX - p2X, dirY - p2Y, dirZ - p2Z);

		int posX = cache.getBlockId(dirX + p1X, dirY + p1Y, dirZ + p1Z);
		int negX = cache.getBlockId(dirX - p1X, dirY - p1Y, dirZ - p1Z);

		int p12X = p1X + p2X;
		int p12Y = p1Y + p2Y;
		int p12Z = p1Z + p2Z;

		int pd12X = p1X - p2X;
		int pd12Y = p1Y - p2Y;
		int pd12Z = p1Z - p2Z;

		int cornerPP = cache.getBlockId(dirX + p12X, dirY + p12Y, dirZ + p12Z);
		int cornerPN = cache.getBlockId(dirX + pd12X, dirY + pd12Y, dirZ + pd12Z);
		int cornerNP = cache.getBlockId(dirX - pd12X, dirY - pd12Y, dirZ - pd12Z);
		int cornerNN = cache.getBlockId(dirX - p12X, dirY - p12Y, dirZ - p12Z);

		final float[] uvs = VERT_UVS;

		int color0 = ColorBGRManager.multiplyColor(color, ao(posZ, posX, cornerPP));
		int color1 = ColorBGRManager.multiplyColor(color, ao(negZ, posX, cornerPN));
		int color2 = ColorBGRManager.multiplyColor(color, ao(negZ, negX, cornerNN));
		int color3 = ColorBGRManager.multiplyColor(color, ao(posZ, negX, cornerNP));

		VertexWriterManager.getCurrentInstance().ensureCapacity(TerrainFormat.STRIDE * 4);

		float inverseW = (float) tex.parentAtlas.getInverseWidth();
		float inverseH = (float) tex.parentAtlas.getInverseWidth();

		uvs[0] = tex.iconX * inverseW;
		uvs[1] = tex.iconY * inverseH;
		uvs[2] = (tex.iconX + tex.width) * inverseW;
		uvs[3] = (tex.iconY + tex.height) * inverseH;

		Vector2i uv0 = MAP_ID_TO_UV[facing.uvData[0]];
		Vector2i uv1 = MAP_ID_TO_UV[facing.uvData[1]];
		Vector2i uv2 = MAP_ID_TO_UV[facing.uvData[2]];
		Vector2i uv3 = MAP_ID_TO_UV[facing.uvData[3]];

		if (color0 > color3 || color2 > color1) {
			addVertex(facing, 0, x, y, z, uvs[uv0.x], uvs[uv0.y], color0);
			addVertex(facing, 1, x, y, z, uvs[uv1.x], uvs[uv1.y], color1);
			addVertex(facing, 2, x, y, z, uvs[uv2.x], uvs[uv2.y], color2);
			addVertex(facing, 3, x, y, z, uvs[uv3.x], uvs[uv3.y], color3);
		} else {
			addVertex(facing, 3, x, y, z, uvs[uv3.x], uvs[uv3.y], color3);
			addVertex(facing, 0, x, y, z, uvs[uv0.x], uvs[uv0.y], color0);
			addVertex(facing, 1, x, y, z, uvs[uv1.x], uvs[uv1.y], color1);
			addVertex(facing, 2, x, y, z, uvs[uv2.x], uvs[uv2.y], color2);
		}
	}

	private int getBlockId(SectionCache cache, Vector3i pos, Vector3i off) {
		return cache.getBlockId(pos.x + off.x, pos.y + off.y, pos.z + off.z);
	}

	private static void addVertex(FacingRender facing, int vertInd, int x, int y, int z, float u, float v, int color) {
		Vector3f vertOff = facing.quadVerts[vertInd];

		float relX = x + vertOff.x;
		float relY = y + vertOff.y;
		float relZ = z + vertOff.z;

		VertexWriterManager manager = VertexWriterManager.getCurrentInstance();
		long ptr = manager.getTotalOffset();

		MemoryUtil.memPutFloat(ptr + 0, relX);
		MemoryUtil.memPutFloat(ptr + 4, relY);
		MemoryUtil.memPutFloat(ptr + 8, relZ);

		MemoryUtil.memPutFloat(ptr + 12, u);
		MemoryUtil.memPutFloat(ptr + 16, v);

		MemoryUtil.memPutInt(ptr + 20, color | 0xFF_000000);

		manager.addVertexCounter();
	}

	// Leaves reduce lighting much more than opaque blocks.
	private static final int[] REDUCE = new int[2];

	public static int br(boolean full) {
		return full ? LIGHT_REDUCE : 0;
	}

	public static int full(int blockId) {
		return BlocksFlags.SOLID_LIGHT_MASK[blockId];
	}

	public static final int LIGHT_REDUCE = 95;
	public static final int CORNER_LIGHT = 256 - ((LIGHT_REDUCE * 3) >> 2);

	public static int ao(int pos1, int pos2, int corner) {
		int fullP1 = full(pos1);
		int fullP2 = full(pos2);
		int fullC = full(corner);

		int fullXorP = fullP1 ^ fullP2;

		int factor = fullC == 1 && fullXorP == 0 ? CORNER_LIGHT - ((fullP1 | fullP2) << 8) : 256;

		if (fullC == 0) {
			factor += 20;
		}

		factor -= br(fullP1 == 1);
		factor -= br(fullP2 == 1);

		return Math.min(Math.max(factor, 65), 255);
	}

	private static Vector2i createVec2i(int x, int y) {
		return new Vector2i(x, y);
	}

	private static Vector3f createVec3f(float x, float y, float z) {
		return new Vector3f(x, y, z);
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
		NEG_Y.quadVerts[0] = createVec3f(0, 0, 1);
		NEG_Y.quadVerts[1] = createVec3f(0, 0, 0);
		NEG_Y.quadVerts[2] = createVec3f(1, 0, 0);
		NEG_Y.quadVerts[3] = createVec3f(1, 0, 1);
		NEG_Y.setTexInd(2, 3, 0, 1);

		POS_Y.aoCorner0 = POS_X_DIR;
		POS_Y.aoCorner1 = POS_Z_DIR;
		POS_Y.quadVerts[0] = createVec3f(1, 1, 1);
		POS_Y.quadVerts[1] = createVec3f(1, 1, 0);
		POS_Y.quadVerts[2] = createVec3f(0, 1, 0);
		POS_Y.quadVerts[3] = createVec3f(0, 1, 1);
		POS_Y.setTexInd(2, 3, 0, 1);

		POS_X.aoCorner0 = NEG_Y_DIR;
		POS_X.aoCorner1 = POS_Z_DIR;
		POS_X.quadVerts[0] = createVec3f(1 - EPSILON, 0, 1);
		POS_X.quadVerts[1] = createVec3f(1 - EPSILON, 0, 0);
		POS_X.quadVerts[2] = createVec3f(1 - EPSILON, 1, 0);
		POS_X.quadVerts[3] = createVec3f(1 - EPSILON, 1, 1);
		POS_X.setTexInd(1, 2, 3, 0);

		NEG_X.aoCorner0 = POS_Y_DIR;
		NEG_X.aoCorner1 = POS_Z_DIR;
		NEG_X.quadVerts[0] = createVec3f(0 + EPSILON, 1, 1);
		NEG_X.quadVerts[1] = createVec3f(0 + EPSILON, 1, 0);
		NEG_X.quadVerts[2] = createVec3f(0 + EPSILON, 0, 0);
		NEG_X.quadVerts[3] = createVec3f(0 + EPSILON, 0, 1);
		NEG_X.setTexInd(3, 0, 1, 2);

		POS_Z.aoCorner0 = NEG_X_DIR;
		POS_Z.aoCorner1 = POS_Y_DIR;
		POS_Z.quadVerts[0] = createVec3f(0, 1, 1 - EPSILON);
		POS_Z.quadVerts[1] = createVec3f(0, 0, 1 - EPSILON);
		POS_Z.quadVerts[2] = createVec3f(1, 0, 1 - EPSILON);
		POS_Z.quadVerts[3] = createVec3f(1, 1, 1 - EPSILON);
		POS_Z.setTexInd(0, 1, 2, 3);

		NEG_Z.aoCorner0 = POS_Y_DIR;
		NEG_Z.aoCorner1 = NEG_X_DIR;
		NEG_Z.quadVerts[0] = createVec3f(0, 1, 0 + EPSILON);
		NEG_Z.quadVerts[1] = createVec3f(1, 1, 0 + EPSILON);
		NEG_Z.quadVerts[2] = createVec3f(1, 0, 0 + EPSILON);
		NEG_Z.quadVerts[3] = createVec3f(0, 0, 0 + EPSILON);
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
