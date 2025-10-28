package dev.safixo.core.hooks;

import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.vertex.GlVertexArrayObject;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.render.vertex.VertexWriterManager;
import dev.safixo.core.HookUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureObject;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.util.Map;
import java.util.Random;

public class FontRendererHook {
	public static final float FONT_HEIGHT = 9, FONT_WIDTH = 8;
	private static final int[] ALLOWED_CHARACTERS_INDEX = new int[256];

	private final ResourceLocation[] unicodePageLocations = new ResourceLocation[256];
	private final ResourceLocation FONT_TEXTURE = new ResourceLocation("textures/font/ascii.png");
	private static int FONT_TEXTURE_ID = -1;

	private float r, g, b, a;
	private int textColor, currentColor;

	private boolean randomStyle, boldStyle, italicStyle, underlineStyle, strikethroughStyle, unicodeText;

	private int[] charWidth;
	public Random fontRandom;
	private byte[] glyphWidth;
	private int[] colorCode;

	private TextureManager textureManager;
	private String lastResourcePack;
	private ResourceLocation lastTexture;

	private final ObjectArrayFIFOQueue<FontRenderData> underlines = new ObjectArrayFIFOQueue<>();
	private final ObjectArrayFIFOQueue<FontRenderData> spikeThrough = new ObjectArrayFIFOQueue<>();

	private static final Reference2ReferenceOpenHashMap<FontRenderer, FontRendererHook> HOOKS = new Reference2ReferenceOpenHashMap<>();

	private void copyFontRendererData(FontRenderer renderer) {
		this.charWidth = (int[]) HookUtils.getFieldObj(renderer, "charWidth", "field_78286_d");
		this.fontRandom = (Random) HookUtils.getFieldObj(renderer, "fontRandom", "field_78289_c");
		this.glyphWidth = (byte[]) HookUtils.getFieldObj(renderer, "glyphWidth", "field_78287_e");
		this.colorCode = (int[]) HookUtils.getFieldObj(renderer, "colorCode", "field_78285_g");
		this.textureManager = Minecraft.getMinecraft().getTextureManager();
		this.unicodeText = (Boolean) HookUtils.getFieldObj(renderer, "unicodeFlag", "field_78293_l");

		for (char i = 0; i < 256; i++) {
			ALLOWED_CHARACTERS_INDEX[i] = ChatAllowedCharacters.allowedCharacters.indexOf(i);
		}

//		int handle = this.vertexArrayObject.getHandle();
//
//		if (handle == 0x80000000) {
//			this.vertexArrayObject.generateHandle();
//		}

		if (FONT_TEXTURE_ID == -1) {
			Map<?, ?> textureMap = (Map<?, ?>) HookUtils.getFieldObj(this.textureManager, "mapTextureObjects", "field_110585_a");
			TextureObject texObj = (TextureObject) textureMap.get(FONT_TEXTURE);
			FONT_TEXTURE_ID = texObj.getGlTextureId();
		}

//		this.vertexBuffer.allocate(UnsafeUtil.NULL, 16 * 4 * 1024);
//
//		{
//			this.vertexArrayObject.bind(this.vertexBuffer);
//			this.vertexBuffer.bind();
//
//			GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
//			GL11.glVertexPointer(2, GL11.GL_FLOAT, 16, 0);
//
//			GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
//			GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 16, 8);
//
//			this.vertexBuffer.unbind();
//			this.vertexArrayObject.unbind();
//		}
	}

	public static FontRendererHook getUsableInstance(FontRenderer renderer) {
		FontRendererHook hook = HOOKS.get(renderer);

		// There isn't a instance for this font renderer.
		if (hook == null) {
			hook = new FontRendererHook();
			HOOKS.put(renderer, hook);
		}

		ResourcePackRepository resourceRepository = Minecraft.getMinecraft().getResourcePackRepository();

		// If the resources changed, re-copy font-renderer arrays and render-data.
		if (!resourceRepository.getResourcePackName().equals(hook.lastResourcePack)) {
			hook.lastResourcePack = resourceRepository.getResourcePackName();
			hook.copyFontRendererData(renderer);
		}

		return hook;
	}

	public static int renderString(FontRenderer renderer, String text, int posX, int posZ, int color, boolean margin) {
		if (text == null) {
			return 0;
		}

		FontRendererHook hook = getUsableInstance(renderer);
		return hook.renderStringFast(text, posX, posZ, color, margin);
	}

