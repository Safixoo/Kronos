package dev.safixo.core.hooks;

import dev.safixo.client.render.pipelines.terrain.WorldManager;
import net.minecraft.client.gui.FontRenderer;

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
		}

		return fontRenderer.drawString(text, x, y, color, true);
	}

	public static void renderDebugOption(FontRenderer fontRenderer, String string, int x, int y, int color) {
		int charWidth = fontRenderer.getStringWidth(string);
		fontRenderer.drawString(string, x - charWidth, y, color, true);
	}
}
