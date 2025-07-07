package turniplabs.examplemod.mixins.injects;

import net.minecraft.core.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import turniplabs.examplemod.client.util.BlocksFlags;

@Mixin(value = Block.class, remap = false)
public class BlocksMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void addBlockToOurList(CallbackInfo ci) {
		BlocksFlags.addBlockToList((Block) (Object) this);
	}
}
