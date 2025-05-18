package turniplabs.examplemod.mixins;

import net.minecraft.client.render.culling.Frustum;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import turniplabs.examplemod.client.ComplexFrustum;

import java.nio.FloatBuffer;

@Mixin(value = Frustum.class, remap = false)
public class FrustumMixin {
	@Shadow
	@Final
	private FloatBuffer _proj;
	@Shadow
	@Final
	private FloatBuffer _modl;

	@Unique
	private final Matrix4f projectionMatrix = new Matrix4f();
	@Unique
	private final Matrix4f modelViewMatrix = new Matrix4f();
	@Unique
	private final Matrix4f clippingMatrix = new Matrix4f();


	/**
	 * @author Safixo
	 * @reason Remplazo con JOML
	 */
	@Inject(method = "calculateFrustum", at = @At("HEAD"))
	private void calculateFrustum(CallbackInfo ci) {
		this._proj.rewind();
		this._modl.rewind();

		GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX, this._proj);
		GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, this._modl);

		this._proj.rewind();
		this._modl.rewind();

		this.projectionMatrix.set(this._proj);
		this.modelViewMatrix.set(this._modl);
		this.projectionMatrix.mul(this.modelViewMatrix, this.clippingMatrix);
		ComplexFrustum.set(this.clippingMatrix);
	}
}
