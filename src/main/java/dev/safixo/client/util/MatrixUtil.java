package dev.safixo.client.util;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class MatrixUtil {
	public static float transformX(Matrix4f mat, float x, float y, float z) {
		return mat.m00() * x + mat.m10() * y + mat.m20() * z + mat.m30();
	}

	public static float transformY(Matrix4f mat, float x, float y, float z) {
		return mat.m01() * x + mat.m11() * y + mat.m21() * z + mat.m31();
	}

	public static float transformZ(Matrix4f mat, float x, float y, float z) {
		return mat.m02() * x + mat.m12() * y + mat.m22() * z + mat.m32();
	}

	public static float transformX(Matrix4f mat, Vector3f vec) {
		return mat.m00() * vec.x + mat.m10() * vec.y + mat.m20() * vec.z + mat.m30();
	}

	public static float transformY(Matrix4f mat, Vector3f vec) {
		return mat.m01() * vec.x + mat.m11() * vec.y + mat.m21() * vec.z + mat.m31();
	}

	public static float transformZ(Matrix4f mat, Vector3f vec) {
		return mat.m02() * vec.x + mat.m12() * vec.y + mat.m22() * vec.z + mat.m32();
	}
}
