package dev.safixo.client.render.pipelines.cloud;

import dev.safixo.client.render.gfx.shader.GlProgram;
import dev.safixo.client.util.ColorBGRManager;
import dev.safixo.core.hooks.GlStateTracker;
import org.lwjgl.opengl.GL20;

public class CloudProgram extends GlProgram {
	private int u_CloudOffset;
	private int u_Color;
	private int u_Distance;

	public CloudProgram() {
		super("clouds/clouds_vertex.glsl", "clouds/clouds_fragment.glsl");
	}

	@Override
	public void processUniformLocations() {
		this.u_CloudOffset = GL20.glGetUniformLocation(this.getHandle(), "u_CloudOffset");
		this.u_Color = GL20.glGetUniformLocation(this.getHandle(), "u_Color");
		this.u_Distance = GL20.glGetUniformLocation(this.getHandle(), "u_Distance");
	}

	public void uploadUniforms(float r, float g, float b, int distance) {
		GL20.glUniform3f(this.u_Color, r, g, b);
		GL20.glUniform1i(this.u_Distance, distance * CloudRenderer.CLOUD_WIDTH);
	}

	public void setOffset(float worldOffsetX, float worldOffsetY, float worldOffsetZ) {
		GL20.glUniform3f(this.u_CloudOffset, worldOffsetX * CloudRenderer.CLOUD_WIDTH, worldOffsetY, worldOffsetZ * CloudRenderer.CLOUD_WIDTH);
	}
}
