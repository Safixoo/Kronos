package turniplabs.examplemod.client.render.cull;

import org.joml.Matrix4f;

public class FrustumCuller {
	private static double nxX, nxY, nxZ, nxW;
	private static double pxX, pxY, pxZ, pxW;
	private static double nyX, nyY, nyZ, nyW;
	private static double pyX, pyY, pyZ, pyW;
//	private static float nzX, nzY, nzZ, nzW;
//	private static float pzX, pzY, pzZ, pzW;

	public static void set(Matrix4f m) {
		//float invW;
		nxX = m.m03() + m.m00(); nxY = m.m13() + m.m10(); nxZ = m.m23() + m.m20(); nxW = m.m33() + m.m30();

		double nxW = 0;
		if (nxX >= 0) nxW += 16.0;
		if (nxY >= 0) nxW += 16.0;
		if (nxZ >= 0) nxW += 16.0;

		FrustumCuller.nxW = (float) -(FrustumCuller.nxW + nxW);
//		invW = 1.0f / nxW;
//
//		nxX *= invW;
//		nxY *= invW;
//		nxZ *= invW;

		pxX = m.m03() - m.m00(); pxY = m.m13() - m.m10(); pxZ = m.m23() - m.m20(); pxW = m.m33() - m.m30();

		double pxW = 0;
		if (pxX >= 0) pxW += 16.0;
		if (pxY >= 0) pxW += 16.0;
		if (pxZ >= 0) pxW += 16.0;

		FrustumCuller.pxW = (float) -(FrustumCuller.pxW + pxW);
//		invW = 1.0f / pxW;
//
//		pxX *= invW;
//		pxY *= invW;
//		pxZ *= invW;

		nyX = m.m03() + m.m01(); nyY = m.m13() + m.m11(); nyZ = m.m23() + m.m21(); nyW = m.m33() + m.m31();

		double nyW = 0;
		if (nyX >= 0) nyW += 16.0;
		if (nyY >= 0) nyW += 16.0;
		if (nyZ >= 0) nyW += 16.0;

		FrustumCuller.nyW = (float) -(FrustumCuller.nyW + nyW);
//		invW = 1.0f / nyW;
//
//		nyX *= invW;
//		nyY *= invW;
//		nyZ *= invW;

		pyX = m.m03() - m.m01(); pyY = m.m13() - m.m11(); pyZ = m.m23() - m.m21(); pyW = m.m33() - m.m31();

		double pyW = 0;
		if (pyX >= 0) pyW += 16.0;
		if (pyY >= 0) pyW += 16.0;
		if (pyZ >= 0) pyW += 16.0;

		FrustumCuller.pyW = (float) -(FrustumCuller.pyW + pyW);
//		invW = 1.0f / pyW;
//
//		pyX *= invW;
//		pyY *= invW;
//		pyZ *= invW;

//		nzX = m.m03() + m.m02(); nzY = m.m13() + m.m12(); nzZ = m.m23() + m.m22(); nzW = m.m33() + m.m32();
//
//		nzW += nzX >= 0 ? 16.0F : 0.0F;
//		nzW += nzY >= 0 ? 16.0F : 0.0F;
//		nzW += nzZ >= 0 ? 16.0F : 0.0F;
//		invW = 1.0f / nzW;
//
//		nzX *= invW;
//		nzY *= invW;
//		nzZ *= invW;

//		pzX = m.m03() - m.m02(); pzY = m.m13() - m.m12(); pzZ = m.m23() - m.m22(); pzW = m.m33() - m.m32();
//
//		pzW += pzX >= 0 ? 16.0F : 0.0F;
//		pzW += pzY >= 0 ? 16.0F : 0.0F;
//		pzW += pzZ >= 0 ? 16.0F : 0.0F;
//		invW = 1.0f / pzW;
//
//		pzX *= invW;
//		pzY *= invW;
//		pzZ *= invW;
	}

	public static void addFractCamera(float fractX, float fractY, float fractZ) {
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

	public static boolean testAab(float minX, float minY, float minZ) {
		return  nxX * minX + nxY * minY + nxZ * minZ >= nxW &&
				pxX * minX + pxY * minY + pxZ * minZ >= pxW &&
				nyX * minX + nyY * minY + nyZ * minZ >= nyW &&
				pyX * minX + pyY * minY + pyZ * minZ >= pyW;
	}
}
