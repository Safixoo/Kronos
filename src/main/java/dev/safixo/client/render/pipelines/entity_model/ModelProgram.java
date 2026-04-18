package dev.safixo.client.render.pipelines.entity_model;

import dev.safixo.client.render.gfx.shader.GlProgram;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.core.hooks.GlStateTracker;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

public class ModelProgram extends GlProgram {
	private int u_ModelTex;
	private int u_MatrixIndex, u_ProjectionMat, u_InvViewMat;

	public ModelProgram() {
		super("model/entity_vertex.glsl", "model/entity_fragment.glsl");
	}

	@Override
	public void processUniformLocations() {
		this.u_ModelTex = GL20.glGetUniformLocation(this.getHandle(), "u_ModelTex");
		this.u_MatrixIndex = GL20.glGetUniformLocation(this.getHandle(), "u_MatrixIndex");
		this.u_ProjectionMat = GL20.glGetUniformLocation(this.getHandle(), "u_ProjectionMat");
		this.u_InvViewMat = GL20.glGetUniformLocation(this.getHandle(), "u_InvViewMat");
	}

	private static final FloatBuffer MATRIX = NativeBuffer.memAllocFloat(16);

	public void uploadUniforms() {
		GL20.glUniform1i(this.u_ModelTex, 0);
		GL20.glUniformMatrix4(this.u_ProjectionMat, false, GlStateTracker.PROJECTION_STACK.top().get(MATRIX));
		GL20.glUniformMatrix4(this.u_InvViewMat, false, ModelQueue.MODEL_QUEUE.viewMatrix.get(MATRIX));
	}

	public void uploadMatrixIndex(int index) {
		GL20.glUniform1i(this.u_MatrixIndex, index);
	}
}
