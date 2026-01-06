package dev.safixo.client.render.pipelines.terrain.meshing.vanilla;

import dev.safixo.client.render.pipelines.terrain.meshing.FullBlockMesher;
import dev.safixo.client.render.pipelines.terrain.meshing.ModelColorizer;
import dev.safixo.client.render.pipelines.terrain.meshing.data.FacingRender;
import dev.safixo.client.util.ColorBGRManager;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.data.PrimitivesFlags;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Icon;
import net.minecraft.world.IBlockAccess;
import org.joml.Vector3i;

import static dev.safixo.client.render.pipelines.terrain.meshing.vanilla.ABBIndexed.*;
import static dev.safixo.client.util.Direction.*;
import static dev.safixo.client.util.Direction.EAST;

public class VanillaBlockMesher {
	private static final float[] UVS = new float[4];
	private static final float[] BOUNDS = new float[7];
	private static final byte[] ROTATIONS = new byte[6];

	@SuppressWarnings("unused") // asm redirected.
	public static boolean renderStandardBlock(RenderBlocks render, Block block, int x, int y, int z) {
		return renderStandardBlock(render, render.blockAccess, block, BOUNDS, x, y, z);
	}

	/**
	 * @return If it has something meshed.
	 */
	public static boolean renderStandardBlock(RenderBlocks blocks, IBlockAccess cache, Block block, float[] bounds, int x, int y, int z) {
		Tessellator tes = Tessellator.instance;

		int drawSet = 0;
		drawSet |= block.shouldSideBeRendered(cache, x, y - 1, z, 0) ? 1 << DOWN : 0;
		drawSet |= block.shouldSideBeRendered(cache, x, y + 1, z, 1) ? 1 << UP : 0;
		drawSet |= block.shouldSideBeRendered(cache, x, y, z - 1, 2) ? 1 << NORTH : 0;

		drawSet |= block.shouldSideBeRendered(cache, x, y, z + 1, 3) ? 1 << SOUTH : 0;
		drawSet |= block.shouldSideBeRendered(cache, x - 1, y, z, 4) ? 1 << WEST : 0;
		drawSet |= block.shouldSideBeRendered(cache, x + 1, y, z, 5) ? 1 << EAST : 0;

		if (drawSet == 0) {
			return false;
		}

		int color = PrimitivesFlags.COLOR_MODULATOR[block.blockID] != ModelColorizer.DEFAULT_COLOR
			? block.colorMultiplier(blocks.blockAccess, x, y, z)
			: 0xFF_FF_FF_FF;

		ABBIndexed.aabbToArray(blocks, BOUNDS);

		byte[] uvRotate = ROTATIONS;
		uvRotate[Direction.DOWN] = (byte) blocks.uvRotateBottom;
		uvRotate[Direction.UP] = (byte) blocks.uvRotateTop;
		uvRotate[Direction.NORTH] = (byte) blocks.uvRotateEast;
		uvRotate[Direction.SOUTH] = (byte) blocks.uvRotateWest;
		uvRotate[Direction.WEST] = (byte) blocks.uvRotateNorth;
		uvRotate[Direction.EAST] = (byte) blocks.uvRotateSouth;

		for (int dir = 0; dir < Direction.COUNT; dir++) {
			if ((drawSet & (1 << dir)) == 0) {
				continue;
			}

			int dirColor;

			if (color != 0xFFFFFF && block.blockID != Block.grass.blockID || dir == UP) {
				dirColor = ColorBGRManager.multiplyColor(color, FullBlockMesher.SHADE_FULL_FACTOR[dir]);
			} else {
				dirColor = FullBlockMesher.SHADE_FULL_COLOR[dir];
			}

			FacingRender face = FACE_RENDER[dir];
			Icon currentTex = block.getBlockTexture(cache, x, y, z, dir);

			UVS[0] = currentTex.getInterpolatedU(face.minUInd(bounds));
			UVS[1] = currentTex.getInterpolatedV(face.maxUInd(bounds));

			UVS[2] = currentTex.getInterpolatedU(face.minVInd(bounds));
			UVS[3] = currentTex.getInterpolatedV(face.maxVInd(bounds));

			int uvRotation = uvRotate[dir];
			renderQuadNoAmbient(tes, face, cache, bounds, dir, x, y, z, dirColor, uvRotation);
		}

		return true;
	}

