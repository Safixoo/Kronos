package dev.safixo.client.render.util.math;

import dev.safixo.client.render.util.MathExt;
import dev.safixo.client.render.util.memory.UnsafeUtil;
import org.lwjgl.MemoryUtil;

import java.nio.FloatBuffer;

// Naive port of JOML.
public class Matrix4f {
	public float m00, m01, m02, m03;
	public float m10, m11, m12, m13;
	public float m20, m21, m22, m23;
	public float m30, m31, m32, m33;

	public Matrix4f transpose(Matrix4f dest) {
		float nm10 = m01;
		float nm20 = m02;
		float nm21 = m12;
		float nm30 = m03;
		float nm31 = m13;
		float nm32 = m23;
		dest.m01 = m10;
		dest.m02 = m20;
		dest.m03 = m30;
		dest.m10 = nm10;
		dest.m12 = m21;
		dest.m13 = m31;
		dest.m20 = nm20;
		dest.m21 = nm21;
		dest.m23 = m32;
		dest.m30 = nm30;
		dest.m31 = nm31;
		dest.m32 = nm32;
		return dest;
	}

	public Matrix4f set(Matrix4f dest) {
		dest.m00 = m00;
		dest.m01 = m01;
		dest.m02 = m02;
		dest.m03 = m03;
		dest.m10 = m10;
		dest.m11 = m11;
		dest.m12 = m12;
		dest.m13 = m13;
		dest.m20 = m20;
		dest.m21 = m21;
		dest.m22 = m22;
		dest.m23 = m23;
		dest.m30 = m30;
		dest.m31 = m31;
		dest.m32 = m32;
		dest.m33 = m33;
		return this;
	}

	public Matrix4f mulAffineFma(Matrix4f right) {
		float nm00 = MathExt.fma(m00, right.m00, MathExt.fma(m10, right.m01, m20 * right.m02));
		float nm01 = MathExt.fma(m01, right.m00, MathExt.fma(m11, right.m01, m21 * right.m02));
		float nm02 = MathExt.fma(m02, right.m00, MathExt.fma(m12, right.m01, m22 * right.m02));
		float nm10 = MathExt.fma(m00, right.m10, MathExt.fma(m10, right.m11, m20 * right.m12));
		float nm11 = MathExt.fma(m01, right.m10, MathExt.fma(m11, right.m11, m21 * right.m12));
		float nm12 = MathExt.fma(m02, right.m10, MathExt.fma(m12, right.m11, m22 * right.m12));
		float nm20 = MathExt.fma(m00, right.m20, MathExt.fma(m10, right.m21, m20 * right.m22));
		float nm21 = MathExt.fma(m01, right.m20, MathExt.fma(m11, right.m21, m21 * right.m22));
		float nm22 = MathExt.fma(m02, right.m20, MathExt.fma(m12, right.m21, m22 * right.m22));
		float nm30 = MathExt.fma(m00, right.m30, MathExt.fma(m10, right.m31, MathExt.fma(m20, right.m32, m30)));
		float nm31 = MathExt.fma(m01, right.m30, MathExt.fma(m11, right.m31, MathExt.fma(m21, right.m32, m31)));
		float nm32 = MathExt.fma(m02, right.m30, MathExt.fma(m12, right.m31, MathExt.fma(m22, right.m32, m32)));
		m00 = nm00;
		m01 = nm01;
		m02 = nm02;
		m03 = 0.0f;
		m10 = nm10;
		m11 = nm11;
		m12 = nm12;
		m13 = 0.0f;
		m20 = nm20;
		m21 = nm21;
		m22 = nm22;
		m23 = 0.0f;
		m30 = nm30;
		m31 = nm31;
		m32 = nm32;
		m33 = 1.0f;
		return this;
	}

