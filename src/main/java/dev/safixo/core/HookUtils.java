package dev.safixo.core;

import dev.safixo.client.util.data.PrimitivesFlags;
import java.lang.reflect.Field;

public class HookUtils {
	public static Object getFieldObj(Object instance, String fieldName, String fieldNotch) {
		if (!PrimitivesFlags.DETECTED) {
			PrimitivesFlags.processDevInfo();
		}

		if (!PrimitivesFlags.DEV_ENVIRONMENT) {
			fieldName = fieldNotch;
		}

		try {
			Field mcField = instance.getClass().getDeclaredField(fieldName);
			mcField.setAccessible(true);

			return mcField.get(instance);
		} catch (Exception e) {
			e.printStackTrace();
		}

		throw new RuntimeException("Couldn't find object");
	}

	public static void setField(Object instance, String fieldName, String fieldNotch, Object value) {
		if (!PrimitivesFlags.DETECTED) {
			PrimitivesFlags.processDevInfo();
		}

		if (!PrimitivesFlags.DEV_ENVIRONMENT) {
			fieldName = fieldNotch;
		}

		try {
			Field mcField = instance.getClass().getDeclaredField(fieldName);
			mcField.setAccessible(true);
			mcField.set(instance, value);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void setFieldValue(Field field, Object instance, Object value) {
		try {
			field.set(instance, value);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static Object getFieldValue(Field field, Object instance) {
		try {
			return field.get(instance);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static Object getFieldStatic(Class<?> clazz, String fieldName, String fieldNotch) {
		if (!PrimitivesFlags.DETECTED) {
			PrimitivesFlags.processDevInfo();
		}

		if (!PrimitivesFlags.DEV_ENVIRONMENT) {
			fieldName = fieldNotch;
		}

		try {
			Field mcField = clazz.getDeclaredField(fieldName);
			mcField.setAccessible(true);

			return mcField.get(null);
		} catch (Exception e) {
			e.printStackTrace();
		}

		throw new RuntimeException("Couldn't find object");
	}

	public static boolean existsField(Object instance, String fieldName) {
		Field[] fields = instance.getClass().getFields();

		for (Field field : fields) {
			if (field != null && field.getName().equals(fieldName)) {
				return true;
			}
		}

		return false;
	}

	public static Field getField(Class<?> clazz, String fieldName, String fieldNotch) {
		if (!PrimitivesFlags.DETECTED) {
			PrimitivesFlags.processDevInfo();
		}

		if (!PrimitivesFlags.DEV_ENVIRONMENT) {
			fieldName = fieldNotch;
		}

		try {
			Field mcField = clazz.getDeclaredField(fieldName);
			mcField.setAccessible(true);

			return mcField;
		} catch (Exception e) {
			System.err.println("Couldn't find field with name: " + fieldName);
		}

		// Couldn't find field with the name.
		return null;
	}

	public static Field getField(Object instance, String fieldName, String fieldNotch) {
		if (!PrimitivesFlags.DETECTED) {
			PrimitivesFlags.processDevInfo();
		}

		if (!PrimitivesFlags.DEV_ENVIRONMENT) {
			fieldName = fieldNotch;
		}

		try {
			Field mcField = instance.getClass().getDeclaredField(fieldName);
			mcField.setAccessible(true);

			return mcField;
		} catch (Exception e) {
			e.printStackTrace();
		}

		throw new RuntimeException("Couldn't find object");
	}
}
