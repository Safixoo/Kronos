package turniplabs.examplemod.client.render.cull;

import org.joml.Math;
import org.joml.Matrix4f;

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
		nxX = m.m03() + m.m00(); nxY = m.m13() + m.m10(); nxZ = m.m23() + m.m20(); nxW = m.m33() + m.m30();
		float invl = Math.invsqrt(nxX * nxX + nxY * nxY + nxZ * nxZ);
		nxX *= invl; nxY *= invl; nxZ *= invl; nxW *= invl;

		double nxW = 0;
		if (nxX >= 0) nxW += 16.0;
		if (nxY >= 0) nxW += 16.0;
		if (nxZ >= 0) nxW += 16.0;

		FrustumCuller.nxW = (float) -(FrustumCuller.nxW + nxW);

		pxX = m.m03() - m.m00(); pxY = m.m13() - m.m10(); pxZ = m.m23() - m.m20(); pxW = m.m33() - m.m30();
		invl = Math.invsqrt(pxX * pxX + pxY * pxY + pxZ * pxZ);
		pxX *= invl; pxY *= invl; pxZ *= invl; pxW *= invl;

		double pxW = 0;
		if (pxX > 0) pxW += 16.0;
		if (pxY > 0) pxW += 16.0;
		if (pxZ > 0) pxW += 16.0;

		FrustumCuller.pxW = (float) -(FrustumCuller.pxW + pxW);

		nyX = m.m03() + m.m01(); nyY = m.m13() + m.m11(); nyZ = m.m23() + m.m21(); nyW = m.m33() + m.m31();
		invl = Math.invsqrt(nyX * nyX + nyY * nyY + nyZ * nyZ);
		nyX *= invl; nyY *= invl; nyZ *= invl; nyW *= invl;

		double nyW = 0;
		if (nyX > 0) nyW += 16.0;
		if (nyY > 0) nyW += 16.0;
		if (nyZ > 0) nyW += 16.0;

		FrustumCuller.nyW = (float) -(FrustumCuller.nyW + nyW);

		pyX = m.m03() - m.m01(); pyY = m.m13() - m.m11(); pyZ = m.m23() - m.m21(); pyW = m.m33() - m.m31();
		invl = Math.invsqrt(pyX * pyX + pyY * pyY + pyZ * pyZ);
		pyX *= invl; pyY *= invl; pyZ *= invl; pyW *= invl;

		double pyW = 0;
		if (pyX > 0) pyW += 16.0;
		if (pyY > 0) pyW += 16.0;
		if (pyZ > 0) pyW += 16.0;

		FrustumCuller.pyW = (float) -(FrustumCuller.pyW + pyW);

		modelViewMatrix.set(modelView);
		projectionMatrix.set(proj);
	}

	public static void addFractToCamera(float fractX, float fractY, float fractZ) {
		{
			nxW -= nxX * fractX;
			nxW -= nxY * fractY;
			nxW -= nxZ * fractZ;

			pxW -= pxX * fractX;
			pxW -= pxY * fractY;
			pxW -= pxZ * fractZ;
		}

		{
			nyW -= nyX * fractX;
			nyW -= nyY * fractY;
			nyW -= nyZ * fractZ;

			pyW -= pyX * fractX;
			pyW -= pyY * fractY;
			pyW -= pyZ * fractZ;
		}
	}

	public static boolean testAab(int minX, int minY, int minZ) {
		return  nxX * minX + nxY * minY + nxZ * minZ > nxW &&
				pxX * minX + pxY * minY + pxZ * minZ > pxW &&
				nyX * minX + nyY * minY + nyZ * minZ > nyW &&
				pyX * minX + pyY * minY + pyZ * minZ > pyW;
	}
}
