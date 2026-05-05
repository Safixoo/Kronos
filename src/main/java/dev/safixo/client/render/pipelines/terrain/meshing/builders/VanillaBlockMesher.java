package dev.safixo.client.render.pipelines.terrain.meshing.builders;

import dev.safixo.client.render.pipelines.terrain.meshing.model.ModelColorizer;
import dev.safixo.client.render.pipelines.terrain.meshing.data.FacingData;
import dev.safixo.client.render.pipelines.terrain.meshing.model.ModelHelper;
import dev.safixo.client.render.pipelines.terrain.meshing.model.ModelLighter;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.render.vertex.writers.TerrainFormat;
import dev.safixo.client.util.AtlasSpriteUnsafe;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.data.PrimitivesFlags;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Icon;
import net.minecraft.world.IBlockAccess;
import org.joml.Vector3i;

import static dev.safixo.client.render.pipelines.terrain.meshing.model.ModelHelper.*;
import static dev.safixo.client.util.ColorBGRManager.*;
import static dev.safixo.client.util.Direction.*;
import static dev.safixo.client.util.Direction.EAST;

@SuppressWarnings("unused") // asm redirected.
public class VanillaBlockMesher {
	private static final float[] UVS = new float[4];

	private static final float[] BOUNDS = new float[SIZE];
	private static final byte[] ROTATIONS = new byte[COUNT];

	private static final int[] LIGHT = new int[4];
	private static final int[] AO = new int[4];

	public static boolean renderStandardBlock(RenderBlocks render, Block block, int x, int y, int z) {
		return renderStandardBlock(render, render.blockAccess, block, BOUNDS, x, y, z);
	}

	/**
	 * Total rewrite of the pipeline for rendering most of the blocks of the game. The main improvements that come
	 * are lighting fixes, much better performance and improved culling by analyzing block model, also the code should
	 * be much more readable and compact in contrast to the inlined field hell that RenderBlocks has which doesn't help
	 * performance.
	 */
	public static boolean renderStandardBlock(RenderBlocks blocks, IBlockAccess cache, Block block, float[] bounds, int x, int y, int z) {
		Tessellator tes = Tessellator.instance;

		int drawSet = 0;
		if (block.shouldSideBeRendered(cache, x, y - 1, z, 0)) drawSet |= 1 << DOWN;
		if (block.shouldSideBeRendered(cache, x, y + 1, z, 1)) drawSet |= 1 << UP;
		if (block.shouldSideBeRendered(cache, x, y, z - 1, 2)) drawSet |= 1 << NORTH;

		if (block.shouldSideBeRendered(cache, x, y, z + 1, 3)) drawSet |= 1 << SOUTH;
		if (block.shouldSideBeRendered(cache, x - 1, y, z, 4)) drawSet |= 1 << WEST;
		if (block.shouldSideBeRendered(cache, x + 1, y, z, 5)) drawSet |= 1 << EAST;

		// Early exit.
		if (drawSet == 0b0) {
			return false;
		}

		int color = PrimitivesFlags.COLOR_MODULATOR[block.blockID] != ModelColorizer.DEFAULT_COLOR
			? block.colorMultiplier(blocks.blockAccess, x, y, z)
			: 0xFFFFFF;

		boolean ao = Minecraft.isAmbientOcclusionEnabled();
		int flag = ModelHelper.processModel(blocks, BOUNDS, blocks.partialRenderBounds && ao);

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

			if (color != 0xFFFFFF && block != Block.grass || dir == UP) {
				dirColor = multiplyColor(color, VoxelMesher.SHADE_FULL_FACTOR[dir]);
			} else {
				dirColor = VoxelMesher.SHADE_FULL_COLOR[dir];
			}

			Icon currentTex = blocks.overrideBlockTexture != null ? blocks.overrideBlockTexture : block.getBlockTexture(cache, x, y, z, dir);

			float minU = AtlasSpriteUnsafe.minU(currentTex);
			float minV = AtlasSpriteUnsafe.minV(currentTex);
			float maxU = AtlasSpriteUnsafe.maxU(currentTex) - minU;
			float maxV = AtlasSpriteUnsafe.maxV(currentTex) - minV;
			FacingData face = FACE_RENDER[dir];

			// TODO: The methods names are incorrect and misleading, fix it.
			UVS[0] = minU + maxU * face.minUInd(bounds);
			UVS[1] = minV + maxV * face.maxUInd(bounds);
			UVS[2] = minU + maxU * face.minVInd(bounds);
			UVS[3] = minV + maxV * face.maxVInd(bounds);

			int uvRotation = uvRotate[dir];

			if (ao) {
				renderQuadYesAmbient(tes, face, cache, bounds, dir, x, y, z, dirColor, uvRotation, flag);
			} else {
				renderQuadNoAmbient(tes, face, cache, bounds, dir, x, y, z, dirColor, uvRotation);
			}
		}

