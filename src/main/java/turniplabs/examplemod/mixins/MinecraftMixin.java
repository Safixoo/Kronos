package turniplabs.examplemod.mixins;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, remap = false)
public class MinecraftMixin {
	@Inject(method = "startGame", at = @At("TAIL"))
	private void startingGame(CallbackInfo ci) {
//		this.terrainRenderer = new TerrainRendererMultiDraw( (Minecraft) (Object) this);
	}
}