	public static void renderQuadNoAmbient(Tessellator tes, FacingRender face, IBlockAccess cache, float[] bounds,
										   int dir, int x, int y, int z, int color, int uvRotate) {
		int lightMap = cache.getLightBrightnessForSkyBlocks(x + Direction.x(dir), y + Direction.y(dir), z + Direction.z(dir), 0);

		uvRotate <<= 2;
		final float[] uvs = UVS;
		int uv0 = face.uvData[uvRotate + 0];
		int uv1 = face.uvData[uvRotate + 1];
		int uv2 = face.uvData[uvRotate + 2];
		int uv3 = face.uvData[uvRotate + 3];

		bufferVertex(tes, face, bounds, 0, x, y, z, uvs[uv0 & 0xFFFF], uvs[uv0 >> 16], color, lightMap);
		bufferVertex(tes, face, bounds, 1, x, y, z, uvs[uv1 & 0xFFFF], uvs[uv1 >> 16], color, lightMap);
		bufferVertex(tes, face, bounds, 2, x, y, z, uvs[uv2 & 0xFFFF], uvs[uv2 >> 16], color, lightMap);
		bufferVertex(tes, face, bounds, 3, x, y, z, uvs[uv3 & 0xFFFF], uvs[uv3 >> 16], color, lightMap);
	}

	public static void bufferVertex(Tessellator tes, FacingRender face, float[] bounds, int vertInd,
									int x, int y, int z, float u, float v, int color, int lightMap) {
		Vector3i vertOff = face.quadVerts[vertInd];
		float relX = x + bounds[vertOff.x];
		float relY = y + bounds[vertOff.y];
		float relZ = z + bounds[vertOff.z];

		tes.setColorOpaque_I(color);
		tes.setBrightness(lightMap);
		tes.addVertexWithUV(relX, relY, relZ, u, v);
	}

	public static final FacingRender NEG_Y = new FacingRender();
	public static final FacingRender POS_Y = new FacingRender();

	public static final FacingRender NEG_X = new FacingRender();
	public static final FacingRender POS_X = new FacingRender();

	public static final FacingRender NEG_Z = new FacingRender();
	public static final FacingRender POS_Z = new FacingRender();

	public static final int NEG_Y_DIR = Direction.DOWN;
	public static final int POS_Y_DIR = Direction.UP;

	public static final int NEG_X_DIR = Direction.WEST;
	public static final int POS_X_DIR = Direction.EAST;

	public static final int NEG_Z_DIR = Direction.NORTH;
	public static final int POS_Z_DIR = Direction.SOUTH;

	private static Vector3i createVec3i(int x, int y, int z) {
		return new Vector3i(x, y, z);
	}

	public static final FacingRender[] FACE_RENDER = new FacingRender[]{
		NEG_Y,
		POS_Y,

		NEG_Z,
		POS_Z,

		NEG_X,
		POS_X
	};

