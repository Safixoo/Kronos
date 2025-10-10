package dev.safixo.client.render.shader;

import dev.safixo.client.render.util.math.Matrix4f;
import dev.safixo.client.render.util.memory.NativeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.GLAllocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import dev.safixo.KronosMod;
import dev.safixo.client.render.cull.FrustumCuller;
import dev.safixo.client.render.util.MathExt;
import dev.safixo.client.render.util.data.CameraData;
import dev.safixo.client.render.util.data.FogData;
import dev.safixo.client.render.vertex.writers.TerrainFormat;

import java.nio.Buffer;
import java.nio.FloatBuffer;

public class ShaderSectionTerrain {
	private int programId;
	private int u_RegionPos;
	private int u_TexId, u_LightTex;
	private int u_ProjMat, u_ModelViewMat;
	private int u_FragCoordToViewCoord;
	private int u_FogEnd, u_FogStart, u_FogColor;

	public static final FloatBuffer TEMP_BUFFER = GLAllocation.createDirectFloatBuffer(16);

	public ShaderSectionTerrain() {
		this.prepareAndCompileShader();
	}

	public void prepareAndCompileShader() {
		if (Keyboard.getEventKey() == Keyboard.KEY_ADD) {
			GL20.glDeleteProgram(this.programId);
		}

		Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage("Terrain shaders reloaded!");

		this.programId = GL20.glCreateProgram();
		int vertexShaderId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
		int fragmentShaderId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);

		ShaderLoader.compileShader("terrain/terrain_fragment.glsl", fragmentShaderId);
		ShaderLoader.compileShader("terrain/terrain_vertex.glsl", vertexShaderId);

		GL20.glAttachShader(this.programId, vertexShaderId);
		GL20.glAttachShader(this.programId, fragmentShaderId);

		GL20.glLinkProgram(this.programId);

		String error = GL20.glGetProgramInfoLog(this.programId, 200);

		if (!error.isEmpty()) {
			KronosMod.LOGGER.fine("Program logged info " + error);
		}

		GL20.glDeleteShader(vertexShaderId);
		GL20.glDeleteShader(fragmentShaderId);

		this.glGetUniformLocation();
	}

	public void glGetUniformLocation() {
		this.u_RegionPos = GL20.glGetUniformLocation(this.programId, "u_RegionPos");
		this.u_TexId = GL20.glGetUniformLocation(this.programId, "u_TexId");
		this.u_LightTex = GL20.glGetUniformLocation(this.programId, "u_LightTex");

		this.u_FogEnd = GL20.glGetUniformLocation(this.programId, "u_FogEnd");
		this.u_FogStart = GL20.glGetUniformLocation(this.programId, "u_FogStart");
		this.u_FogColor = GL20.glGetUniformLocation(this.programId, "u_FogColor");

		this.u_ProjMat = GL20.glGetUniformLocation(this.programId, "u_ProjMat");
		this.u_ModelViewMat = GL20.glGetUniformLocation(this.programId, "u_ModelViewMat");

		this.u_FragCoordToViewCoord = GL20.glGetUniformLocation(this.programId, "u_FragCoordToViewCoord");
	}

	public void unbindProgram() {
		GL20.glUseProgram(0);
	}

	public void bindProgram() {
		GL20.glUseProgram(this.programId);
	}

	public void setupUniforms(boolean noFog) {
		((Buffer) TEMP_BUFFER).clear();
		GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, TEMP_BUFFER);
		((Buffer) TEMP_BUFFER).flip().limit(16);
		GL20.glUniformMatrix4(this.u_ProjMat, false, TEMP_BUFFER);

		((Buffer) TEMP_BUFFER).clear();
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, TEMP_BUFFER);
		((Buffer) TEMP_BUFFER).flip().limit(16);
		GL20.glUniformMatrix4(this.u_ModelViewMat, false, TEMP_BUFFER);

		GL20.glUniform1i(this.u_TexId, 0);
		GL20.glUniform1i(this.u_LightTex, 1);

		GL20.glUniform1f(this.u_FogEnd, noFog ? 1E+12F : MathExt.square(FogData.FOG_END));
		GL20.glUniform1f(this.u_FogStart, noFog ? 1E+12F : MathExt.square(FogData.FOG_START));

		float[] fogColor = FogData.FOG_COLOR;

		GL20.glUniform4f(this.u_FogColor, fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
	}

	public void setupRegionOffset(CameraData camera, int regionX, int regionY, int regionZ) {
		// First the integer subtraction to avoid float precision loss.
		float offsetX = (regionX - camera.intX) - camera.fractX;
		float offsetY = (regionY - camera.intY) - camera.fractY;
		float offsetZ = (regionZ - camera.intZ) - camera.fractZ;

		float radius = (float) TerrainFormat.RADIUS;

		GL20.glUniform3f(this.u_RegionPos, offsetX - radius, offsetY - radius, offsetZ - radius);
	}
}
