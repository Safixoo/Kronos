package dev.safixo.core.hooks;

import dev.safixo.client.render.gfx.state.GlMatrixTracker;
import dev.safixo.client.render.gfx.state.GlStateTracker;
import dev.safixo.client.render.pipelines.terrain.cull.FrustumCuller;
import dev.safixo.core.HookUtils;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import org.joml.Matrix4f;

import java.nio.Buffer;
import java.nio.FloatBuffer;

@SuppressWarnings("unused")
public class FrustumHook {
	public static void init(ClippingHelperImpl clipper) {
		FrustumCuller.projectionMatrix.set(GlMatrixTracker.PROJECTION_STACK.top());
		FrustumCuller.modelViewMatrix.set(GlMatrixTracker.MODEL_VIEW_STACK.top());

		Matrix4f mvp = FrustumCuller.projectionMatrix.mul(FrustumCuller.modelViewMatrix, new Matrix4f());

		FrustumCuller.processMatrices(mvp);
	}

	public static boolean isBoxInFrustum(ClippingHelper clipper, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		return FrustumCuller.withinFrustumBounds((float) minX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ);
	}
}
