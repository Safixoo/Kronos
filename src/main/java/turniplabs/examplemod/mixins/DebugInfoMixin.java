package turniplabs.examplemod.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.hud.HudIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import turniplabs.examplemod.client.render.SectionManager;

@Mixin(value = HudIngame.class, remap = false)
public abstract class DebugInfoMixin {
	@Shadow
	protected abstract void drawDebugScreenLineRight(String string);

	@Inject(method = "drawDebugScreenLine(Ljava/lang/String;)V", at = @At("HEAD"))
	private void renderDebugInfo(String string, CallbackInfo ci) {
		if (string.equals("Better than Adventure! " + Minecraft.VERSION)) {
			SectionManager manager = SectionManager.getCurrentInstance();

			this.drawDebugScreenLineRight("");
			this.drawDebugScreenLineRight("Safixo Renderer: v0.0");
			this.drawDebugScreenLineRight("Sections: " + manager.allocatedSections());
			this.drawDebugScreenLineRight("Regions: " + manager.getRegionCount());
			this.drawDebugScreenLineRight("VRAM U/A: " + manager.getMemoryUsed() + "/" + manager.getMemoryTotal() + "MiB");
		}
	}
}
