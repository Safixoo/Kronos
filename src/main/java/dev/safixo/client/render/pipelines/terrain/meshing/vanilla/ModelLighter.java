package dev.safixo.client.render.pipelines.terrain.meshing.vanilla;

import dev.safixo.client.render.pipelines.terrain.meshing.data.FacingRender;
import net.minecraft.world.IBlockAccess;
import org.joml.Vector2i;

import static dev.safixo.client.util.Direction.*;

public class ModelLighter {
	public static void applyLighting(FacingRender face, IBlockAccess cache, float[] bounds,
									 int dir, int x, int y, int z, int[] ao, int[] light, int partialSides)
	{
		setupCornerLighting(face, cache, x, y, z, ao, light);

		if ((partialSides & (1 << dir)) != 0) {
			processPartialAlignedLight(face, bounds, ao, light);
		}
	}

	public static void setupCornerLighting(FacingRender face, IBlockAccess cache, int x, int y, int z, int[] ao, int[] light) {
		int p1X = face.aoCornerX0;
		int p1Y = face.aoCornerY0;
		int p1Z = face.aoCornerZ0;

		int p2X = face.aoCornerX1;
		int p2Y = face.aoCornerY1;
		int p2Z = face.aoCornerZ1;

		x += face.dirX;
		y += face.dirY;
		z += face.dirZ;

		int posZ = ModelHelper.getBlockCached(cache, x + p2X, y + p2Y, z + p2Z);
		int negZ = ModelHelper.getBlockCached(cache, x - p2X, y - p2Y, z - p2Z);
		int posX = ModelHelper.getBlockCached(cache, x + p1X, y + p1Y, z + p1Z);
		int negX = ModelHelper.getBlockCached(cache, x - p1X, y - p1Y, z - p1Z);

		int p12X = p1X + p2X;
		int p12Y = p1Y + p2Y;
		int p12Z = p1Z + p2Z;

		int pd12X = p1X - p2X;
		int pd12Y = p1Y - p2Y;
		int pd12Z = p1Z - p2Z;

		int cornerPP = ModelHelper.getBlockCacheLazily(cache, x + p12X, y + p12Y, z + p12Z);
		int cornerPN = ModelHelper.getBlockCacheLazily(cache, x + pd12X, y + pd12Y, z + pd12Z);

		int lightPP = ModelHelper.fullFace(posZ | posX) == 0 ? ModelHelper.light(cache, x + p12X, y + p12Y, z + p12Z, cornerPP) : 0;
		int lightPN = ModelHelper.fullFace(negZ | posX) == 0 ? ModelHelper.light(cache, x + pd12X, y + pd12Y, z + pd12Z, cornerPN) : 0;

		int cornerNP = ModelHelper.getBlockCacheLazily(cache, x - pd12X, y - pd12Y, z - pd12Z);
		int cornerNN = ModelHelper.getBlockCacheLazily(cache, x - p12X, y - p12Y, z - p12Z);

		int lightNP = ModelHelper.fullFace(posZ | negX) == 0 ? ModelHelper.light(cache, x - pd12X, y - pd12Y, z - pd12Z, cornerNP) : 0;
		int lightNN = ModelHelper.fullFace(negZ | negX) == 0 ? ModelHelper.light(cache, x - p12X, y - p12Y, z - p12Z, cornerNN) : 0;

		ao[0] = ModelHelper.ao(posZ, posX, cornerPP);
		ao[1] = ModelHelper.ao(negZ, posX, cornerPN);
		ao[2] = ModelHelper.ao(negZ, negX, cornerNN);
		ao[3] = ModelHelper.ao(posZ, negX, cornerNP);

		int lightMap = cache.getLightBrightnessForSkyBlocks(x, y, z, 0);

		int lightPZ = ModelHelper.light(posZ);
		int lightPX = ModelHelper.light(posX);

		int lightNZ = ModelHelper.light(negZ);
		int lightNX = ModelHelper.light(negX);

		light[0] = ModelHelper.avg(ModelHelper.avg(lightPP, lightMap), ModelHelper.avg(lightPZ, lightPX)); // 0 vertex
		light[1] = ModelHelper.avg(ModelHelper.avg(lightPN, lightMap), ModelHelper.avg(lightPX, lightNZ)); // 1 vertex
		light[2] = ModelHelper.avg(ModelHelper.avg(lightNN, lightMap), ModelHelper.avg(lightNZ, lightNX)); // 2 vertex
		light[3] = ModelHelper.avg(ModelHelper.avg(lightNP, lightMap), ModelHelper.avg(lightNX, lightPZ)); // 3 vertex
	}

	public static void processPartialAlignedLight(FacingRender face, float[] bounds, int[] ao, int[] light) {
		long pack0 = light[0] | (long) ao[0] << 32L;
		long pack1 = light[1] | (long) ao[1] << 32L;
		long pack2 = light[2] | (long) ao[2] << 32L;
		long pack3 = light[3] | (long) ao[3] << 32L;

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

			light[i] = (int) (sum >>> 16);
			ao[i] =    (int) (sum >>> 48);
		}
	}
}