		return true;
	}

	public static void renderQuadNoAmbient(Tessellator tes, FacingData face, IBlockAccess cache, float[] bounds,
                                           int dir, int x, int y, int z, int color, int uvRotate) {
		int lightMap = cache.getLightBrightnessForSkyBlocks(x + face.dirX, y + face.dirY, z + face.dirZ, 0);

		uvRotate <<= 2;
		final float[] uvs = UVS;
		int uv0 = face.uvData[uvRotate + 0];
		int uv1 = face.uvData[uvRotate + 1];
		int uv2 = face.uvData[uvRotate + 2];
		int uv3 = face.uvData[uvRotate + 3];

		bufferVertex(tes, face, bounds, 0 * 12, x, y, z, uvs[uv0 & 0xFF], uvs[uv0 >>> 8], color, lightMap);
		bufferVertex(tes, face, bounds, 1 * 12, x, y, z, uvs[uv1 & 0xFF], uvs[uv1 >>> 8], color, lightMap);
		bufferVertex(tes, face, bounds, 2 * 12, x, y, z, uvs[uv2 & 0xFF], uvs[uv2 >>> 8], color, lightMap);
		bufferVertex(tes, face, bounds, 3 * 12, x, y, z, uvs[uv3 & 0xFF], uvs[uv3 >>> 8], color, lightMap);
	}

	public static void renderQuadYesAmbient(Tessellator tes, FacingData face, IBlockAccess cache, float[] bounds,
                                            int dir, int x, int y, int z, int color, int uvRotate, int flag) {
		int ao = ModelLighter.applyLighting(face, cache, BOUNDS, dir, x, y, z, LIGHT, flag);

		uvRotate <<= 2;
		final float[] uvs = UVS;
		int uv0 = face.uvData[uvRotate + 0];
		int uv1 = face.uvData[uvRotate + 1];
		int uv2 = face.uvData[uvRotate + 2];
		int uv3 = face.uvData[uvRotate + 3];

		int ao0 = ao & 0xFF;
		int ao1 = ao >> 8 & 0xFF;
		int ao2 = ao >> 16 & 0xFF;
		int ao3 = ao >> 24 & 0xFF;

		bufferVertex(tes, face, bounds, 0 * 12, x, y, z, uvs[uv0 & 0xFF], uvs[uv0 >>> 8], multiplyColor(color, ao0), LIGHT[0]);
		bufferVertex(tes, face, bounds, 1 * 12, x, y, z, uvs[uv1 & 0xFF], uvs[uv1 >>> 8], multiplyColor(color, ao1), LIGHT[1]);
		bufferVertex(tes, face, bounds, 2 * 12, x, y, z, uvs[uv2 & 0xFF], uvs[uv2 >>> 8], multiplyColor(color, ao2), LIGHT[2]);
		bufferVertex(tes, face, bounds, 3 * 12, x, y, z, uvs[uv3 & 0xFF], uvs[uv3 >>> 8], multiplyColor(color, ao3), LIGHT[3]);
	}

	public static void bufferVertex(Tessellator tes, FacingData face, float[] bounds, int vertInd,
                                    int x, int y, int z, float u, float v, int color, int lightMap) {
		int vertOff = (int) (face.quadVert >>> vertInd);
		float relX = x + bounds[vertOff & 0xF];
		vertOff >>= 4;
		float relY = y + bounds[vertOff & 0xF];
		vertOff >>= 4;
		float relZ = z + bounds[vertOff & 0xF];

		tes.setColorOpaque_I(color);
		tes.setBrightness(lightMap);
		tes.addVertexWithUV(relX, relY, relZ, u, v);
	}

	public static final FacingData NEG_Y = new FacingData();
	public static final FacingData POS_Y = new FacingData();

	public static final FacingData NEG_X = new FacingData();
	public static final FacingData POS_X = new FacingData();

	public static final FacingData NEG_Z = new FacingData();
	public static final FacingData POS_Z = new FacingData();

	public static final int NEG_Y_DIR = Direction.DOWN;
	public static final int POS_Y_DIR = Direction.UP;

	public static final int NEG_X_DIR = Direction.WEST;
	public static final int POS_X_DIR = Direction.EAST;

	public static final int NEG_Z_DIR = Direction.NORTH;
	public static final int POS_Z_DIR = Direction.SOUTH;

	private static Vector3i createVec3i(int x, int y, int z) {
		return new Vector3i(x, y, z);
	}

	public static final FacingData[] FACE_RENDER = new FacingData[]{
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
		NEG_Y.setQuadVerts(0, createVec3i(MIN_X, MIN_Y, MAX_Z));
		NEG_Y.setQuadVerts(1, createVec3i(MIN_X, MIN_Y, MIN_Z));
		NEG_Y.setQuadVerts(2, createVec3i(MAX_X, MIN_Y, MIN_Z));
		NEG_Y.setQuadVerts(3, createVec3i(MAX_X, MIN_Y, MAX_Z));
		NEG_Y.setBoundsTex(MIN_X, MAX_X, MIN_Z, MAX_Z);
		NEG_Y.setTexInd(2, 3, 0, 1);

		POS_Y.aoCorner0 = POS_X_DIR;
		POS_Y.aoCorner1 = POS_Z_DIR;
		POS_Y.setQuadVerts(0, createVec3i(MAX_X, MAX_Y, MAX_Z));
		POS_Y.setQuadVerts(1, createVec3i(MAX_X, MAX_Y, MIN_Z));
		POS_Y.setQuadVerts(2, createVec3i(MIN_X, MAX_Y, MIN_Z));
		POS_Y.setQuadVerts(3, createVec3i(MIN_X, MAX_Y, MAX_Z));
		POS_Y.setBoundsTex(MIN_X, MAX_X, MIN_Z, MAX_Z);
		POS_Y.setTexInd(2, 3, 0, 1);

		POS_X.aoCorner0 = NEG_Y_DIR;
		POS_X.aoCorner1 = POS_Z_DIR;
		POS_X.setQuadVerts(0, createVec3i(MAX_X, MIN_Y, MAX_Z));
		POS_X.setQuadVerts(1, createVec3i(MAX_X, MIN_Y, MIN_Z));
		POS_X.setQuadVerts(2, createVec3i(MAX_X, MAX_Y, MIN_Z));
		POS_X.setQuadVerts(3, createVec3i(MAX_X, MAX_Y, MAX_Z));
		POS_X.setBoundsTex(MAX_Z, MIN_Z, -MIN_Y, -MAX_Y);
		POS_X.setTexInd(3, 0, 1, 2);

		NEG_X.aoCorner0 = POS_Y_DIR;
		NEG_X.aoCorner1 = POS_Z_DIR;
		NEG_X.setQuadVerts(0, createVec3i(MIN_X, MAX_Y, MAX_Z));
		NEG_X.setQuadVerts(1, createVec3i(MIN_X, MAX_Y, MIN_Z));
		NEG_X.setQuadVerts(2, createVec3i(MIN_X, MIN_Y, MIN_Z));
		NEG_X.setQuadVerts(3, createVec3i(MIN_X, MIN_Y, MAX_Z));
		NEG_X.setBoundsTex(MAX_Z, MIN_Z, -MIN_Y, -MAX_Y);
		NEG_X.setTexInd(1, 2, 3, 0);

		POS_Z.aoCorner0 = NEG_X_DIR;
		POS_Z.aoCorner1 = POS_Y_DIR;
		POS_Z.setQuadVerts(0, createVec3i(MIN_X, MAX_Y, MAX_Z));
		POS_Z.setQuadVerts(1, createVec3i(MIN_X, MIN_Y, MAX_Z));
		POS_Z.setQuadVerts(2, createVec3i(MAX_X, MIN_Y, MAX_Z));
		POS_Z.setQuadVerts(3, createVec3i(MAX_X, MAX_Y, MAX_Z));
		POS_Z.setBoundsTex(MAX_X, MIN_X, -MIN_Y, -MAX_Y);
		POS_Z.setTexInd(2, 3, 0, 1);

		NEG_Z.aoCorner0 = POS_Y_DIR;
		NEG_Z.aoCorner1 = NEG_X_DIR;
		NEG_Z.setQuadVerts(0, createVec3i(MIN_X, MAX_Y, MIN_Z));
		NEG_Z.setQuadVerts(1, createVec3i(MAX_X, MAX_Y, MIN_Z));
		NEG_Z.setQuadVerts(2, createVec3i(MAX_X, MIN_Y, MIN_Z));
		NEG_Z.setQuadVerts(3, createVec3i(MIN_X, MIN_Y, MIN_Z));
		NEG_Z.setBoundsTex(MAX_X, MIN_X, -MIN_Y, -MAX_Y);
		NEG_Z.setTexInd(1, 2, 3, 0);

		NEG_X.processCornersDir(WEST);
		POS_X.processCornersDir(EAST);

		NEG_Z.processCornersDir(NORTH);
		POS_Z.processCornersDir(SOUTH);

		NEG_Y.processCornersDir(DOWN);
		POS_Y.processCornersDir(UP);
	}
}
