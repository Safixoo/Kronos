package turniplabs.examplemod.client;

import org.joml.Matrix4f;

public class ComplexFrustum {
	private static float nxX, nxY, nxZ, nxW;
	private static float pxX, pxY, pxZ, pxW;
	private static float nyX, nyY, nyZ, nyW;
	private static float pyX, pyY, pyZ, pyW;
//	private static float nzX, nzY, nzZ, nzW;
//	private static float pzX, pzY, pzZ, pzW;

	public static void set(Matrix4f m) {
		//float invW;

		nxX = m.m03() + m.m00(); nxY = m.m13() + m.m10(); nxZ = m.m23() + m.m20(); nxW = m.m33() + m.m30();

		nxW += nxX >= 0 ? 16.0F : 0.0F;
		nxW += nxY >= 0 ? 16.0F : 0.0F;
		nxW += nxZ >= 0 ? 16.0F : 0.0F;
		nxW = -nxW;
//		invW = 1.0f / nxW;
//
//		nxX *= invW;
//		nxY *= invW;
//		nxZ *= invW;

		pxX = m.m03() - m.m00(); pxY = m.m13() - m.m10(); pxZ = m.m23() - m.m20(); pxW = m.m33() - m.m30();

		pxW += pxX >= 0 ? 16.0F : 0.0F;
		pxW += pxY >= 0 ? 16.0F : 0.0F;
		pxW += pxZ >= 0 ? 16.0F : 0.0F;
		pxW = -pxW;
//		invW = 1.0f / pxW;
//
//		pxX *= invW;
//		pxY *= invW;
//		pxZ *= invW;

		nyX = m.m03() + m.m01(); nyY = m.m13() + m.m11(); nyZ = m.m23() + m.m21(); nyW = m.m33() + m.m31();

		nyW += nyX >= 0 ? 16.0F : 0.0F;
		nyW += nyY >= 0 ? 16.0F : 0.0F;
		nyW += nyZ >= 0 ? 16.0F : 0.0F;
		nyW = -nyW;
//		invW = 1.0f / nyW;
//
//		nyX *= invW;
//		nyY *= invW;
//		nyZ *= invW;

		pyX = m.m03() - m.m01(); pyY = m.m13() - m.m11(); pyZ = m.m23() - m.m21(); pyW = m.m33() - m.m31();

		pyW += pyX >= 0 ? 16.0F : 0.0F;
		pyW += pyY >= 0 ? 16.0F : 0.0F;
		pyW += pyZ >= 0 ? 16.0F : 0.0F;
		pyW = -pyW;
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

	public static boolean testAab(float minX, float minY, float minZ) {
		return  nxX * minX + nxY * minY + nxZ * minZ >= nxW &&
				pxX * minX + pxY * minY + pxZ * minZ >= pxW &&
				nyX * minX + nyY * minY + nyZ * minZ >= nyW &&
				pyX * minX + pyY * minY + pyZ * minZ >= pyW;
	}
}
