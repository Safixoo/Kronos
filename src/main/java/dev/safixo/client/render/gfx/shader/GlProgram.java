package dev.safixo.client.render.gfx.shader;

import org.lwjgl.opengl.GL20;

import java.util.List;

public abstract class GlProgram {
	private String vertexShaderPath, fragmentShaderPath;
	private List<ShaderDefine> shaderDefines;
	private int id;

	public GlProgram(String vertexPath, String fragmentPath) {
		this();

		this.addShaderPath(vertexPath, fragmentPath);
		this.compile();

		this.processUniformLocations();
	}

	public GlProgram(List<ShaderDefine> shaderDefines, String vertexPath, String fragmentPath) {
		this();

		this.addShaderPath(vertexPath, fragmentPath);
		this.compile(shaderDefines);

		this.processUniformLocations();
	}

	public GlProgram() {
		this.id = GL20.glCreateProgram();
	}


	public void compile() {
		this.compile(null);
	}

	public void compile(List<ShaderDefine> shaderDefines) {
		if (shaderDefines == null) {
			shaderDefines = this.shaderDefines;
		} else {
			this.shaderDefines = shaderDefines;
		}

		GlShader vertexShader = new GlShader(GL20.GL_VERTEX_SHADER);
		GlShader fragmentShader = new GlShader(GL20.GL_FRAGMENT_SHADER);

		vertexShader.compile(shaderDefines, this.vertexShaderPath);
		fragmentShader.compile(shaderDefines, this.fragmentShaderPath);

		this.attachShader(vertexShader);
		this.attachShader(fragmentShader);

		this.linkProgram();

		String error = GL20.glGetProgramInfoLog(this.id, 200);

		if (!error.isEmpty()) {
			System.out.println("Program logged info " + error);
		}

		vertexShader.delete();
		fragmentShader.delete();
	}

	public void addShaderPath(String vertexShader, String fragmentShader) {
		this.vertexShaderPath = vertexShader;
		this.fragmentShaderPath = fragmentShader;
	}

	public abstract void processUniformLocations();

	public int getHandle() {
		return this.id;
	}

	public void delete() {
		GL20.glDeleteProgram(this.id);
		this.id = -1;
	}

	public void attachShader(GlShader shader) {
		GL20.glAttachShader(this.id, shader.getHandle());
	}

	public void linkProgram() {
		GL20.glLinkProgram(this.id);
	}

	public void useProgram() {
		GL20.glUseProgram(this.id);
	}

	public void disableProgram() {
		GL20.glUseProgram(0);
	}
}
