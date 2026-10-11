package dev.safixo.core.hooks;

import dev.safixo.client.util.AtlasManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.ResourceManager;

@SuppressWarnings("unused")
public class TextureMapHook {
	public static void loadTextureAtlas(TextureMap atlas, ResourceManager resourceManager) {
		AtlasManager.INSTANCE.loadTextureAtlas(atlas, resourceManager);
	}

	public static void updateAnimation(TextureAtlasSprite sprite) {
		sprite.tickCounter++;
		if (sprite.tickCounter < sprite.animationMetadata.getFrameTimeSingle(sprite.frameCounter)) {
			return;
		}
		int i = sprite.animationMetadata.getFrameIndex(sprite.frameCounter);
		int j = sprite.animationMetadata.getFrameCount() == 0 ? sprite.framesTextureData.size() : sprite.animationMetadata.getFrameCount();
		sprite.frameCounter = (sprite.frameCounter + 1) % j;
		sprite.tickCounter = 0;
		int k = sprite.animationMetadata.getFrameIndex(sprite.frameCounter);
		if (i != k && k >= 0 && k < sprite.framesTextureData.size()) {
			TextureUtil.uploadTextureSub((int[])sprite.framesTextureData.get(k), sprite.width, sprite.height, sprite.originX, sprite.originY, false, false);
		}
	}
}
