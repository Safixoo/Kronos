package dev.safixo.client.render.pipelines.terrain.shader;

import dev.safixo.client.render.gfx.shader.ShaderDefine;
import dev.safixo.client.render.gfx.state.GlFogTracker;
import dev.safixo.client.render.gfx.state.GlStateTracker;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;

public class LinearFogProgram extends TerrainProgram {
	private int u_FogNegInvRadius, u_FogEndInvRad;

	@Override
	public void processUniformLocations() {
		super.processUniformLocations();

		this.u_FogEndInvRad = GL20.glGetUniformLocation(this.getHandle(), "u_FogEndInvRad");
		this.u_FogNegInvRadius = GL20.glGetUniformLocation(this.getHandle(), "u_FogNegInvRadius");
	}

	@Override
	public void compile() {
		List<ShaderDefine> shaderDefines = new ArrayList<>();
		shaderDefines.add(new ShaderDefine("FOG_LINEAR"));
		this.compile(shaderDefines);
	}

	@Override
	public void setupUniforms(boolean noFog) {
		super.setupUniforms(noFog);

		// (u_FogEnd - v_Distance) / (u_FogEnd - u_FogStart)
		// (u_FogNegInvRadius * v_Distance) + u_FogEndNegInvRadius;
		float start = GlFogTracker.FOG_START;
		float end = GlFogTracker.FOG_END;

		float radius = end - start;
		float fogNegInvRadius = 1.0F / radius;
		float fogEndInvRad = end * fogNegInvRadius;

		GL20.glUniform1f(this.u_FogEndInvRad, noFog ? 1E+12F : fogEndInvRad);
		GL20.glUniform1f(this.u_FogNegInvRadius, noFog ? 1E+12F : -fogNegInvRadius);
	}
}
