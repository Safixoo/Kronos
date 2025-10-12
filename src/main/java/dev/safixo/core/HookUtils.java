package dev.safixo.core;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import dev.safixo.client.render.util.data.BlocksFlags;

import java.lang.reflect.Field;

public class HookUtils {
	public static Object getFieldObj(Object instance, String fieldName, String fieldNotch) {
		if (!BlocksFlags.DETECTED) {
			BlocksFlags.processDevInfo();
		}

		if (!BlocksFlags.DEV_ENVIRONMENT) {
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
		if (!BlocksFlags.DETECTED) {
			BlocksFlags.processDevInfo();
		}

		if (!BlocksFlags.DEV_ENVIRONMENT) {
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

	public static Object getFieldStatic(Class<?> clazz, String fieldName, String fieldNotch) {
		if (!BlocksFlags.DETECTED) {
			BlocksFlags.processDevInfo();
		}

		if (!BlocksFlags.DEV_ENVIRONMENT) {
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

	public static Field getField(Object instance, String fieldName, String fieldNotch) {
		if (!BlocksFlags.DETECTED) {
			BlocksFlags.processDevInfo();
		}

		if (!BlocksFlags.DEV_ENVIRONMENT) {
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