	public Matrix4f invert(Matrix4f dest) {
		float a = m00 * m11 - m01 * m10;
		float b = m00 * m12 - m02 * m10;
		float c = m00 * m13 - m03 * m10;
		float d = m01 * m12 - m02 * m11;
		float e = m01 * m13 - m03 * m11;
		float f = m02 * m13 - m03 * m12;
		float g = m20 * m31 - m21 * m30;
		float h = m20 * m32 - m22 * m30;
		float i = m20 * m33 - m23 * m30;
		float j = m21 * m32 - m22 * m31;
		float k = m21 * m33 - m23 * m31;
		float l = m22 * m33 - m23 * m32;
		float det = a * l - b * k + c * j + d * i - e * h + f * g;
		det = 1.0f / det;
		float nm00 = MathExt.fma( m11, l, MathExt.fma(-m12, k,  m13 * j)) * det;
		float nm01 = MathExt.fma(-m01, l, MathExt.fma( m02, k, -m03 * j)) * det;
		float nm02 = MathExt.fma( m31, f, MathExt.fma(-m32, e,  m33 * d)) * det;
		float nm03 = MathExt.fma(-m21, f, MathExt.fma( m22, e, -m23 * d)) * det;
		float nm10 = MathExt.fma(-m10, l, MathExt.fma( m12, i, -m13 * h)) * det;
		float nm11 = MathExt.fma( m00, l, MathExt.fma(-m02, i,  m03 * h)) * det;
		float nm12 = MathExt.fma(-m30, f, MathExt.fma( m32, c, -m33 * b)) * det;
		float nm13 = MathExt.fma( m20, f, MathExt.fma(-m22, c,  m23 * b)) * det;
		float nm20 = MathExt.fma( m10, k, MathExt.fma(-m11, i,  m13 * g)) * det;
		float nm21 = MathExt.fma(-m00, k, MathExt.fma( m01, i, -m03 * g)) * det;
		float nm22 = MathExt.fma( m30, e, MathExt.fma(-m31, c,  m33 * a)) * det;
		float nm23 = MathExt.fma(-m20, e, MathExt.fma( m21, c, -m23 * a)) * det;
		float nm30 = MathExt.fma(-m10, j, MathExt.fma( m11, h, -m12 * g)) * det;
		float nm31 = MathExt.fma( m00, j, MathExt.fma(-m01, h,  m02 * g)) * det;
		float nm32 = MathExt.fma(-m30, d, MathExt.fma( m31, b, -m32 * a)) * det;
		float nm33 = MathExt.fma( m20, d, MathExt.fma(-m21, b,  m22 * a)) * det;
		dest.m00 = nm00;
		dest.m01 = nm01;
		dest.m02 = nm02;
		dest.m03 = nm03;
		dest.m10 = nm10;
		dest.m11 = nm11;
		dest.m12 = nm12;
		dest.m13 = nm13;
		dest.m20 = nm20;
		dest.m21 = nm21;
		dest.m22 = nm22;
		dest.m23 = nm23;
		dest.m30 = nm30;
		dest.m31 = nm31;
		dest.m32 = nm32;
		dest.m33 = nm33;
		return dest;
	}

	public Matrix4f mul(Matrix4f right, Matrix4f result) {
		return result.set(this).mul(right);
	}

	public Matrix4f mul(Matrix4f right) {
		float nm00 = MathExt.fma(m00, right.m00, MathExt.fma(m10, right.m01, MathExt.fma(m20, right.m02, m30 * right.m03)));
		float nm01 = MathExt.fma(m01, right.m00, MathExt.fma(m11, right.m01, MathExt.fma(m21, right.m02, m31 * right.m03)));
		float nm02 = MathExt.fma(m02, right.m00, MathExt.fma(m12, right.m01, MathExt.fma(m22, right.m02, m32 * right.m03)));
		float nm03 = MathExt.fma(m03, right.m00, MathExt.fma(m13, right.m01, MathExt.fma(m23, right.m02, m33 * right.m03)));
		float nm10 = MathExt.fma(m00, right.m10, MathExt.fma(m10, right.m11, MathExt.fma(m20, right.m12, m30 * right.m13)));
		float nm11 = MathExt.fma(m01, right.m10, MathExt.fma(m11, right.m11, MathExt.fma(m21, right.m12, m31 * right.m13)));
		float nm12 = MathExt.fma(m02, right.m10, MathExt.fma(m12, right.m11, MathExt.fma(m22, right.m12, m32 * right.m13)));
		float nm13 = MathExt.fma(m03, right.m10, MathExt.fma(m13, right.m11, MathExt.fma(m23, right.m12, m33 * right.m13)));
		float nm20 = MathExt.fma(m00, right.m20, MathExt.fma(m10, right.m21, MathExt.fma(m20, right.m22, m30 * right.m23)));
		float nm21 = MathExt.fma(m01, right.m20, MathExt.fma(m11, right.m21, MathExt.fma(m21, right.m22, m31 * right.m23)));
		float nm22 = MathExt.fma(m02, right.m20, MathExt.fma(m12, right.m21, MathExt.fma(m22, right.m22, m32 * right.m23)));
		float nm23 = MathExt.fma(m03, right.m20, MathExt.fma(m13, right.m21, MathExt.fma(m23, right.m22, m33 * right.m23)));
		float nm30 = MathExt.fma(m00, right.m30, MathExt.fma(m10, right.m31, MathExt.fma(m20, right.m32, m30 * right.m33)));
		float nm31 = MathExt.fma(m01, right.m30, MathExt.fma(m11, right.m31, MathExt.fma(m21, right.m32, m31 * right.m33)));
		float nm32 = MathExt.fma(m02, right.m30, MathExt.fma(m12, right.m31, MathExt.fma(m22, right.m32, m32 * right.m33)));
		float nm33 = MathExt.fma(m03, right.m30, MathExt.fma(m13, right.m31, MathExt.fma(m23, right.m32, m33 * right.m33)));
		m00 = nm00;
		m01 = nm01;
		m02 = nm02;
		m03 = nm03;
		m10 = nm10;
		m11 = nm11;
		m12 = nm12;
		m13 = nm13;
		m20 = nm20;
		m21 = nm21;
		m22 = nm22;
		m23 = nm23;
		m30 = nm30;
		m31 = nm31;
		m32 = nm32;
		m33 = nm33;
		return this;
	}

