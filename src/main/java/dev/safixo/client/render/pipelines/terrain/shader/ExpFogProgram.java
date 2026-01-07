package dev.safixo.client.render.pipelines.terrain.shader;

import dev.safixo.client.render.gfx.shader.ShaderDefine;
import dev.safixo.core.hooks.GlStateTracker;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;

public class ExpFogProgram extends TerrainProgram {
	private int u_FogDensity;

	@Override
	public void processUniformLocations() {
		super.processUniformLocations();
		this.u_FogDensity = GL20.glGetUniformLocation(this.getHandle(), "u_FogDensity");
	}

	@Override
	public void compile() {
		List<ShaderDefine> shaderDefines = new ArrayList<>();
		shaderDefines.add(new ShaderDefine("FOG_EXP"));
		this.compile(shaderDefines);
	}

	@Override
	public void setupUniforms(boolean noFog) {
		super.setupUniforms(noFog);
		GL20.glUniform1f(this.u_FogDensity, GlStateTracker.FOG_DENSITY);
	}
}
