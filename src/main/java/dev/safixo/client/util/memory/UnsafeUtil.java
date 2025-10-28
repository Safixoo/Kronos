package dev.safixo.client.util.memory;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class UnsafeUtil {
	public static final Unsafe UNSAFE = getUnsafeInstance();
	public static final long NULL = 0;

	private static Unsafe getUnsafeInstance() {
		Field[] fields = Unsafe.class.getDeclaredFields();
		int length = fields.length;
		int i = 0;

		// Got from MemoryUtilSun.class.
		while (true) {
			loop: {
				if (i < length) {
					Field field = fields[i];
					if (!field.getType().equals(Unsafe.class)) {
						break loop;
					}

					int modifiers = field.getModifiers();
					if (!Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
						break loop;
					}

					field.setAccessible(true);

					try {
						return (Unsafe) field.get(null);
					} catch (IllegalAccessException ignored) {}
				}

				throw new UnsupportedOperationException();
			}

			++i;
		}
	}

	public static void nmemFree(long ptr) {
		UNSAFE.freeMemory(ptr);
	}

	public static void memCopy(long src, long dest, long size) {
		UNSAFE.copyMemory(src, dest, size);
	}

	public static void memPutLong(long ptr, long value) {
		UNSAFE.putLong(ptr, value);
	}

	public static void memPutInt(long ptr, int value) {
		UNSAFE.putInt(ptr, value);
	}

	public static void memPutShort(long ptr, short value) {
		UNSAFE.putShort(ptr, value);
	}

	public static void memPutByte(long ptr, byte value) {
		UNSAFE.putByte(ptr, value);
	}

	public static void memPutFloat(long ptr, float value) {
		UNSAFE.putFloat(ptr, value);
	}

	public static float memGetFloat(long ptr) {
		return UNSAFE.getFloat(ptr);
	}

	public static int memGetInt(long ptr) {
		return UNSAFE.getInt(ptr);
	}
}
