package dev.safixo.client.render.pipelines.terrain.cull;


import dev.safixo.client.render.pipelines.clouds.CloudRenderer;
import org.joml.Matrix4f;

import java.nio.FloatBuffer;

public class FrustumCuller {
	public static final Matrix4f projectionMatrix = new Matrix4f();
	public static final Matrix4f modelViewMatrix = new Matrix4f();

	public static FloatBuffer modelViewBuff, projectionBuff;

	private static float nxX, nxY, nxZ, nxW;
	private static float pxX, pxY, pxZ, pxW;
	private static float nyX, nyY, nyZ, nyW;
	private static float pyX, pyY, pyZ, pyW;

	private static float nxWG, pxWG, nyWG, pyWG;

	private static int nxXC, nxZC, nxWC;
	private static int pxXC, pxZC, pxWC;

	/**
	 * Creates a frustum at the style of JOML, it needs to be normalized to keep as much precision as possible
	 * also an extra w is created to fast path some checks in section frustum tests.
	 */
	public static void processMatrices(org.joml.Matrix4f m) {
		double nxX, nxY, nxZ, nxW;
		double pxX, pxY, pxZ, pxW;
		double nyX, nyY, nyZ, nyW;
		double pyX, pyY, pyZ, pyW;

		nxX = (double) m.m03() + (double) m.m00(); nxY = (double) m.m13() + (double) m.m10(); nxZ = (double) m.m23() + (double) m.m20(); nxW = (double) m.m33() + (double) m.m30();
		double invl = invSqrt(nxX * nxX + nxY * nxY + nxZ * nxZ);
		nxX *= invl; nxY *= invl; nxZ *= invl; nxW *= invl;

		nxWG = (float) -nxW;
		if (nxX >= 0) nxW += 16.0 * nxX;
		if (nxY >= 0) nxW += 16.0 * nxY;
		if (nxZ >= 0) nxW += 16.0 * nxZ;

		pxX = (double) m.m03() - (double) m.m00(); pxY = (double) m.m13() - (double) m.m10(); pxZ = (double) m.m23() - (double) m.m20(); pxW = (double) m.m33() - (double) m.m30();
		invl = invSqrt(pxX * pxX + pxY * pxY + pxZ * pxZ);
		pxX *= invl; pxY *= invl; pxZ *= invl; pxW *= invl;

		pxWG = (float) -pxW;
		if (pxX > 0) pxW += 16.0 * pxX;
		if (pxY > 0) pxW += 16.0 * pxY;
		if (pxZ > 0) pxW += 16.0 * pxZ;

		nyX = (double) m.m03() + (double) m.m01(); nyY = (double) m.m13() + (double) m.m11(); nyZ = (double) m.m23() + (double) m.m21(); nyW = (double) m.m33() + (double) m.m31();
		invl = invSqrt(nyX * nyX + nyY * nyY + nyZ * nyZ);
		nyX *= invl; nyY *= invl; nyZ *= invl; nyW *= invl;

		nyWG = (float) -nyW;
		if (nyX > 0) nyW += 16.0 * nyX;
		if (nyY > 0) nyW += 16.0 * nyY;
		if (nyZ > 0) nyW += 16.0 * nyZ;

		pyX = (double) m.m03() - (double) m.m01(); pyY = (double) m.m13() - (double) m.m11(); pyZ = (double) m.m23() - (double) m.m21(); pyW = (double) m.m33() - (double) m.m31();
		invl = invSqrt(pyX * pyX + pyY * pyY + pyZ * pyZ);
		pyX *= invl; pyY *= invl; pyZ *= invl; pyW *= invl;

		pyWG = (float) -pyW;
		if (pyX > 0) pyW += 16.0 * pyX;
		if (pyY > 0) pyW += 16.0 * pyY;
		if (pyZ > 0) pyW += 16.0 * pyZ;

		FrustumCuller.nxX = (float) nxX;
		FrustumCuller.nxY = (float) nxY;
		FrustumCuller.nxZ = (float) nxZ;
		FrustumCuller.nxW = (float) -nxW;

		FrustumCuller.pxX = (float) pxX;
		FrustumCuller.pxY = (float) pxY;
		FrustumCuller.pxZ = (float) pxZ;
		FrustumCuller.pxW = (float) -pxW;

		FrustumCuller.nyX = (float) nyX;
		FrustumCuller.nyY = (float) nyY;
		FrustumCuller.nyZ = (float) nyZ;
		FrustumCuller.nyW = (float) -nyW;

		FrustumCuller.pyX = (float) pyX;
		FrustumCuller.pyY = (float) pyY;
		FrustumCuller.pyZ = (float) pyZ;
		FrustumCuller.pyW = (float) -pyW;
	}

	private static double invSqrt(double a) {
		return 1.0 / Math.sqrt(a);
	}

