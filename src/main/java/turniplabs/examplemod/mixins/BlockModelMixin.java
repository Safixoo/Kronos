package turniplabs.examplemod.mixins;

import net.minecraft.client.render.RenderBlocks;
import net.minecraft.client.render.block.model.BlockModel;
import net.minecraft.core.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockModel.class, remap = false)
public class BlockModelMixin {
	@Shadow
	public static RenderBlocks renderBlocks;
	@Unique
	private static final RenderBlocks DUMMY = new RenderBlocks();

	@Inject(
		method = "<init>",
		at = @At("TAIL")
	)
	private void avoidNull(Block<?> block, CallbackInfo ci) {
		// For a reason, in my instance it constantly crashes because of a null
		// renderBlocks when rendering inventory, using a dummy first time fixes it.
		if (renderBlocks == null) {
			renderBlocks = DUMMY;
		}
	}
}
