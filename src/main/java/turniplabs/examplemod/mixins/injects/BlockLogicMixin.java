package turniplabs.examplemod.mixins.injects;

import net.minecraft.core.block.BlockLogic;
import net.minecraft.core.util.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import turniplabs.examplemod.client.GlobalFlags;
import turniplabs.examplemod.client.util.interfaces.mixin.IBlockAABB;

@Mixin(value = BlockLogic.class, remap = false)
public class BlockLogicMixin {
	@Shadow
	@Final
	protected AABB bounds;

	@Inject(method = "setBlockBounds", at = @At("TAIL"))
	public void setBlockBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, CallbackInfo ci) {
		((IBlockAABB)this.bounds).calculateBounds();
	}

	@Inject(method = "getBounds", at = @At("HEAD"), cancellable = true)
	private void avoidCopy(CallbackInfoReturnable<AABB> cir) {
		if (GlobalFlags.MESHING) {
			cir.setReturnValue(this.bounds);
		}
	}
}
