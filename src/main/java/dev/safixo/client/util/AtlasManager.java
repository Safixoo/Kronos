package dev.safixo.client.util;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.StitcherException;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.ResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.IOException;
import java.nio.Buffer;
import java.util.List;
import java.util.Map;

public class AtlasManager {
	private static final int ALPHA_THRESHOLD = (int) (0.1f * 0xFF);
	public static final AtlasManager INSTANCE = new AtlasManager();

	private static final int LINEAR_PRECISION_BITS = 11;
	private static final int WEIGHT_PRECISION_BITS = 16;

	private static final int[] SRGB_TO_LINEAR = new int[256];
	private static final byte[] LINEAR_TO_SRGB = new byte[(1 << LINEAR_PRECISION_BITS) + 1];

	static {
		for (int i = 0; i < 256; i++) {
			SRGB_TO_LINEAR[i] = (int) Math.round(Math.pow(i / 255.0, 2.2) * (double) (1 << LINEAR_PRECISION_BITS));
		}
		for (int i = 1; i <= 1 << LINEAR_PRECISION_BITS; i++) {
			LINEAR_TO_SRGB[i] = (byte) (Math.round(Math.pow(i / (double) (1 << LINEAR_PRECISION_BITS), 1.0 / 2.2) * 255.0));
		}
	}

	public void loadTextureAtlas(TextureMap atlas, ResourceManager resourceManager) {
		int maxTexSize = Minecraft.getGLMaximumTextureSize();
		Stitcher stitcher = new Stitcher(maxTexSize, maxTexSize, true);

		atlas.registerIcons();

		Map<String, TextureAtlasSprite> uploadedSprites = (Map<String, TextureAtlasSprite>) atlas.mapUploadedSprites;
		Map<String, TextureAtlasSprite> registeredSprites = (Map<String, TextureAtlasSprite>) atlas.mapRegisteredSprites;
		List<TextureAtlasSprite> animatedSprites = (List<TextureAtlasSprite>) atlas.listAnimatedSprites;

		uploadedSprites.clear();
		animatedSprites.clear();

		ForgeHooksClient.onTextureStitchedPre(atlas);

		for (Map.Entry<String, TextureAtlasSprite> entry : registeredSprites.entrySet()) {
			ResourceLocation entryPath = new ResourceLocation(entry.getKey());
			TextureAtlasSprite sprite = entry.getValue();
			ResourceLocation spritePath = new ResourceLocation(entryPath.getResourceDomain(), String.format("%s/%s%s", atlas.basePath, entryPath.getResourcePath(), ".png"));

			try {
				if (!sprite.load(resourceManager, spritePath)) {
					continue;
				}
			} catch (RuntimeException exception) {
				Minecraft.getMinecraft().getLogAgent().logSevere(String.format("Unable to parse animation metadata from %s: %s", spritePath, exception.getMessage()));
				continue;
			} catch (IOException exception) {
				Minecraft.getMinecraft().getLogAgent().logSevere("Using missing texture, unable to load: " + spritePath);
				continue;
			}

			stitcher.addSprite(sprite);
		}

		stitcher.addSprite(atlas.missingImage);
		try {
			stitcher.doStitch();
		} catch (StitcherException exception) {
			throw exception;
		}

		Map<String, TextureAtlasSprite> hashMap = new Reference2ReferenceOpenHashMap<>(registeredSprites);
		uploadAllSprites(stitcher, atlas, hashMap);

		for (TextureAtlasSprite sprite : hashMap.values()) {
			sprite.copyFrom(atlas.missingImage);
		}

		ForgeHooksClient.onTextureStitchedPost(atlas);
	}

	private static final int MAX_MIPMAP = 4;

	private void uploadAllSprites(Stitcher stitcher, TextureMap atlas, Map<String, TextureAtlasSprite> map) {
		int aHeight = stitcher.getCurrentHeight(), aWidth = stitcher.getCurrentWidth();

		int[][] atlasData = new int[MAX_MIPMAP + 1][];
		for (int mip = 0; mip <= MAX_MIPMAP; mip++) {
			atlasData[mip] = new int[(aWidth >> mip) * (aHeight >> mip)];
		}

		for (TextureAtlasSprite sprite : (List<TextureAtlasSprite>) stitcher.getStichSlots()) {
			int[] spriteData = sprite.getFrameTextureData(0);

			int x = sprite.getOriginX(), y = sprite.getOriginY();
			int width = sprite.getIconWidth(), height = sprite.getIconHeight();

			int[] atlasUnmipped = atlasData[0];

			for (int texelX = 0; texelX < width; texelX++) {
				for (int texelY = 0; texelY < height; texelY++) {
					int atlasX = texelX + x, atlasY = texelY + y;
					int color = argbToAbgr(getColor(spriteData, texelX, texelY, width));

					setColor(atlasUnmipped, atlasX, atlasY, aWidth, color);
				}
			}

			for (int mip = 1; mip <= MAX_MIPMAP; mip++) {
				mipmapTexture(atlasData, x, y, width, height, aWidth, mip);
			}
		}

		GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.getGlTextureId());
		uploadTextureRgbaMipmaps(atlasData, aWidth, aHeight, MAX_MIPMAP);

