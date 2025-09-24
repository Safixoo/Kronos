package turniplabs.examplemod.client.render;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Unique;
import turniplabs.examplemod.ExampleMod;
import turniplabs.examplemod.client.render.data.CameraData;
import turniplabs.examplemod.client.render.data.FogData;
import turniplabs.examplemod.client.render.shader.ShaderLoader;

public class ShaderSectionTerrain {
	private boolean shaderCreated;
	private int programId;
	private int u_RegionPos;
	private int u_TexId;
	private int u_FogEnd, u_FogStart, u_FogColor;

	public ShaderSectionTerrain() {
		this.prepareAndCompileShader();
	}

	public void prepareAndCompileShader() {
		if (Keyboard.getEventKey() == Keyboard.KEY_ADD) {
			GL20.glDeleteProgram(this.programId);
		}

		Minecraft.getMinecraft().hudIngame.addChatMessage("Terrain shaders reloaded!");

		this.programId = GL20.glCreateProgram();
		int vertexShaderId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
		int fragmentShaderId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);

		ShaderLoader.compileShader("terrain/terrain_fragment.glsl", fragmentShaderId);
		ShaderLoader.compileShader("terrain/terrain_vertex.glsl", vertexShaderId);

		GL20.glAttachShader(this.programId, vertexShaderId);
		GL20.glAttachShader(this.programId, fragmentShaderId);

		GL20.glLinkProgram(this.programId);

		String error = GL20.glGetProgramInfoLog(this.programId);

		if (!error.isEmpty()) {
			ExampleMod.LOGGER.error("Program logged info {}", error);
		}

		GL20.glDeleteShader(vertexShaderId);
		GL20.glDeleteShader(fragmentShaderId);

		this.glGetUniformLocation();

		this.shaderCreated = true;
	}

	public void glGetUniformLocation() {
		this.u_RegionPos = GL20.glGetUniformLocation(this.programId, "u_RegionPos");
		this.u_TexId = GL20.glGetUniformLocation(this.programId, "u_TexId");

		this.u_FogEnd = GL20.glGetUniformLocation(this.programId, "u_FogEnd");
		this.u_FogStart = GL20.glGetUniformLocation(this.programId, "u_FogStart");
		this.u_FogColor = GL20.glGetUniformLocation(this.programId, "u_FogColor");
	}

	public void unbindProgram() {
		GL20.glUseProgram(0);
	}

	public void bindProgram() {
		GL20.glUseProgram(this.programId);
	}

	public void setupUniforms(boolean noFog) {
		GL20.glUniform1i(this.u_TexId, 0);

		GL20.glUniform1f(this.u_FogEnd, noFog ? 1E+12F : FogData.fogEnd);
		GL20.glUniform1f(this.u_FogStart, noFog ? 1E+12F : FogData.fogStart);

		float[] fogColor = FogData.fogColor;
		GL20.glUniform3f(this.u_FogColor, fogColor[0], fogColor[1], fogColor[2]);
	}

	public void setupRegionOffset(CameraData camera, int regionX, int regionY, int regionZ) {
		// First the integer substraction to avoid float precision loss.
		float offsetX = (regionX - camera.intX) - camera.fractX;
		float offsetY = (regionY - camera.intY) - camera.fractY;
		float offsetZ = (regionZ - camera.intZ) - camera.fractZ;

		GL20.glUniform3f(this.u_RegionPos, offsetX, offsetY, offsetZ);
	}
}
