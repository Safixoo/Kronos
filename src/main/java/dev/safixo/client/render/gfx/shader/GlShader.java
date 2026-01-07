package dev.safixo.client.render.gfx.shader;

import dev.safixo.client.render.gfx.ShaderLoader;
import org.lwjgl.opengl.GL20;

import java.util.List;

public class GlShader {
	private int id;

	public GlShader(int shaderType) {
		this.id = GL20.glCreateShader(shaderType);
	}

	public void delete() {
		GL20.glDeleteShader(this.id);
		this.id = -1;
	}

	public int getHandle() {
		return this.id;
	}

	public void compile(List<ShaderDefine> shaderDefine, String shaderPath) {
		ShaderLoader.compileShader(shaderDefine, shaderPath, this.id);
		String shaderLog = GL20.glGetShaderInfoLog(this.id, 250);

		if (!shaderLog.isEmpty()) {
			System.err.println("Shader " + shaderPath + " has logged the next info!: ");
			System.err.println(shaderLog);
		}
	}
}
