package dev.safixo.core.hooks;

import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.util.ColorBGRManager;
import dev.safixo.client.util.memory.UnsafeUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

@SuppressWarnings("unused")
public class FontRendererHook {
	public static final float FONT_HEIGHT = 9, FONT_WIDTH = 8;
	private static final int[] ALLOWED_CHARACTERS_INDEX = new int[256];

	private static final ObjectArrayFIFOQueue<FontRenderData> underlines = new ObjectArrayFIFOQueue<>();
	private static final ObjectArrayFIFOQueue<FontRenderData> spikeThrough = new ObjectArrayFIFOQueue<>();

	static {
		for (char i = 0; i < 256; i++) {
			ALLOWED_CHARACTERS_INDEX[i] = ChatAllowedCharacters.allowedCharacters.indexOf(i);
		}
	}

	public static int renderString(FontRenderer fr, String text, int posX, int posZ, int color, boolean margin) {
		if (text == null) {
			return 0;
		}

		return renderStringFast(fr, text, posX, posZ, color, margin);
	}

	public static int renderStringFast(FontRenderer fr, String text, int posX, int posZ, int color, boolean margin) {
		if ((color & 0xFF000000) == 0) {
			color |= 0xFF000000;
		}

		if (margin) {
			color = (color & 0xFCFCFC) >> 2 | color & 0xFF000000;
		}

		currentColor = fr.textColor = color;

		ImprovedTessellator tes = (ImprovedTessellator) Tessellator.instance;

		// Render main string.
		tes.startDrawing(GL11.GL_QUADS);
		float returnVal = renderStringAtPos(fr, tes, posX, posZ, text, margin);
		tes.draw();

		// Draw extra styles.
		if (!underlines.isEmpty() || !spikeThrough.isEmpty()){
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			tes.startDrawingQuads();

			drawUnderlines();
			drawSpikeThrough();

			tes.draw();
			GL11.glEnable(GL11.GL_TEXTURE_2D);
		}

		return (int) returnVal;
	}

	private static char toLowerCase(char ch) {
		// if upper-case char, lower case.
		if (ch >= 'A' && ch <= 'Z') {
			ch |= 0x20;
		}

		return ch;
	}

	private static void customizeMessage(FontRenderer fr, String text, int charInd, boolean cond) {
		char nextChar = toLowerCase(text.charAt(charInd + 1));
		int type = "0123456789abcdefklmnor".indexOf(nextChar);

		if (type < 16) {
			fr.randomStyle = false;
			fr.boldStyle = false;
			fr.strikethroughStyle = false;
			fr.underlineStyle = false;
			fr.italicStyle = false;
			if (type < 0) {
				type = 15;
			}

			if (cond) {
				type += 16;
			}

			currentColor = fr.colorCode[type];
		} else if (type == 16) {
			fr.randomStyle = true;
		} else if (type == 17) {
			fr.boldStyle = true;
		} else if (type == 18) {
			fr.strikethroughStyle = true;
		} else if (type == 19) {
			fr.underlineStyle = true;
		} else if (type == 20) {
			fr.italicStyle = true;
		} else {
			fr.randomStyle = false;
			fr.boldStyle = false;
			fr.strikethroughStyle = false;
			fr.underlineStyle = false;
			fr.italicStyle = false;
			currentColor = fr.textColor;
		}
	}

	static int currentColor;

	private static float renderStringAtPos(FontRenderer fr, ImprovedTessellator tes, float posX, float posY, String text, boolean cond) {
		char[] textArr = text.toCharArray();

		for (int charInd = 0; charInd < textArr.length; charInd++) {
			char asciiChar = textArr[charInd];

			// Customize message based in flags.
			if (asciiChar == '§' && charInd + 1 < textArr.length) {
				customizeMessage(fr, text, charInd, cond);
				charInd++;
				continue;
			}

			if (tes.offset + 128 > tes.capacity) {
				tes.resize();
			}

			int allowedIndex = asciiChar >= 256 ? -1 : ALLOWED_CHARACTERS_INDEX[asciiChar];

			if (fr.randomStyle && allowedIndex > 0) {
				int randCh;
				do {
					randCh = fr.fontRandom.nextInt(ChatAllowedCharacters.allowedCharacters.length());
				} while (fr.charWidth[allowedIndex + 32] != fr.charWidth[randCh + 32]);

				allowedIndex = randCh;
			}

			float marginSize = fr.unicodeFlag ? 0.5F : 1.0F;
			boolean margin = (allowedIndex <= 0 || fr.unicodeFlag) && cond;

			float offset;

			if (margin) {
				offset = renderCharAtPos(fr, tes, posX - marginSize, posY - marginSize, allowedIndex, asciiChar, fr.italicStyle);
			} else {
				offset = renderCharAtPos(fr, tes, posX, posY, allowedIndex, asciiChar, fr.italicStyle);
			}

			if (fr.boldStyle) {
				if (margin) {
					renderCharAtPos(fr, tes, posX, posY - marginSize, allowedIndex, asciiChar, fr.italicStyle);
				} else {
					renderCharAtPos(fr, tes, posX + marginSize, posY, allowedIndex, asciiChar, fr.italicStyle);
				}

				if (margin) {
					posX += marginSize;
					posY += marginSize;
				}

				offset++;
			}

			if (fr.strikethroughStyle || fr.underlineStyle) {
				if (fr.strikethroughStyle) {
				}

				if (fr.underlineStyle) {
				}
			}

			posX += (int) offset;
		}

		return posX;
	}

