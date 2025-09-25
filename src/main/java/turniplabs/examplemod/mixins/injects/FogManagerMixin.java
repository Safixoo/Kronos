package turniplabs.examplemod.mixins.injects;

import net.minecraft.client.render.FogManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import turniplabs.examplemod.client.render.util.data.FogData;

@Mixin(value = FogManager.class, remap = false)
public abstract class FogManagerMixin {
	@Shadow public float fogRed;
	@Shadow public float fogGreen;
	@Shadow public float fogBlue;

	@Redirect(method = "setupFog", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFogf(IF)V"))
	private void glFogf(int mode, float param) {
		if (mode == GL11.GL_FOG_START) {
			FogData.FOG_START = param;
		} else if (mode == GL11.GL_FOG_END) {
			FogData.FOG_END = param;
		} else if (mode == GL11.GL_FOG_DENSITY) {
			FogData.FOG_DENSITY = param;
		}

		GL11.glFogf(mode, param);
	}

	@Redirect(method = "setupFog", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFogi(II)V"))
	private void glFogi(int mode, int param) {
		if (mode == GL11.GL_FOG_MODE) {
			FogData.FOG_MODE = param;
		}
		GL11.glFogf(mode, param);
	}

	@Inject(method = "setupFog", at = @At("HEAD"))
	public void setupFogStart(int fogMode, float farPlaneDistance, float partialTick, CallbackInfo ci) {
		FogData.FOG_COLOR[0] = this.fogRed;
		FogData.FOG_COLOR[1] = this.fogGreen;
		FogData.FOG_COLOR[2] = this.fogBlue;
		FogData.FOG_COLOR[3] = 0.5f;
	}
}
