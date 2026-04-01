package dev.safixo.client.render.pipelines.cloud;

import dev.safixo.client.render.gfx.shader.GlProgram;
import dev.safixo.client.util.ColorBGRManager;
import dev.safixo.core.hooks.GlStateTracker;
import org.lwjgl.opengl.GL20;

public class CloudProgram extends GlProgram {
	private int u_CloudOffset;
	private int u_CloudData;

	public CloudProgram() {
		super("clouds/clouds_vertex.glsl", "clouds/clouds_fragment.glsl");
	}

	@Override
	public void processUniformLocations() {
		this.u_CloudOffset = GL20.glGetUniformLocation(this.getHandle(), "u_CloudOffset");
		this.u_CloudData = GL20.glGetUniformLocation(this.getHandle(), "u_CloudData");
	}

	public void uploadUniforms(float r, float g, float b, int distance) {
		GL20.glUniform1i(this.u_CloudData, ColorBGRManager.packColor(r, g, b) | distance << 24);
	}

	public void setOffset(float worldOffsetX, float worldOffsetY, float worldOffsetZ) {
		GL20.glUniform3f(this.u_CloudOffset, worldOffsetX, worldOffsetY, worldOffsetZ);
	}
}
