package dev.safixo.client.render.pipelines.terrain.meshing.model;

import dev.safixo.client.render.pipelines.terrain.meshing.data.FacingData;
import dev.safixo.client.util.MathExt;
import net.minecraft.world.IBlockAccess;

import static dev.safixo.client.render.pipelines.terrain.meshing.model.ModelHelper.*;

public class ModelLighter {
	public static int applyLighting(FacingData face, IBlockAccess cache, float[] bounds, int dir,
									 int x, int y, int z, int[] light, int partialSides)
	{
		int dirX = x + face.dirX;
		int dirY = y + face.dirY;
		int dirZ = z + face.dirZ;

		if (cache.isBlockOpaqueCube(dirX, dirY, dirZ)) {
			dirX = x;
			dirY = y;
			dirZ = z;
		}

		int ao = setupCornerLighting(face, cache, dirX, dirY, dirZ, light);

		if ((partialSides & (1 << dir)) != 0) {
			ao = processPartialAlignedLight(face, bounds, ao, light);
		}

		return ao;
	}

	public static int setupCornerLighting(FacingData face, IBlockAccess cache, int x, int y, int z, int[] light) {
		int p1X = face.aoCornerX0;
		int p1Y = face.aoCornerY0;
		int p1Z = face.aoCornerZ0;

		int p2X = face.aoCornerX1;
		int p2Y = face.aoCornerY1;
		int p2Z = face.aoCornerZ1;

		int posZ = getBlockCached(cache, x + p2X, y + p2Y, z + p2Z);
		int negZ = getBlockCached(cache, x - p2X, y - p2Y, z - p2Z);
		int posX = getBlockCached(cache, x + p1X, y + p1Y, z + p1Z);
		int negX = getBlockCached(cache, x - p1X, y - p1Y, z - p1Z);

		int p12X = p1X + p2X;
		int p12Y = p1Y + p2Y;
		int p12Z = p1Z + p2Z;

		int pd12X = p1X - p2X;
		int pd12Y = p1Y - p2Y;
		int pd12Z = p1Z - p2Z;

		int cornerPP = fullFace(posZ & posX) == 0 ? getBlockCached(cache, x + p12X, y + p12Y, z + p12Z) : 1;
		int cornerPN = fullFace(negZ & posX) == 0 ? getBlockCached(cache, x + pd12X, y + pd12Y, z + pd12Z) : 1;
		int cornerNP = fullFace(posZ & negX) == 0 ? getBlockCached(cache, x - pd12X, y - pd12Y, z - pd12Z) : 1;
		int cornerNN = fullFace(negZ & negX) == 0 ? getBlockCached(cache, x - p12X, y - p12Y, z - p12Z) : 1;

		int ao = ao(posZ, posX, cornerPP);
		ao |= ao(negZ, posX, cornerPN) << 8;
		ao |= ao(negZ, negX, cornerNN) << 16;
		ao |= ao(posZ, negX, cornerNP) << 24;

		int lightMap = cache.getLightBrightnessForSkyBlocks(x, y, z, 0);
		light[0] = avg(avgF(lightMap, cornerPP), avg(posZ, posX)); // 0 vertex
		light[1] = avg(avgF(lightMap, cornerPN), avg(posX, negZ)); // 1 vertex
		light[2] = avg(avgF(lightMap, cornerNN), avg(negZ, negX)); // 2 vertex
		light[3] = avg(avgF(lightMap, cornerNP), avg(negX, posZ)); // 3 vertex

		return ao;
	}

	public static int processPartialAlignedLight(FacingData face, float[] bounds, int ao, int[] light) {
		int ao0 = ao & 0xFF;
		int ao1 = ao >> 8 & 0xFF;
		int ao2 = ao >> 16 & 0xFF;
		int ao3 = ao >> 24 & 0xFF;

		long pack0 = light[0] | (long) ao0 << 32L;
		long pack1 = light[1] | (long) ao1 << 32L;
		long pack2 = light[2] | (long) ao2 << 32L;
		long pack3 = light[3] | (long) ao3 << 32L;

		for (int i = 0; i < 4; i++) {
			int ind = i << 1;
			int wx = face.weightIndices[ind];
			int wy = face.weightIndices[ind + 1];

			float uf = wx < 0 ? 1.0f - bounds[-wx] : bounds[wx];
			float vf = wy < 0 ? 1.0f - bounds[-wy] : bounds[wy];

			int u = (int) (uf * 256);
			int v = (int) (vf * 256);

			int w0 = v * u;
			int w1 = v * (256 - u);
			int w2 = (256 - v) * (256 - u);
			int w3 = (256 - v) * u;

			long la0 = w0 * pack0;
			long la1 = w1 * pack1;
			long la2 = w2 * pack2;
			long la3 = w3 * pack3;

			long sum = la0 + la1 + la2 + la3;

			light[i] = (int) (sum >>> 16) & 0xF000F0;
			ao |= (int) ((sum >>> 48) & 0xFF) << (i * 8);
		}

		return ao;
	}
}
