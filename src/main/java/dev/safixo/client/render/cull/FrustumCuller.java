package dev.safixo.client.render.cull;

import org.joml.Math;
import org.joml.Matrix4f;

import static org.joml.Math.fma;

public class FrustumCuller {
	public static final Matrix4f projectionMatrix = new Matrix4f();
	public static final Matrix4f modelViewMatrix = new Matrix4f();

	private static float nxX, nxY, nxZ, nxW;
	private static float pxX, pxY, pxZ, pxW;
	private static float nyX, nyY, nyZ, nyW;
	private static float pyX, pyY, pyZ, pyW;
//	private static float nzX, nzY, nzZ, nzW;
//	private static float pzX, pzY, pzZ, pzW;

	public static void processMatrices(Matrix4f proj, Matrix4f modelView, Matrix4f m) {
		double nxX, nxY, nxZ, nxW;
		double pxX, pxY, pxZ, pxW;
		double nyX, nyY, nyZ, nyW;
		double pyX, pyY, pyZ, pyW;

		nxX = (double) m.m03() + (double) m.m00(); nxY = (double) m.m13() + (double) m.m10(); nxZ = (double) m.m23() + (double) m.m20(); nxW = (double) m.m33() + (double) m.m30();
		double invl = Math.invsqrt(nxX * nxX + nxY * nxY + nxZ * nxZ);
		nxX *= invl; nxY *= invl; nxZ *= invl; nxW *= invl;

		if (nxX >= 0) nxW += 16.0;
		if (nxY >= 0) nxW += 16.0;
		if (nxZ >= 0) nxW += 16.0;

		pxX = (double) m.m03() - (double) m.m00(); pxY = (double) m.m13() - (double) m.m10(); pxZ = (double) m.m23() - (double) m.m20(); pxW = (double) m.m33() - (double) m.m30();
		invl = Math.invsqrt(pxX * pxX + pxY * pxY + pxZ * pxZ);
		pxX *= invl; pxY *= invl; pxZ *= invl; pxW *= invl;

		if (pxX > 0) pxW += 16.0;
		if (pxY > 0) pxW += 16.0;
		if (pxZ > 0) pxW += 16.0;

		nyX = (double) m.m03() + (double) m.m01(); nyY = (double) m.m13() + (double) m.m11(); nyZ = (double) m.m23() + (double) m.m21(); nyW = (double) m.m33() + (double) m.m31();
		invl = Math.invsqrt(nyX * nyX + nyY * nyY + nyZ * nyZ);
		nyX *= invl; nyY *= invl; nyZ *= invl; nyW *= invl;

		if (nyX > 0) nyW += 16.0;
		if (nyY > 0) nyW += 16.0;
		if (nyZ > 0) nyW += 16.0;

		pyX = (double) m.m03() - (double) m.m01(); pyY = (double) m.m13() - (double) m.m11(); pyZ = (double) m.m23() - (double) m.m21(); pyW = (double) m.m33() - (double) m.m31();
		invl = Math.invsqrt(pyX * pyX + pyY * pyY + pyZ * pyZ);
		pyX *= invl; pyY *= invl; pyZ *= invl; pyW *= invl;

		if (pyX > 0) pyW += 16.0;
		if (pyY > 0) pyW += 16.0;
		if (pyZ > 0) pyW += 16.0;

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

		modelViewMatrix.set(modelView);
		projectionMatrix.set(proj);
	}

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

	public static boolean testAab(int minX, int minY, int minZ) {
		return  nxX * minX + nxY * minY + nxZ * minZ > nxW &&
				pxX * minX + pxY * minY + pxZ * minZ > pxW &&
				nyX * minX + nyY * minY + nyZ * minZ > nyW &&
				pyX * minX + pyY * minY + pyZ * minZ > pyW;
	}
}