	public FloatBuffer set(FloatBuffer buffer) {
		long ptr = MemoryUtil.getAddress(buffer);

		this.m00 = UnsafeUtil.memGetFloat(ptr + 0);
		this.m01 = UnsafeUtil.memGetFloat(ptr + 4);
		this.m02 = UnsafeUtil.memGetFloat(ptr + 8);
		this.m03 = UnsafeUtil.memGetFloat(ptr + 12);
		ptr += 16;

		this.m10 = UnsafeUtil.memGetFloat(ptr + 0);
		this.m11 = UnsafeUtil.memGetFloat(ptr + 4);
		this.m12 = UnsafeUtil.memGetFloat(ptr + 8);
		this.m13 = UnsafeUtil.memGetFloat(ptr + 12);
		ptr += 16;

		this.m20 = UnsafeUtil.memGetFloat(ptr + 0);
		this.m21 = UnsafeUtil.memGetFloat(ptr + 4);
		this.m22 = UnsafeUtil.memGetFloat(ptr + 8);
		this.m23 = UnsafeUtil.memGetFloat(ptr + 12);
		ptr += 16;

		this.m30 = UnsafeUtil.memGetFloat(ptr + 0);
		this.m31 = UnsafeUtil.memGetFloat(ptr + 4);
		this.m32 = UnsafeUtil.memGetFloat(ptr + 8);
		this.m33 = UnsafeUtil.memGetFloat(ptr + 12);

		return buffer;
	}

	public FloatBuffer get(FloatBuffer buffer) {
		long ptr = MemoryUtil.getAddress(buffer);

		UnsafeUtil.memPutFloat(ptr + 0, this.m00);
		UnsafeUtil.memPutFloat(ptr + 4, this.m01);
		UnsafeUtil.memPutFloat(ptr + 8, this.m02);
		UnsafeUtil.memPutFloat(ptr + 12, this.m03);
		ptr += 16;

		UnsafeUtil.memPutFloat(ptr + 0, this.m10);
		UnsafeUtil.memPutFloat(ptr + 4, this.m11);
		UnsafeUtil.memPutFloat(ptr + 8, this.m12);
		UnsafeUtil.memPutFloat(ptr + 12, this.m13);
		ptr += 16;

		UnsafeUtil.memPutFloat(ptr + 0, this.m20);
		UnsafeUtil.memPutFloat(ptr + 4, this.m21);
		UnsafeUtil.memPutFloat(ptr + 8, this.m22);
		UnsafeUtil.memPutFloat(ptr + 12, this.m23);
		ptr += 16;

		UnsafeUtil.memPutFloat(ptr + 0, this.m30);
		UnsafeUtil.memPutFloat(ptr + 4, this.m31);
		UnsafeUtil.memPutFloat(ptr + 8, this.m32);
		UnsafeUtil.memPutFloat(ptr + 12, this.m33);

		return buffer;
	}
}
