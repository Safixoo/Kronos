package dev.safixo.core;

import java.lang.reflect.Field;

public class HookUtils {
	public static Object getFieldObj(Object instance, String fieldName) {
		try {
			Field mcField = instance.getClass().getDeclaredField(fieldName);
			mcField.setAccessible(true);

			return mcField.get(instance);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	public static void setField(Object instance, String fieldName, Object value) {
		try {
			Field mcField = instance.getClass().getDeclaredField(fieldName);
			mcField.setAccessible(true);
			mcField.set(instance, value);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Field getField(Object instance, String fieldName) {
		try {
			Field mcField = instance.getClass().getDeclaredField(fieldName);
			mcField.setAccessible(true);

			return mcField;
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}
}
