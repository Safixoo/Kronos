package dev.safixo.client.render.pipelines.terrain.meshing.vanilla;

import dev.safixo.client.render.pipelines.terrain.meshing.data.FacingRender;
import net.minecraft.world.IBlockAccess;
import org.joml.Vector3i;

import static dev.safixo.client.util.Direction.*;

public class ModelLighter {
	public static void applyLighting(FacingRender face, IBlockAccess cache, float[] bounds,
									 int dir, int x, int y, int z, int[] ao, int[] light, int partialSides)
	{
		setupCornerLighting(face, cache, x, y, z, dir, ao, light);

		if ((partialSides & (1 << dir)) != 0) {
			processPartialAlignedLight(face, dir, bounds, ao, light);
		}
	}

	public static void setupCornerLighting(FacingRender face, IBlockAccess cache, int x, int y, int z,
										   int dir, int[] color, int[] light) {
		int p1X = face.aoCornerX0;
		int p1Y = face.aoCornerY0;
		int p1Z = face.aoCornerZ0;

		int p2X = face.aoCornerX1;
		int p2Y = face.aoCornerY1;
		int p2Z = face.aoCornerZ1;

		int dirX = x + x(dir);
		int dirY = y + y(dir);
		int dirZ = z + z(dir);

		int posZ = ModelHelper.getBlockCached(cache, dirX + p2X, dirY + p2Y, dirZ + p2Z);
		int negZ = ModelHelper.getBlockCached(cache, dirX - p2X, dirY - p2Y, dirZ - p2Z);
		int posX = ModelHelper.getBlockCached(cache, dirX + p1X, dirY + p1Y, dirZ + p1Z);
		int negX = ModelHelper.getBlockCached(cache, dirX - p1X, dirY - p1Y, dirZ - p1Z);

		int p12X = p1X + p2X;
		int p12Y = p1Y + p2Y;
		int p12Z = p1Z + p2Z;

		int pd12X = p1X - p2X;
		int pd12Y = p1Y - p2Y;
		int pd12Z = p1Z - p2Z;

		int cornerPP = ModelHelper.getBlockCacheLazily(cache, dirX + p12X, dirY + p12Y, dirZ + p12Z);
		int cornerPN = ModelHelper.getBlockCacheLazily(cache, dirX + pd12X, dirY + pd12Y, dirZ + pd12Z);

		int lightPP = ModelHelper.fullFace(posZ | posX) == 0 ? ModelHelper.light(cache, dirX + p12X, dirY + p12Y, dirZ + p12Z, cornerPP) : 0;
		int lightPN = ModelHelper.fullFace(negZ | posX) == 0 ? ModelHelper.light(cache, dirX + pd12X, dirY + pd12Y, dirZ + pd12Z, cornerPN) : 0;

		int cornerNP = ModelHelper.getBlockCacheLazily(cache, dirX - pd12X, dirY - pd12Y, dirZ - pd12Z);
		int cornerNN = ModelHelper.getBlockCacheLazily(cache, dirX - p12X, dirY - p12Y, dirZ - p12Z);

		int lightNP = ModelHelper.fullFace(posZ | negX) == 0 ? ModelHelper.light(cache, dirX - pd12X, dirY - pd12Y, dirZ - pd12Z, cornerNP) : 0;
		int lightNN = ModelHelper.fullFace(negZ | negX) == 0 ? ModelHelper.light(cache, dirX - p12X, dirY - p12Y, dirZ - p12Z, cornerNN) : 0;

		color[0] = ModelHelper.ao(posZ, posX, cornerPP);
		color[1] = ModelHelper.ao(negZ, posX, cornerPN);
		color[2] = ModelHelper.ao(negZ, negX, cornerNN);
		color[3] = ModelHelper.ao(posZ, negX, cornerNP);

		int lightMap = cache.getLightBrightnessForSkyBlocks(dirX, dirY, dirZ, 0);

		int lightPZ = ModelHelper.light(posZ);
		int lightPX = ModelHelper.light(posX);

		int lightNZ = ModelHelper.light(negZ);
		int lightNX = ModelHelper.light(negX);

		light[0] = ModelHelper.avg(ModelHelper.avg(lightPP, lightMap), ModelHelper.avg(lightPZ, lightPX)); // 0 vertex
		light[1] = ModelHelper.avg(ModelHelper.avg(lightPN, lightMap), ModelHelper.avg(lightPX, lightNZ)); // 1 vertex
		light[2] = ModelHelper.avg(ModelHelper.avg(lightNN, lightMap), ModelHelper.avg(lightNZ, lightNX)); // 2 vertex
		light[3] = ModelHelper.avg(ModelHelper.avg(lightNP, lightMap), ModelHelper.avg(lightNX, lightPZ)); // 3 vertex
	}

	public static void processPartialAlignedLight(FacingRender face, int dir, float[] bounds, int[] ao, int[] light) {
		float uf;
		float vf;

		for (int i = 0; i < 4; i++) {
			Vector3i vertOff = face.quadVerts[i];
			float x = bounds[vertOff.x];
			float y = bounds[vertOff.y];
			float z = bounds[vertOff.z];

			if (dir == DOWN) {
				uf = z;
				vf = 1.0f - x;
			} else if (dir == UP) {
				uf = z;
				vf = x;
			} else if (dir == NORTH) {
				uf = 1.0f - x;
				vf = y;
			} else if (dir == SOUTH) {
				uf = y;
				vf = 1.0f - x;
			} else if (dir == WEST) {
				uf = z;
				vf = y;
			} else {
				uf = z;
				vf = 1.0f - y;
			}

			long u = (long) (uf * 4096);
			long v = (long) (vf * 4096);

			long w0 = v * u;
			long w1 = v * (4096 - u);
			long w2 = (4096 - v) * (4096 - u);
			long w3 = (4096 - v) * u;

			long l0 = (w0 * light[0]) >> 24;
			long l1 = (w1 * light[1]) >> 24;
			long l2 = (w2 * light[2]) >> 24;
			long l3 = (w3 * light[3]) >> 24;

			light[i] = (int) (l0 + l1 + l2 + l3);

			long a0 = (w0 * ao[0]) >> 24;
			long a1 = (w1 * ao[1]) >> 24;
			long a2 = (w2 * ao[2]) >> 24;
			long a3 = (w3 * ao[3]) >> 24;

			ao[i] = (int) (a0 + a1 + a2 + a3);
		}
	}
}