		for (TextureAtlasSprite sprite : (List<TextureAtlasSprite>) stitcher.getStichSlots()) {
			String spriteName = sprite.getIconName();

			map.remove(spriteName);
			atlas.mapUploadedSprites.put(spriteName, sprite);

			if (sprite.hasAnimationMetadata()) {
				atlas.listAnimatedSprites.add(sprite);
			} else {
				sprite.clearFramesTextureData();
			}
		}
	}

	private static void uploadTexture(int[] tex, int width, int height, int mip) {
		((Buffer) TextureUtil.dataBuffer).clear();
		TextureUtil.dataBuffer.put(tex);
		((Buffer) TextureUtil.dataBuffer).limit(width * height);
		((Buffer) TextureUtil.dataBuffer).flip();

		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, mip, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, TextureUtil.dataBuffer);
	}

	private static void mipmapTexture(int[][] texture, int x, int y, int width, int height, int atlasWidth, int mip) {
		for (int wm = x >> mip; wm < (x + width) >> mip; wm++) {
			for (int hm = y >> mip; hm < (y + height) >> mip; hm++) {
				int w = wm << 1, h = hm << 1;

				int one   = getColor(texture[mip - 1], w + 0, h + 0, atlasWidth >> (mip - 1));
				int two   = getColor(texture[mip - 1], w + 1, h + 0, atlasWidth >> (mip - 1));
				int three = getColor(texture[mip - 1], w + 0, h + 1, atlasWidth >> (mip - 1));
				int four  = getColor(texture[mip - 1], w + 1, h + 1, atlasWidth >> (mip - 1));

				setColor(texture[mip], wm, hm, atlasWidth >> mip, blend(one, two, three, four));
			}
		}
	}

	private static void uploadTextureRgbaMipmaps(int[][] atlas, int aWidth, int aHeight, int maxMipmap) {
		for (int mip = 0; mip <= maxMipmap; mip++) {
			uploadTexture(atlas[mip], aWidth >> mip, aHeight >> mip, mip);
		}

		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, maxMipmap);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_NEAREST);
	}

	private static int blend(int one, int two, int three, int four) {
		return weightedAverageColor(weightedAverageColor(one, two), weightedAverageColor(three, four));
	}

	private static int weightedAverageColor(int one, int two) {
		int alphaOne = unpackAlpha(one);
		int alphaTwo = unpackAlpha(two);

		if (alphaOne == alphaTwo) {
			return averageRgb(one, two, alphaOne);
		}

		if (alphaOne <= ALPHA_THRESHOLD) {
			return (two & 0x00FFFFFF) | ((alphaTwo >> 2) << 24);
		}
		if (alphaTwo <= ALPHA_THRESHOLD) {
			return (one & 0x00FFFFFF) | ((alphaOne >> 2) << 24);
		}

		int scale = (1 << WEIGHT_PRECISION_BITS) / (alphaOne + alphaTwo);
		int weightOne = alphaOne * scale;
		int weightTwo = alphaTwo * scale;

		int linearR = mulWeight(unpackLR(one), weightOne) + mulWeight(unpackLR(two), weightTwo);
		int linearG = mulWeight(unpackLG(one), weightOne) + mulWeight(unpackLG(two), weightTwo);
		int linearB = mulWeight(unpackLB(one), weightOne) + mulWeight(unpackLB(two), weightTwo);
		int averageAlpha = (alphaOne + alphaTwo) >> 1;

		return packLinearToSrgb(linearR, linearG, linearB, averageAlpha);
	}

	private static int unpackAlpha(int color) {
		return color >>> 24;
	}

	private static int getColor(int[] colors, int x, int y, int width) {
		return colors[x + y * width];
	}

	private static void setColor(int[] colors, int x, int y, int width, int value) {
		colors[x + y * width] = value;
	}

	private static int averageRgb(int a, int b, int alpha) {
		int ar = unpackLR(a);
		int ag = unpackLG(a);
		int ab = unpackLB(a);

		int br = unpackLR(b);
		int bg = unpackLG(b);
		int bb = unpackLB(b);

		return packLinearToSrgb((ar + br) >> 1, (ag + bg) >> 1, (ab + bb) >> 1, alpha);
	}

	private static int averageRgb(int a, int b, int c, int d, int alpha) {
		int lr = unpackLR(a) + unpackLR(b) + unpackLR(c) + unpackLR(d);
		int lg = unpackLG(a) + unpackLG(b) + unpackLG(c) + unpackLG(d);
		int lb = unpackLB(a) + unpackLB(b) + unpackLB(c) + unpackLB(d);

		return packLinearToSrgb(lr >> 2, lg >> 2, lb >> 2, alpha);
	}

	private static int mulWeight(int x, int weight) {
		return (x * weight) >> WEIGHT_PRECISION_BITS;
	}

	private static int unpackLR(int color) {
		return SRGB_TO_LINEAR[(color >>> 0) & 0xFF];
	}

	private static int unpackLG(int color) {
		return SRGB_TO_LINEAR[(color >>> 8) & 0xFF];
	}

	private static int unpackLB(int color) {
		return SRGB_TO_LINEAR[(color >>> 16) & 0xFF];
	}

	private static int unpackSRGB(int comp) {
		return MathExt.byteToUnsigned(LINEAR_TO_SRGB[comp]);
	}

	private static int packLinearToSrgb(int r, int g, int b, int a) {
		return (a << 24) | unpackSRGB(b) << 16 | unpackSRGB(g) << 8 | unpackSRGB(r);
	}

	private static int argbToAbgr(int color) {
		return ColorBGRManager.rgbToBgr(color) | color & 0xFF_000000;
	}
}