	public int renderStringFast(String text, int posX, int posZ, int color, boolean margin) {
//		this.vertexArrayObject.bind(this.vertexBuffer);
//		VertexWriterManager.DEFAULT_INSTANCE.startDrawing();

		if ((color & 0xFF000000) == 0) {
			color |= 0xFF000000;
		}

		if (margin) {
			color = (color & 0xFCFCFC) >> 2 | color & 0xFF000000;
		}

		this.a = (color >>> 24) * (1.0f / 255.0f);
		this.r = (color >>> 16 & 0xFF) * (1.0f / 255.0f);
		this.b = (color >>> 8 & 0xFF) * (1.0f / 255.0f);
		this.g = (color >>> 0 & 0xFF) * (1.0f / 255.0f);
		GL11.glColor4f(this.r, this.b, this.g, this.a);
		this.currentColor = this.textColor = color;

		ImprovedTessellator tes = (ImprovedTessellator) Tessellator.instance;
		tes.startDrawing(GL11.GL_QUADS);

		// Render main string.
		float returnVal = this.renderStringAtPos(tes, posX, posZ, text, margin);

		// Draw extra styles.
		if (!this.underlines.isEmpty() || !this.spikeThrough.isEmpty()){
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			tes.startDrawingQuads();

			this.drawUnderlines();
			this.drawSpikeThrough();

			tes.draw();
			GL11.glEnable(GL11.GL_TEXTURE_2D);
		}

		if (this.lastTexture != null || tes.isDrawing) {
			tes.draw();
		}

		// Flush cached last texture bind.
		this.lastTexture = null;

		return (int) returnVal;
	}

	private static char toLowerCase(char ch) {
		// if upper-case char, lower case.
		if (ch >= 'A' && ch <= 'Z') {
			ch |= 0x20;
		}

		return ch;
	}

	private void customizeMessage(String text, int charInd, boolean cond) {
		char nextChar = toLowerCase(text.charAt(charInd + 1));
		int type = "0123456789abcdefklmnor".indexOf(nextChar);

		if (type < 16) {
			this.randomStyle = false;
			this.boldStyle = false;
			this.strikethroughStyle = false;
			this.underlineStyle = false;
			this.italicStyle = false;
			if (type < 0) {
				type = 15;
			}

			if (cond) {
				type += 16;
			}

			int color = this.colorCode[type];
			GL11.glColor3f((float)(color >> 16) / 255.0F, (float)(color >> 8 & 255) / 255.0F, (float)(color & 255) / 255.0F);
			this.currentColor = color;
		} else if (type == 16) {
			this.randomStyle = true;
		} else if (type == 17) {
			this.boldStyle = true;
		} else if (type == 18) {
			this.strikethroughStyle = true;
		} else if (type == 19) {
			this.underlineStyle = true;
		} else if (type == 20) {
			this.italicStyle = true;
		} else {
			this.randomStyle = false;
			this.boldStyle = false;
			this.strikethroughStyle = false;
			this.underlineStyle = false;
			this.italicStyle = false;
			GL11.glColor3f(this.r, this.g, this.b);
			this.currentColor = this.textColor;
		}
	}

	private float renderStringAtPos(ImprovedTessellator tes, float posX, float posY, String text, boolean cond) {
		char[] textArr = text.toCharArray();

		for (int charInd = 0; charInd < textArr.length; charInd++) {
			char asciiChar = textArr[charInd];

			// Customize message based in flags.
			if (asciiChar == '§' && charInd + 1 < textArr.length) {
				customizeMessage(text, charInd, cond);
				charInd++;
				continue;
			}

			if (tes.offset + 80 > tes.capacity) {
				tes.resize();
			}

			int allowedIndex = asciiChar >= 256 ? -1 : ALLOWED_CHARACTERS_INDEX[asciiChar];

			if (this.randomStyle && allowedIndex > 0) {
				int randCh;
				do {
					randCh = this.fontRandom.nextInt(ChatAllowedCharacters.allowedCharacters.length());
				} while (this.charWidth[allowedIndex + 32] != this.charWidth[randCh + 32]);

				allowedIndex = randCh;
			}

			float marginSize = this.unicodeText ? 0.5F : 1.0F;
			boolean margin = (allowedIndex <= 0 || this.unicodeText) && cond;

			float offset;

			if (margin) {
				offset = this.renderCharAtPos(tes, posX - marginSize, posY - marginSize, allowedIndex, asciiChar, this.italicStyle);
			} else {
				offset = this.renderCharAtPos(tes, posX, posY, allowedIndex, asciiChar, this.italicStyle);
			}

			if (this.boldStyle) {
				if (margin) {
					this.renderCharAtPos(tes, posX, posY - marginSize, allowedIndex, asciiChar, this.italicStyle);
				} else {
					this.renderCharAtPos(tes, posX + marginSize, posY, allowedIndex, asciiChar, this.italicStyle);
				}

				if (margin) {
					posX += marginSize;
					posY += marginSize;
				}

				offset++;
			}

			if (this.strikethroughStyle || this.underlineStyle) {
				if (this.strikethroughStyle) {
				}

				if (this.underlineStyle) {
				}
			}

			posX += (int) offset;
		}

		return posX;
	}

