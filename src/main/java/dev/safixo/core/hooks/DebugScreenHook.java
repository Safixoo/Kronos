package dev.safixo.core.hooks;

import dev.safixo.client.render.SectionManager;
import net.minecraft.client.gui.FontRenderer;

public class DebugScreenHook {
	public static void drawStringWithShadow(FontRenderer fontRenderer, String text, int x, int y, int color) {
		if (text.contains("Allocated")) {
			SectionManager manager = SectionManager.getCurrentInstance();

			int offY = y + 70;
			x += 20;

			fontRenderer.drawString("Safixo Renderer: v0.0", x, offY, color, true);
			offY += 12;
			fontRenderer.drawString("Sections: " + manager.allocatedSections(), x, offY, color, true);
			offY += 12;
			fontRenderer.drawString("Regions: " + manager.getRegionCount(), x, offY, color, true);
			offY += 12;
			fontRenderer.drawString("VRAM U/A: " + manager.getMemoryUsed() + "/" + manager.getMemoryTotal() + "MiB", x, offY, color, true);
			offY += 12;

			x -= 20;
		}

		fontRenderer.drawString(text, x, y, color, true);
	}
}
