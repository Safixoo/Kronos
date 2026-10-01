package dev.safixo.client.render.pipelines.sky;

import dev.safixo.client.render.gfx.shader.GlProgram;
import org.lwjgl.opengl.GL20;

public class SkyProgram extends GlProgram {
	public SkyProgram() {
		super("sky/sky_vertex.glsl", "sky/sky_fragment.glsl");
	}

	@Override
	public void processUniformLocations() {
		GL20.glUniform1i(GL20.glGetUniformLocation(this.getHandle(), "u_Tex"), 0);
	}
}
