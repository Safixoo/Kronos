package turniplabs.examplemod.mixins.injects;

import net.minecraft.client.render.terrain.VertexData;
import net.minecraft.client.render.tessellator.TessellatorBase;
import net.minecraft.client.render.tessellator.TessellatorStandard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import turniplabs.examplemod.client.vertex.VertexWriterManager;

// TODO: Rewrite injections completely, maybe use ASM to avoid allocations.
@Mixin(value = TessellatorStandard.class, remap = false)
public abstract class TesselatorStandardMixin extends TessellatorBase {
	@Shadow
	public abstract void checkIsDrawing();
	@Shadow
	public VertexData data;

	@Inject(method = "addVertex", at = @At("HEAD"), cancellable = true)
	public void passPositionAndUV(double x, double y, double z, CallbackInfo ci) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		if (!man.isDrawing) {
			return;
		}

		man.x = (float) x;
		man.y = (float) y;
		man.z = (float) z;

		man.addVertex();
		ci.cancel();
	}

	@Inject(method = "setTextureUV", at = @At("HEAD"), cancellable = true)
	public void passUV(double u, double v, CallbackInfo ci) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		if (!man.isDrawing) {
			return;
		}

		man.u = (float) u;
		man.v = (float) v;

		ci.cancel();
	}

	@Inject(method = "setColorRGBA", at = @At("HEAD"), cancellable = true)
	public void passColor(int r, int g, int b, int a, CallbackInfo ci) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		if (!man.isDrawing) {
			return;
		}

		man.color = (a << 24 | b << 16 | g << 8 | r);
		ci.cancel();
	}

	@Inject(method = "setLightmapCoord", at = @At("HEAD"), cancellable = true)
	public void setLightmap(int lightmapCoord, CallbackInfo ci) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		if (!man.isDrawing) {
			return;
		}

		man.lightMap = lightmapCoord;
		ci.cancel();
	}


	@Inject(method = "setTranslation", at = @At("HEAD"), cancellable = true)
	public void passTranslation(double x, double y, double z, CallbackInfo ci) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		if (!man.isDrawing) {
			return;
		}

		man.trasX = (float) x;
		man.trasY = (float) y;
		man.trasZ = (float) z;

		ci.cancel();
	}

	@Inject(method = "offsetTranslation", at = @At("HEAD"), cancellable = true)
	public void addOffsetTranslation(float x, float y, float z, CallbackInfo ci) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		if (!man.isDrawing) {
			return;
		}

		man.trasX += x;
		man.trasY += y;
		man.trasZ += z;

		ci.cancel();
	}

	@Inject(method = "checkIsDrawing", at = @At("HEAD"), cancellable = true)
	public void manageDrawing(CallbackInfo ci) {
		VertexWriterManager man = VertexWriterManager.getCurrentInstance();

		if (!man.isDrawing) {
			return;
		}

		ci.cancel();
	}
}