	// minU - 0, minV - 1, maxU - 2, maxV - 3.
	static {
		NEG_Y.aoCorner0 = NEG_X_DIR;
		NEG_Y.aoCorner1 = POS_Z_DIR;
		NEG_Y.quadVerts[0] = createVec3i(MIN_X, MIN_Y, MAX_Z);
		NEG_Y.quadVerts[1] = createVec3i(MIN_X, MIN_Y, MIN_Z);
		NEG_Y.quadVerts[2] = createVec3i(MAX_X, MIN_Y, MIN_Z);
		NEG_Y.quadVerts[3] = createVec3i(MAX_X, MIN_Y, MAX_Z);
		NEG_Y.setBoundsTex(MIN_X, MAX_X, MIN_Z, MAX_Z);
		NEG_Y.setTexInd(2, 3, 0, 1);

		POS_Y.aoCorner0 = POS_X_DIR;
		POS_Y.aoCorner1 = POS_Z_DIR;
		POS_Y.quadVerts[0] = createVec3i(MAX_X, MAX_Y, MAX_Z);
		POS_Y.quadVerts[1] = createVec3i(MAX_X, MAX_Y, MIN_Z);
		POS_Y.quadVerts[2] = createVec3i(MIN_X, MAX_Y, MIN_Z);
		POS_Y.quadVerts[3] = createVec3i(MIN_X, MAX_Y, MAX_Z);
		POS_Y.setBoundsTex(MIN_X, MAX_X, MIN_Z, MAX_Z);
		POS_Y.setTexInd(2, 3, 0, 1);

		POS_X.aoCorner0 = NEG_Y_DIR;
		POS_X.aoCorner1 = POS_Z_DIR;
		POS_X.quadVerts[0] = createVec3i(MAX_X, MIN_Y, MAX_Z);
		POS_X.quadVerts[1] = createVec3i(MAX_X, MIN_Y, MIN_Z);
		POS_X.quadVerts[2] = createVec3i(MAX_X, MAX_Y, MIN_Z);
		POS_X.quadVerts[3] = createVec3i(MAX_X, MAX_Y, MAX_Z);
		POS_X.setBoundsTex(MAX_Z, MIN_Z, -MIN_Y, -MAX_Y);
		POS_X.setTexInd(3, 0, 1, 2);

		NEG_X.aoCorner0 = POS_Y_DIR;
		NEG_X.aoCorner1 = POS_Z_DIR;
		NEG_X.quadVerts[0] = createVec3i(MIN_X, MAX_Y, MAX_Z);
		NEG_X.quadVerts[1] = createVec3i(MIN_X, MAX_Y, MIN_Z);
		NEG_X.quadVerts[2] = createVec3i(MIN_X, MIN_Y, MIN_Z);
		NEG_X.quadVerts[3] = createVec3i(MIN_X, MIN_Y, MAX_Z);
		NEG_X.setBoundsTex(MAX_Z, MIN_Z, -MIN_Y, -MAX_Y);
		NEG_X.setTexInd(1, 2, 3, 0);

		POS_Z.aoCorner0 = NEG_X_DIR;
		POS_Z.aoCorner1 = POS_Y_DIR;
		POS_Z.quadVerts[0] = createVec3i(MIN_X, MAX_Y, MAX_Z);
		POS_Z.quadVerts[1] = createVec3i(MIN_X, MIN_Y, MAX_Z);
		POS_Z.quadVerts[2] = createVec3i(MAX_X, MIN_Y, MAX_Z);
		POS_Z.quadVerts[3] = createVec3i(MAX_X, MAX_Y, MAX_Z);
		POS_Z.setBoundsTex(MAX_X, MIN_X, -MIN_Y, -MAX_Y);
		POS_Z.setTexInd(2, 3, 0, 1);

		NEG_Z.aoCorner0 = POS_Y_DIR;
		NEG_Z.aoCorner1 = NEG_X_DIR;
		NEG_Z.quadVerts[0] = createVec3i(MIN_X, MAX_Y, MIN_Z);
		NEG_Z.quadVerts[1] = createVec3i(MAX_X, MAX_Y, MIN_Z);
		NEG_Z.quadVerts[2] = createVec3i(MAX_X, MIN_Y, MIN_Z);
		NEG_Z.quadVerts[3] = createVec3i(MIN_X, MIN_Y, MIN_Z);
		NEG_Z.setBoundsTex(MAX_X, MIN_X, -MIN_Y, -MAX_Y);
		NEG_Z.setTexInd(1, 2, 3, 0);

		NEG_X.processCornersDir();
		POS_X.processCornersDir();

		NEG_Z.processCornersDir();
		POS_Z.processCornersDir();

		NEG_Y.processCornersDir();
		POS_Y.processCornersDir();
	}
}
