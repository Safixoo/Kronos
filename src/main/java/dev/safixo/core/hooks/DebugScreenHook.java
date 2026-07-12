package dev.safixo.core.hooks;

import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.util.data.FrameTimer;
import dev.safixo.client.util.MathExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

@SuppressWarnings("unused")
public class DebugScreenHook {
	public static int drawStringWithShadow(FontRenderer fontRenderer, String text, int x, int y, int color) {
		if (text.contains("Alloc")) {
			WorldManager manager = WorldManager.getCurrentInstance();

			int charWidth = fontRenderer.getStringWidth(text);
			int offY = y + 80;
			x += charWidth;

			int effect = (int) (((Math.sin(System.nanoTime() / 5E+8D) + 1) * 128) % 256);

			renderDebugOption(fontRenderer, "Kronos Renderer: v0.0", x, offY, 0x1F00F1 | Math.max(120, effect) << 8);
			offY += 10;
			renderDebugOption(fontRenderer, "Regions: " + manager.getRegionCount(), x, offY, color);
			offY += 10;
			renderDebugOption(fontRenderer, "VRAM U/A: " + manager.getMemoryUsed() + "/" + manager.getMemoryTotal() + "MiB", x, offY, color);

			x -= charWidth;

			if (Minecraft.getMinecraft().gameSettings.showDebugProfilerChart && !RenderGlobalHook.OPTIFINE_ACTIVE) {
				renderLagometer(fontRenderer);
			}
		}

		return fontRenderer.drawString(text, x, y, color, true);
	}

	private static void renderLagometer(FontRenderer fontRenderer) {
		GL11.glDisable(GL11.GL_DEPTH_TEST);

		FrameTimer frametimer = FrameTimer.getInstance();
		int lastIndex = frametimer.getLastIndex();
		int maxIndex = frametimer.getIndex();
		long[] frames = frametimer.getFrames();

		Minecraft mc = Minecraft.getMinecraft();
		ScaledResolution scaledresolution = new ScaledResolution(mc.gameSettings, mc.displayWidth, mc.displayHeight);

		int frameIndex = lastIndex;
		int framesDrawn = 0;

		Gui.drawRect(0, scaledresolution.getScaledHeight() - 60, 240, scaledresolution.getScaledHeight(), 0x90505050);

		while (frameIndex != maxIndex) {
			int lagometerValue = frametimer.getLagometerValue(frames[frameIndex], 30);
			int color = getFrameColor(MathExt.clamp(lagometerValue, 0, 60));

			drawVerticalLine(framesDrawn++, scaledresolution.getScaledHeight(), scaledresolution.getScaledHeight() - lagometerValue, color);

			frameIndex = frametimer.parseIndex(frameIndex + 1);
		}

		Gui.drawRect(1, scaledresolution.getScaledHeight() - 30 + 1, 14, scaledresolution.getScaledHeight() - 30 + 10, 0x90505050);
		fontRenderer.drawString("60", 2, scaledresolution.getScaledHeight() - 30 + 2, 0xe0e0e0);
		drawHorizontalLine(0, 239, scaledresolution.getScaledHeight() - 30, -1);
		Gui.drawRect(1, scaledresolution.getScaledHeight() - 60 + 1, 14, scaledresolution.getScaledHeight() - 60 + 10, 0x90505050);
		int i = fontRenderer.drawString("30", 2, scaledresolution.getScaledHeight() - 60 + 2, 0xe0e0e0);
		drawHorizontalLine(0, 239, scaledresolution.getScaledHeight() - 60, -1);
		drawHorizontalLine(0, 239, scaledresolution.getScaledHeight() - 1, -1);
		drawVerticalLine(0, scaledresolution.getScaledHeight() - 60, scaledresolution.getScaledHeight(), -1);
		drawVerticalLine(239, scaledresolution.getScaledHeight() - 60, scaledresolution.getScaledHeight(), -1);

		if (mc.gameSettings.limitFramerate <= 120)
		{
			drawHorizontalLine(0, 239, scaledresolution.getScaledHeight() - 60 + mc.gameSettings.limitFramerate / 2, 0xff00ffff);
		}

		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}

	protected static void drawHorizontalLine(int par1, int par2, int par3, int par4) {
		if (par2 < par1) {
			int var5 = par1;
			par1 = par2;
			par2 = var5;
		}

		Gui.drawRect(par1, par3, par2 + 1, par3 + 1, par4);
	}

	protected static void drawVerticalLine(int par1, int par2, int par3, int par4) {
		if (par3 < par2) {
			int var5 = par2;
			par2 = par3;
			par3 = var5;
		}

		Gui.drawRect(par1, par2 + 1, par1 + 1, par3, par4);
	}

	private static int getFrameColor(int lagometerValue) {
		if (lagometerValue < 30) {
			return blendColors(0xFF00FF00, 0xFFFFFF00, (float) lagometerValue / 30);
		}
		return blendColors(0xFFFFFF00, 0xFFFF0000, (float) (lagometerValue - 30) / 30);
	}

	private static int blendColors(int color0, int color1, float factor) {
		int r0 = color0 >> 24 & 0xFF;
		int g0 = color0 >> 16 & 0xFF;
		int b0 = color0 >> 8 & 0xFF;
		int a0 = color0 & 0xFF;

		int r1 = color1 >> 24 & 0xFF;
		int g1 = color1 >> 16 & 0xFF;
		int b1 = color1 >> 8 & 0xFF;
		int a1 = color1 & 0xFF;

		int factorInt = (int) (factor * (1 << 10));

		int r = lerpU8(r0, r1, factorInt);
		int g = lerpU8(g0, g1, factorInt);
		int b = lerpU8(b0, b1, factorInt);
		int a = lerpU8(a0, a1, factorInt);

		return (r << 24 | g << 16 | b << 8 | a);
	}

	private static int lerpU8(int a, int b, int t) {
		return a + ((b - a) * t >> 10);
	}

	public static void renderDebugOption(FontRenderer fontRenderer, String string, int x, int y, int color) {
		int charWidth = fontRenderer.getStringWidth(string);
		fontRenderer.drawString(string, x - charWidth, y, color, true);
	}
}