	private void drawUnderlines() {
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

	private static void addVertexWithUV(ImprovedTessellator tes, float x, float y, float u, float v) {
		long ptr = tes.vertexPtr + tes.offset;

		UnsafeUtil.memPutFloat(ptr + 0, x);
		UnsafeUtil.memPutFloat(ptr + 4, y);
		UnsafeUtil.memPutFloat(ptr + 8, 0);

		UnsafeUtil.memPutFloat(ptr + 12, u);
		UnsafeUtil.memPutFloat(ptr + 16, v);

		tes.vertices++;
		tes.flags |= ImprovedTessellator.VERTEX_UV;
		tes.offset += 20;
	}

	private void drawSpikeThrough() {
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

	private float renderCharAtPos(ImprovedTessellator tes, float posX, float posY, int allowedIndex, char textChar, boolean margin) {
		if (textChar == ' ') {
			return 4.0F;
		}

		if (allowedIndex > 0 && !this.unicodeText) {
			return this.renderDefaultChar(tes, posX, posY, allowedIndex + 32, margin);
		}

		return this.renderUnicodeChar(tes, posX, posY, textChar, margin);
	}

	private ResourceLocation getUnicodePageLocation(int par1) {
		if (this.unicodePageLocations[par1] == null) {
			this.unicodePageLocations[par1] = new ResourceLocation(String.format("textures/font/unicode_page_%02x.png", par1));
		}

		return this.unicodePageLocations[par1];
	}

	private float renderDefaultChar(ImprovedTessellator tes, float posX, float posY, int textChar, boolean shouldMargin) {
		float u = ((textChar & 15) * FONT_WIDTH);
		float v = ((textChar >> 4) * FONT_WIDTH);
		float margin = shouldMargin ? 1.0F : 0.0F;

		bindTextureAndRender(tes, FONT_TEXTURE);

		float width = this.charWidth[textChar] - 0.01F - 1.0f;
		float invTex = 1.0f / 128.0f;

		addVertexWithUV(tes, posX + margin, posY, u * invTex, v * invTex);
		addVertexWithUV(tes, posX - margin, posY + 7.99F, u * invTex, (v + 7.99F) * invTex);
		addVertexWithUV(tes, posX + width - margin, posY + 7.99F, (u + width) * invTex, (v + 7.99F) * invTex);
		addVertexWithUV(tes, posX + width + margin, posY, (u + width) * invTex, v * invTex);

		return this.charWidth[textChar];
	}

	private void loadGlyphTexture(ImprovedTessellator tes, int par1) {
		bindTextureAndRender(tes, getUnicodePageLocation(par1));
	}

	private void bindTextureAndRender(ImprovedTessellator tes, ResourceLocation texture) {
		if (texture != this.lastTexture) {
			if (this.lastTexture != null) {
				tes.draw();
				tes.startDrawing(GL11.GL_QUADS);
			}

			this.lastTexture = texture;

			if (texture != FONT_TEXTURE) {
				this.textureManager.bindTexture(texture);
			} else {
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, FONT_TEXTURE_ID);
			}
		}
	}

	private float renderUnicodeChar(ImprovedTessellator tes, float posX, float posY, char unicode, boolean margin) {
		if (this.glyphWidth[unicode] == 0) {
			return 0.0F;
		}

		int unicodeType = unicode >> 8;
		this.loadGlyphTexture(tes, unicodeType);

		int glyphHigh = this.glyphWidth[unicode] >>> 4;
		int glyphLow = (this.glyphWidth[unicode] & 0xF) + 1;

		float u = (float) ((unicode & 0xF) * 16) + glyphHigh;
		float v = (float) ((unicode & 0xFF) / 16 * 16);

		float diff = glyphLow - glyphHigh - 0.02F;
		float marginSize = margin ? 1.0F : 0.0F;

		float invTex = 1.0f / 256.0f;

		addVertexWithUV(tes, posX + marginSize, posY, u * invTex, v * invTex);
		addVertexWithUV(tes, posX - marginSize, posY + 7.99F, u * invTex, (v + 15.98F) * invTex);
		addVertexWithUV(tes, posX + diff / 2.0F - marginSize, posY + 7.99F, (u + diff) * invTex, (v + 15.98F) * invTex);
		addVertexWithUV(tes, posX + diff / 2.0F + marginSize, posY, (u + diff) * invTex, v * invTex);

		return (glyphLow - glyphHigh) * 0.5F + 1.0F;
	}

	private static class FontRenderData {
		private float x, y, offset;
		private int color;
	}
}
