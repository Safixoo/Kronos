package dev.safixo.client.render.pipelines.cloud;

import dev.safixo.client.render.gfx.shader.GlProgram;
import dev.safixo.client.render.util.data.FogData;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public class CloudProgram extends GlProgram {
	private int u_FogEnd, u_FogColor;
	private int u_CloudTex;

	public CloudProgram() {
		super("clouds/clouds_vertex.glsl", "clouds/clouds_fragment.glsl");
	}

	@Override
	public void processUniformLocations() {
		this.u_CloudTex = GL20.glGetUniformLocation(this.getHandle(), "u_CloudTex");

		this.u_FogEnd = GL20.glGetUniformLocation(this.getHandle(), "u_FogEnd");
		this.u_FogColor = GL20.glGetUniformLocation(this.getHandle(), "u_FogColor");
	}

	public void uploadUniforms() {
		GL20.glUniform1i(this.u_CloudTex, GL13.GL_TEXTURE0);
		GL20.glUniform1f(this.u_FogEnd, FogData.FOG_END);

		float[] fogColor = FogData.FOG_COLOR;
		GL20.glUniform4f(this.u_FogColor, fogColor[0], fogColor[1], fogColor[2], 0.0f);
	}
}
