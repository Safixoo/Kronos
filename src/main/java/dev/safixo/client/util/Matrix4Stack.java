package dev.safixo.client.util;

import dev.safixo.client.util.memory.UnsafeUtil;
import org.joml.Matrix4f;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * JOML already have a very useful Matrix4fStack, the problem is that it can be very performance intensive with its
 * matrix copies when popping and pushing.
 */
public class Matrix4Stack {
	private static final long M00_OFFSET;

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

		Unsafe unsafe = UnsafeUtil.UNSAFE;
		long offset = M00_OFFSET;

		unsafe.putLong(top, offset, unsafe.getLong(oldTop, offset));
		offset += 8;
		unsafe.putLong(top, offset, unsafe.getLong(oldTop, offset));
		offset += 8;
		unsafe.putLong(top, offset, unsafe.getLong(oldTop, offset));
		offset += 8;
		unsafe.putLong(top, offset, unsafe.getLong(oldTop, offset));
		offset += 8;

		unsafe.putLong(top, offset, unsafe.getLong(oldTop, offset));
		offset += 8;
		unsafe.putLong(top, offset, unsafe.getLong(oldTop, offset));
		offset += 8;
		unsafe.putLong(top, offset, unsafe.getLong(oldTop, offset));
		offset += 8;
		unsafe.putLong(top, offset, unsafe.getLong(oldTop, offset));

		this.top = top;
	}

	public void pop() {
		this.top = this.mats[--this.curr];
	}

	public Matrix4f top() {
		return this.top;
	}
}
