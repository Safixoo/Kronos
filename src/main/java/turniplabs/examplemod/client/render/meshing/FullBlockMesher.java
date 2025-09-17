package turniplabs.examplemod.client.render.meshing;

import net.minecraft.client.render.block.model.BlockModel;
import net.minecraft.client.render.texture.stitcher.IconCoordinate;
import net.minecraft.core.util.helper.Side;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3i;
import org.joml.Vector4i;
import turniplabs.examplemod.client.render.data.SectionCache;
import turniplabs.examplemod.client.util.BlocksFlags;
import turniplabs.examplemod.client.util.ColorBGRManager;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.vertex.VertexWriterManager;
import turniplabs.examplemod.client.vertex.writer.TerrainVertexWriter;

public class FullBlockMesher {
	private static final int[] SHADE_FULL_COLOR = new int[Direction.COUNT];
	private static final int[] VERT_COLOR = new int[4];
	private static final float[] VERT_UVS = new float[4];

	private static final Vector2i[] MAP_ID_TO_UV = new Vector2i[4];

	public static void renderFaces(BlockModel<?> model, SectionCache cache, int x, int y, int z, int bitMask) {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			IconCoordinate tex = model.getBlockTexture(cache, x, y, z, Side.sides[dir]);

			int dirX = Direction.x(dir);
			int dirY = Direction.y(dir);
			int dirZ = Direction.z(dir);

			VertexWriterManager.setCurrentInstance(VertexWriterManager.SOLID[dir]);

			if ((bitMask & (1 << dir)) != 0) {
				renderFace(FACE_RENDER[dir], tex, dir, cache, x + dirX, y + dirY, z + dirZ, x, y, z);
			}
		}
	}

	public static void renderFace(FacingRender facing, IconCoordinate tex, int dir, SectionCache cache, int dirX, int dirY, int dirZ, int x, int y, int z) {
		int shade = SHADE_FULL_COLOR[dir];

		int offP1 = facing.aoCorners[0];
		int offP2 = facing.aoCorners[1];

		int p1X = Direction.x(offP1);
		int p1Y = Direction.y(offP1);
		int p1Z = Direction.z(offP1);

		int p2X = Direction.x(offP2);
		int p2Y = Direction.y(offP2);
		int p2Z = Direction.z(offP2);

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

		final int[] rgb = VERT_COLOR;
		final float[] uvs = VERT_UVS;

		rgb[0] = ColorBGRManager.multiplyColor(shade, ao(posZ, posX, cornerPP));
		rgb[1] = ColorBGRManager.multiplyColor(shade, ao(negZ, posX, cornerPN));
		rgb[2] = ColorBGRManager.multiplyColor(shade, ao(negZ, negX, cornerNN));
		rgb[3] = ColorBGRManager.multiplyColor(shade, ao(posZ, negX, cornerNP));

		int extraOff = (rgb[0] > rgb[3] || rgb[2] > rgb[1]) ? 0 : 1;
		VertexWriterManager.getCurrentInstance().ensureCapacity(TerrainVertexWriter.STRIDE * 4);

		float inverseW = (float) tex.parentAtlas.getInverseWidth();
		float inverseH = (float) tex.parentAtlas.getInverseWidth();

		uvs[0] = tex.iconX * inverseW;
		uvs[1] = tex.iconY * inverseH;
		uvs[2] = (tex.iconX + tex.width) * inverseW;
		uvs[3] = (tex.iconY + tex.height) * inverseH;

		for (int vertInd = 0; vertInd < 4; vertInd++) {
			int realInd = (vertInd + extraOff) & 3;
			Vector2i texId = MAP_ID_TO_UV[facing.uvData[realInd]];
			addVertex(facing, realInd, x, y, z, uvs[texId.x], uvs[texId.y], rgb[realInd]);
		}
	}

	private int getBlockId(SectionCache cache, Vector3i pos, Vector3i off) {
		return cache.getBlockId(pos.x + off.x, pos.y + off.y, pos.z + off.z);
	}

	private static void addVertex(FacingRender facing, int vertInd, int x, int y, int z, float u, float v, int color) {
		Vector3i vertOff = facing.quadVerts[vertInd];

		float relX = x + vertOff.x;
		float relY = y + vertOff.y;
		float relZ = z + vertOff.z;

		VertexWriterManager manager = VertexWriterManager.getCurrentInstance();

		manager.setPos(relX, relY, relZ);
		manager.setUv(u, v);
		manager.setColor(color);

		manager.addVertex();
	}

	public static int br(boolean full) {
		return full ? (int) (0.22f * 256.0f) : 0;
	}

	public static boolean full(int blockId) {
		return BlocksFlags.SOLID[blockId];
	}

	public static int ao(int pos1, int pos2, int corner) {
		boolean fullP1 = full(pos1);
		boolean fullP2 = full(pos2);
		boolean fullC = full(corner);

		int factor = fullC && ((!fullP1 && !fullP2) || (fullP1 && fullP2)) ? (int) (0.78f * 256.0f) : 256;

		factor -= br(fullP1);
		factor -= br(fullP2);

		return Math.max(factor, 129);
	}

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
		NEG_Y.aoCorners[0] = NEG_X_DIR;
		NEG_Y.aoCorners[1] = POS_Z_DIR;
		NEG_Y.quadVerts[0] = createVec3i(0, 0, 1);
		NEG_Y.quadVerts[1] = createVec3i(0, 0, 0);
		NEG_Y.quadVerts[2] = createVec3i(1, 0, 0);
		NEG_Y.quadVerts[3] = createVec3i(1, 0, 1);
		NEG_Y.setTexInd(2, 3, 0, 1);

		POS_Y.aoCorners[0] = POS_X_DIR;
		POS_Y.aoCorners[1] = POS_Z_DIR;
		POS_Y.quadVerts[0] = createVec3i(1, 1, 1);
		POS_Y.quadVerts[1] = createVec3i(1, 1, 0);
		POS_Y.quadVerts[2] = createVec3i(0, 1, 0);
		POS_Y.quadVerts[3] = createVec3i(0, 1, 1);
		POS_Y.setTexInd(3, 0, 1, 2);

		POS_X.aoCorners[0] = NEG_Y_DIR;
		POS_X.aoCorners[1] = POS_Z_DIR;
		POS_X.quadVerts[0] = createVec3i(1, 0, 1);
		POS_X.quadVerts[1] = createVec3i(1, 0, 0);
		POS_X.quadVerts[2] = createVec3i(1, 1, 0);
		POS_X.quadVerts[3] = createVec3i(1, 1, 1);
		POS_X.setTexInd(1, 2, 3, 0);

		NEG_X.aoCorners[0] = POS_Y_DIR;
		NEG_X.aoCorners[1] = POS_Z_DIR;
		NEG_X.quadVerts[0] = createVec3i(0, 1, 1);
		NEG_X.quadVerts[1] = createVec3i(0, 1, 0);
		NEG_X.quadVerts[2] = createVec3i(0, 0, 0);
		NEG_X.quadVerts[3] = createVec3i(0, 0, 1);
		NEG_X.setTexInd(3, 0, 1, 2);

		POS_Z.aoCorners[0] = NEG_X_DIR;
		POS_Z.aoCorners[1] = POS_Y_DIR;
		POS_Z.quadVerts[0] = createVec3i(0, 1, 1);
		POS_Z.quadVerts[1] = createVec3i(0, 0, 1);
		POS_Z.quadVerts[2] = createVec3i(1, 0, 1);
		POS_Z.quadVerts[3] = createVec3i(1, 1, 1);
		POS_Z.setTexInd(0, 1, 2, 3);

		NEG_Z.aoCorners[0] = POS_Y_DIR;
		NEG_Z.aoCorners[1] = NEG_X_DIR;
		NEG_Z.quadVerts[0] = createVec3i(0, 1, 0);
		NEG_Z.quadVerts[1] = createVec3i(1, 1, 0);
		NEG_Z.quadVerts[2] = createVec3i(1, 0, 0);
		NEG_Z.quadVerts[3] = createVec3i(0, 0, 0);
		NEG_Z.setTexInd(3, 0, 1, 2);


		MAP_ID_TO_UV[0] = new Vector2i(0, 1);
		MAP_ID_TO_UV[1] = new Vector2i(0, 3);
		MAP_ID_TO_UV[2] = new Vector2i(2, 3);
		MAP_ID_TO_UV[3] = new Vector2i(2, 1);

		for (int i = 0; i < Direction.COUNT; i++) {
			SHADE_FULL_COLOR[i] = ColorBGRManager.multiplyColor(0xFF_FF_FF, BlockRenderer.SIDE_LIGHT_MULTIPLIER[i]);
		}
	}
}
