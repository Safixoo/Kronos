package dev.safixo.client.render.pipelines.cloud;

import dev.safixo.client.render.gfx.shader.GlProgram;
import dev.safixo.client.util.data.FogData;
import org.lwjgl.opengl.GL20;

public class CloudProgram extends GlProgram {
	private int u_CloudEnd, u_FogColor, u_CloudColor;
	private int u_CloudOff;

	public CloudProgram() {
		super("clouds/clouds_vertex.glsl", "clouds/clouds_fragment.glsl");
	}

	@Override
	public void processUniformLocations() {
		this.u_CloudEnd = GL20.glGetUniformLocation(this.getHandle(), "u_CloudEnd");
		this.u_FogColor = GL20.glGetUniformLocation(this.getHandle(), "u_FogColor");
		this.u_CloudColor = GL20.glGetUniformLocation(this.getHandle(), "u_CloudColor");
		this.u_CloudOff = GL20.glGetUniformLocation(this.getHandle(), "u_CloudOff");
	}

	public void uploadUniforms(int distance, float r, float g, float b) {
		GL20.glUniform1f(this.u_CloudEnd, distance);

		float[] fogColor = FogData.FOG_COLOR;

		GL20.glUniform4f(this.u_FogColor, fogColor[0] * r, fogColor[1] * g, fogColor[2] * b, 0.0f);
		GL20.glUniform3f(this.u_CloudColor, r, g, b);
	}

	public void setOffset(float worldOffsetX, float worldOffsetY, float worldOffsetZ) {
		GL20.glUniform3f(this.u_CloudOff, worldOffsetX, worldOffsetY, worldOffsetZ);
	}
}