	private static void drawUnderlines() {
		Tessellator tes = Tessellator.instance;
		int underlineSize = underlines.size();

		for (int i = 0; i < underlineSize; i++) {
			FontRenderData data = underlines.dequeue();

			tes.addVertex(data.x - 1, data.y + FONT_HEIGHT, 0.0F);
			tes.addVertex(data.x + data.offset, data.y + FONT_HEIGHT, 0.0F);
			tes.addVertex(data.x + data.offset, data.y + FONT_HEIGHT - 1.0F, 0.0F);
			tes.addVertex(data.x - 1, data.y + FONT_HEIGHT - 1.0F, 0.0F);
		}
	}

	private static void addVertexWithUV(ImprovedTessellator tes, float x, float y, float u, float v, int color) {
		long ptr = tes.vertexPtr + tes.offset;

		UnsafeUtil.memPutFloat(ptr + 0, x);
		UnsafeUtil.memPutFloat(ptr + 4, y);
		UnsafeUtil.memPutFloat(ptr + 8, 0);

		UnsafeUtil.memPutFloat(ptr + 12, u);
		UnsafeUtil.memPutFloat(ptr + 16, v);

		UnsafeUtil.memPutInt(ptr + 20, color);

		tes.vertices++;
		tes.flags |= ImprovedTessellator.VERTEX_UV;
		tes.flags |= ImprovedTessellator.VERTEX_COLOR;
		tes.offset += 24;
	}

	private static void drawSpikeThrough() {
		Tessellator tes = Tessellator.instance;
		int spikeThroughSize = spikeThrough.size();

		for (int i = 0; i < spikeThroughSize; i++) {
			FontRenderData data = spikeThrough.dequeue();

			tes.addVertex(data.x - 1, data.y + (FONT_HEIGHT * 0.5F), 0.0F);
			tes.addVertex(data.x + data.offset, data.y + (FONT_HEIGHT * 0.5F), 0.0F);
			tes.addVertex(data.x + data.offset, data.y + (FONT_HEIGHT * 0.5F) - 1.0F, 0.0F);
			tes.addVertex(data.x - 1, data.y + (FONT_HEIGHT * 0.5F) - 1.0F, 0.0F);
		}
	}

	private static float renderCharAtPos(FontRenderer fr, ImprovedTessellator tes, float posX, float posY, int allowedIndex, char textChar, boolean margin) {
		if (textChar == ' ') {
			return 4.0F;
		}

		if (allowedIndex > 0 && !fr.unicodeFlag) {
			return renderDefaultChar(fr, tes, posX, posY, allowedIndex + 32, margin);
		}

		return renderUnicodeChar(fr, tes, posX, posY, textChar, margin);
	}

	private static void bindTexture(ResourceLocation resourceLocation) {
		Minecraft.getMinecraft().renderEngine.bindTexture(resourceLocation);
	}

	private static float renderDefaultChar(FontRenderer fr, ImprovedTessellator tes, float posX, float posY, int textChar, boolean shouldMargin) {
		float u = ((textChar & 0xF) * FONT_WIDTH);
		float v = ((textChar >> 4) * FONT_WIDTH);
		float margin = shouldMargin ? 1.0F : 0.0F;

		bindTexture(fr.locationFontTexture);

		float width = fr.charWidth[textChar] - 0.01F - 1.0f;
		float invTex = 1.0f / 128.0f;

		int color = ColorBGRManager.rgbToBgr(currentColor) | 0xFF_000000;
		addVertexWithUV(tes, posX + margin, posY, u * invTex, v * invTex, color);
		addVertexWithUV(tes, posX - margin, posY + 7.99F, u * invTex, (v + 7.99F) * invTex, color);
		addVertexWithUV(tes, posX + width - margin, posY + 7.99F, (u + width) * invTex, (v + 7.99F) * invTex, color);
		addVertexWithUV(tes, posX + width + margin, posY, (u + width) * invTex, v * invTex, color);

		return fr.charWidth[textChar];
	}


	private static float renderUnicodeChar(FontRenderer fr, ImprovedTessellator tes, float posX, float posY, char unicode, boolean margin) {
		if (fr.glyphWidth[unicode] == 0) {
			return 0.0F;
		}

		int unicodeType = unicode / 256;
		fr.loadGlyphTexture(unicodeType);

		int glyphHigh = fr.glyphWidth[unicode] >>> 4;
		int glyphLow = (fr.glyphWidth[unicode] & 0xF) + 1;

		float u = (float) ((unicode & 0xF) * 16) + glyphHigh;
		float v = (float) ((unicode & 0xFF) / 16 * 16);

		float diff = glyphLow - glyphHigh - 0.02F;
		float marginSize = margin ? 1.0F : 0.0F;

		float invTex = 1.0f / 256.0f;

		addVertexWithUV(tes, posX + marginSize, posY, u * invTex, v * invTex, fr.textColor);
		addVertexWithUV(tes, posX - marginSize, posY + 7.99F, u * invTex, (v + 15.98F) * invTex, fr.textColor);
		addVertexWithUV(tes, posX + diff / 2.0F - marginSize, posY + 7.99F, (u + diff) * invTex, (v + 15.98F) * invTex, fr.textColor);
		addVertexWithUV(tes, posX + diff / 2.0F + marginSize, posY, (u + diff) * invTex, v * invTex, fr.textColor);

		return (glyphLow - glyphHigh) * 0.5F + 1.0F;
	}

	private static class FontRenderData {
		private float x, y, offset;
		private int color;
	}
}