	/**
	 * Instead of adding the player offset into the coordinates passed to the test in helps
	 * in many occasions to simply pass by the offset ahead of time to simplify ops.
	 */
	public static void addFractToCamera(float fractX, float fractY, float fractZ) {
		{
			nxW += nxX * fractX;
			nxW += nxY * fractY;
			nxW += nxZ * fractZ;

			pxW += pxX * fractX;
			pxW += pxY * fractY;
			pxW += pxZ * fractZ;
		}

		{
			nyW += nyX * fractX;
			nyW += nyY * fractY;
			nyW += nyZ * fractZ;

			pyW += pyX * fractX;
			pyW += pyY * fractY;
			pyW += pyZ * fractZ;
		}
	}

	public static void prepareCloudFrustum(float worldFracX, float viewY, float worldFracZ) {
		int extraRadius = 0;

		double nxXC = nxX;
		double nxZC = nxZ;

		double pxXC = pxX;
		double pxZC = pxZ;

		double nxWC = nxWG;
		nxWC -= (nxX >= 0 ? CloudRenderer.CLOUD_WIDTH + extraRadius : -extraRadius) * nxXC;
		nxWC -= ((nxY >= 0 ? CloudRenderer.CLOUD_HEIGHT + extraRadius : -extraRadius) + viewY) * (double) nxY;
		nxWC -= (nxZ >= 0 ? CloudRenderer.CLOUD_WIDTH + extraRadius : -extraRadius) * nxZC;

		double pxWC = nxWG;
		pxWC -= (pxX >= 0 ? CloudRenderer.CLOUD_WIDTH + extraRadius : -extraRadius) * pxXC;
		pxWC -= ((pxY >= 0 ? CloudRenderer.CLOUD_HEIGHT + extraRadius : -extraRadius) + viewY) * (double) pxY;
		pxWC -= (pxZ >= 0 ? CloudRenderer.CLOUD_WIDTH + extraRadius : -extraRadius) * pxZC;

		nxXC *= CloudRenderer.CLOUD_WIDTH;
		nxZC *= CloudRenderer.CLOUD_WIDTH;
		pxXC *= CloudRenderer.CLOUD_WIDTH;
		pxZC *= CloudRenderer.CLOUD_WIDTH;

		nxWC += (CloudRenderer.MAX_CELL_DISTANCE + worldFracX) * nxXC;
		nxWC += (CloudRenderer.MAX_CELL_DISTANCE + worldFracZ) * nxZC;

		pxWC += (CloudRenderer.MAX_CELL_DISTANCE + worldFracX) * pxXC;
		pxWC += (CloudRenderer.MAX_CELL_DISTANCE + worldFracZ) * pxZC;

		FrustumCuller.nxXC = (int) (nxXC * (1 << 20));
		FrustumCuller.nxZC = (int) (nxZC * (1 << 20));

		FrustumCuller.pxXC = (int) (pxXC * (1 << 20));
		FrustumCuller.pxZC = (int) (pxZC * (1 << 20));

		FrustumCuller.pxWC = (int) (pxWC * (1 << 20));
		FrustumCuller.nxWC = (int) (nxWC * (1 << 20));
	}

	/**
	 * Simple JOML testAbb, based on sign of each component pick a corner of the AABB to check, is less precise
	 * as it takes into account in the wrong way the fract camera position, but should work regardless.
	 */
	public static boolean withinFrustumBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return nxX * (nxX < 0.0F ? minX : maxX) + nxY * (nxY < 0.0F ? minY : maxY) + nxZ * (nxZ < 0.0F ? minZ : maxZ) > nxWG
			&& pxX * (pxX < 0.0F ? minX : maxX) + pxY * (pxY < 0.0F ? minY : maxY) + pxZ * (pxZ < 0.0F ? minZ : maxZ) > pxWG
			&& nyX * (nyX < 0.0F ? minX : maxX) + nyY * (nyY < 0.0F ? minY : maxY) + nyZ * (nyZ < 0.0F ? minZ : maxZ) > nyWG
			&& pyX * (pyX < 0.0F ? minX : maxX) + pyY * (pyY < 0.0F ? minY : maxY) + pyZ * (pyZ < 0.0F ? minZ : maxZ) > pyWG;
	}

	/**
	 * Pre-process the sign of all frustum planes, and adds a complement
	 * to every w component based on the size of the section, the advantage
	 * is that the size of a section is always the same so it is a powerful optimization.
	 */
	public static boolean withinFrustumBounds(float blockX, float blockY, float blockZ) {
		return  nxX * blockX + nxY * blockY + nxZ * blockZ > nxW &&
				pxX * blockX + pxY * blockY + pxZ * blockZ > pxW &&
				nyX * blockX + nyY * blockY + nyZ * blockZ > nyW &&
				pyX * blockX + pyY * blockY + pyZ * blockZ > pyW;
	}

	public static boolean cloudWithinFrustumBounds(int blockX, int blockZ) {
		return nxXC * blockX + nxZC * blockZ > nxWC && pxXC * blockX + pxZC * blockZ > pxWC;
	}
}
