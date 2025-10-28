package dev.safixo.core.hooks;

import dev.safixo.client.render.pipelines.terrain.SectionManager;
import dev.safixo.client.render.pipelines.terrain.cull.FrustumCuller;
import dev.safixo.core.HookUtils;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.nio.Buffer;
import java.nio.FloatBuffer;

public class FrustumHook {
	public static void init(ClippingHelperImpl clipper) {
		FrustumCuller.modelViewBuff = (FloatBuffer) HookUtils.getFieldStatic(ActiveRenderInfo.class, "modelview", "field_74594_j");
		FrustumCuller.projectionBuff = (FloatBuffer) HookUtils.getFieldStatic(ActiveRenderInfo.class, "projection", "field_74595_k");

		((Buffer) FrustumCuller.modelViewBuff).rewind().limit(16);
		((Buffer) FrustumCuller.projectionBuff).rewind().limit(16);

		FrustumCuller.projectionMatrix.set(FrustumCuller.projectionBuff);
		FrustumCuller.modelViewMatrix.set(FrustumCuller.modelViewBuff);

		Matrix4f combined = FrustumCuller.projectionMatrix.mul(FrustumCuller.modelViewMatrix, new Matrix4f());

		FrustumCuller.processMatrices(combined);
	}

	public static boolean isBoxInFrustum(ClippingHelper clipper, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		return FrustumCuller.testAab((float) minX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ);
	}
}
