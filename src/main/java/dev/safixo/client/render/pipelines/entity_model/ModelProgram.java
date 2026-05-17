package dev.safixo.client.render.pipelines.entity_model;

import dev.safixo.client.render.gfx.shader.GlProgram;
import dev.safixo.client.render.gfx.state.GlFogTracker;
import dev.safixo.client.render.gfx.state.GlMatrixTracker;
import dev.safixo.client.util.memory.NativeBuffer;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

public class ModelProgram extends GlProgram {
	private int u_FogNegInvRadius, u_FogEndInvRad, u_FogColor;
	private int u_ModelTex, u_LightTex;
	private int u_ProjectionMat, u_InvViewMat;

	public ModelProgram() {
		super("model/entity_vertex.glsl", "model/entity_fragment.glsl");
	}

	@Override
	public void processUniformLocations() {
		this.u_ModelTex = GL20.glGetUniformLocation(this.getHandle(), "u_ModelTex");
		this.u_LightTex = GL20.glGetUniformLocation(this.getHandle(), "u_LightTex");
		this.u_ProjectionMat = GL20.glGetUniformLocation(this.getHandle(), "u_ProjectionMat");
		this.u_InvViewMat = GL20.glGetUniformLocation(this.getHandle(), "u_InvViewMat");
		this.u_FogNegInvRadius = GL20.glGetUniformLocation(this.getHandle(), "u_FogNegInvRadius");
		this.u_FogEndInvRad = GL20.glGetUniformLocation(this.getHandle(), "u_FogEndInvRad");
		this.u_FogColor = GL20.glGetUniformLocation(this.getHandle(), "u_FogColor");

		this.useProgram();
		GL20.glUniform1i(this.u_ModelTex, 0);
		GL20.glUniform1i(this.u_LightTex, 1);
		this.disableProgram();
	}

	private static final FloatBuffer MATRIX = NativeBuffer.memAllocFloat(16);

	public void uploadUniforms() {
		GL20.glUniformMatrix4(this.u_ProjectionMat, false, GlMatrixTracker.PROJECTION_STACK.top().get(MATRIX));
		GL20.glUniformMatrix4(this.u_InvViewMat, false, ModelQueue.MODEL_QUEUE.viewMatrix.get(MATRIX));

		float start = GlFogTracker.FOG_START;
		float end = GlFogTracker.FOG_END;

		float radius = end - start;
		float fogNegInvRadius = 1.0F / radius;
		float fogEndInvRad = end * fogNegInvRadius;

		GL20.glUniform1f(this.u_FogEndInvRad, fogEndInvRad);
		GL20.glUniform1f(this.u_FogNegInvRadius, -fogNegInvRadius);
		GL20.glUniform3f(this.u_FogColor, GlFogTracker.FOG_COLOR_R, GlFogTracker.FOG_COLOR_G, GlFogTracker.FOG_COLOR_B);
	}

	public void uploadMatrixIndex(int index) {
	}
}
