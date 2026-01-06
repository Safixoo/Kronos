package dev.safixo.client.util;

import dev.safixo.client.util.memory.UnsafeUtil;
import org.joml.Matrix4f;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;

/**
 * JOML already have a very useful Matrix4fStack, the problem is that it can be very performance intensive with its
 * matrix copies when popping and pushing.
 */
public class Matrix4Stack {
	public static final long M00_OFFSET;

	static {
		Field m00;

		try {
			m00 = Matrix4f.class.getDeclaredField("m00");
			M00_OFFSET = UnsafeUtil.getFieldOffset(m00);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
	}

	private final Matrix4f[] mats;
	private Matrix4f top;
	private int curr;

	public Matrix4Stack(int size) {
		this.mats = new Matrix4f[size];

		for (int i = 0; i < size; i++) {
			this.mats[i] = new Matrix4f();
		}

		this.top = this.mats[0];
	}

	public void push() {
		Matrix4f oldTop = this.top;
		Matrix4f top = this.mats[++this.curr];
		copyMat(oldTop, top);

		this.top = top;
	}

	public static void copyMat(Matrix4f from, Matrix4f to) {
		Unsafe unsafe = UnsafeUtil.UNSAFE;
		long offset = M00_OFFSET;

		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
		offset += 8;

		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
	}

	public static void copyMat(Matrix4f from, FloatBuffer to) {
		Unsafe unsafe = UnsafeUtil.UNSAFE;
		long offset = M00_OFFSET;

		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
		offset += 8;

		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(to, offset, unsafe.getLong(from, offset));
	}

	public static void copyMat(Matrix4f from, long to) {
		Unsafe unsafe = UnsafeUtil.UNSAFE;
		long offset = M00_OFFSET;

		unsafe.putLong(null, offset + to, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(null, offset + to, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(null, offset + to, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(null, offset + to, unsafe.getLong(from, offset));
		offset += 8;

		unsafe.putLong(null, offset + to, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(null, offset + to, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(null, offset + to, unsafe.getLong(from, offset));
		offset += 8;
		unsafe.putLong(null, offset + to, unsafe.getLong(from, offset));
	}

	public void pop() {
		this.top = this.mats[--this.curr];
	}

	public Matrix4f top() {
		return this.top;
	}
}
