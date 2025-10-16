package dev.safixo.core.hooks;

import dev.safixo.client.render.pipelines.terrain.SectionManager;
import net.minecraft.client.gui.FontRenderer;

public class DebugScreenHook {
	public static int drawStringWithShadow(FontRenderer fontRenderer, String text, int x, int y, int color) {
		if (text.contains("Allocated")) {
			SectionManager manager = SectionManager.getCurrentInstance();

			int charWidth = fontRenderer.getStringWidth(text);
			int offY = y + 80;
			x += charWidth;

			int effect = (int) (((Math.sin(System.nanoTime() / 5E+8D) + 1) * 128) % 256);

			renderDebugOption(fontRenderer, "Safixo Renderer: v0.0", x, offY, 0x1F00F1 | Math.max(120, effect) << 8);
			offY += 10;
			renderDebugOption(fontRenderer, "Sections: " + manager.allocatedSections(), x, offY, color);
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
