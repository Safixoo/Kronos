package dev.safixo.core.hooks;

import dev.safixo.client.util.AtlasManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.ResourceManager;

@SuppressWarnings("unused")
public class TextureMapHook {
	public static void loadTextureAtlas(TextureMap atlas, ResourceManager resourceManager) {
		AtlasManager.INSTANCE.loadTextureAtlas(atlas, resourceManager);
	}
}
