package dev.safixo.client.util;

import dev.safixo.client.util.data.PrimitivesFlags;
import dev.safixo.client.util.memory.UnsafeUtil;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Icon;

import java.lang.reflect.Field;

public class AtlasSpriteUnsafe {
//	private float minU;
//	private float maxU;
//	private float minV;
//	private float maxV;
	private static final long UV_OFFSET;

	static {
		try {
			Field field = TextureAtlasSprite.class.getDeclaredField(
				PrimitivesFlags.DEV_ENVIRONMENT ?
					"minU" :
					"field_110979_l"
			);
			UV_OFFSET = UnsafeUtil.getFieldOffset(field);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static float minU(Icon sprite) {
		return UnsafeUtil.UNSAFE.getFloat(sprite, UV_OFFSET);
	}

	public static float maxU(Icon sprite) {
		return UnsafeUtil.UNSAFE.getFloat(sprite, UV_OFFSET + 4);
	}

	public static float minV(Icon sprite) {
		return UnsafeUtil.UNSAFE.getFloat(sprite, UV_OFFSET + 8);
	}

	public static float maxV(Icon sprite) {
		return UnsafeUtil.UNSAFE.getFloat(sprite, UV_OFFSET + 12);
	}
}
