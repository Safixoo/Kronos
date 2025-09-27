package turniplabs.examplemod.client.render.shader;

import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.ExampleMod;
import turniplabs.examplemod.client.render.cull.FrustumCuller;
import turniplabs.examplemod.client.render.util.MathExt;
import turniplabs.examplemod.client.render.util.data.CameraData;
import turniplabs.examplemod.client.render.util.data.FogData;
import turniplabs.examplemod.client.render.vertex.writers.TerrainFormat;

import java.nio.FloatBuffer;

public class ShaderSectionTerrain {
	private int programId;
	private int u_RegionPos;
	private int u_TexId;
	private int u_ProjModelViewMat;
	private int u_FragCoordToViewCoord;
	private int u_FogEnd, u_FogStart, u_FogColor;

	public static final FloatBuffer TEMP_BUFFER = MemoryUtil.memAllocFloat(16);

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
	}

	public void glGetUniformLocation() {
		this.u_RegionPos = GL20.glGetUniformLocation(this.programId, "u_RegionPos");
		this.u_TexId = GL20.glGetUniformLocation(this.programId, "u_TexId");

		this.u_FogEnd = GL20.glGetUniformLocation(this.programId, "u_FogEnd");
		this.u_FogStart = GL20.glGetUniformLocation(this.programId, "u_FogStart");
		this.u_FogColor = GL20.glGetUniformLocation(this.programId, "u_FogColor");

		this.u_ProjModelViewMat = GL20.glGetUniformLocation(this.programId, "u_ProjModelViewMat");
		this.u_FragCoordToViewCoord = GL20.glGetUniformLocation(this.programId, "u_FragCoordToViewCoord");
	}

	public void unbindProgram() {
		GL20.glUseProgram(0);
	}

	public void bindProgram() {
		GL20.glUseProgram(this.programId);
	}

	public void setupUniforms(boolean noFog) {
		Matrix4f modelViewMat = FrustumCuller.modelViewMatrix;
		Matrix4f projectionMat = FrustumCuller.projectionMatrix;

		Matrix4f combinedInv = new Matrix4f();
		projectionMat.mul(modelViewMat, combinedInv);

		float width = Minecraft.getMinecraft().gameWindow.getWidthPixels();
		float height = Minecraft.getMinecraft().gameWindow.getHeightPixels();

		final Matrix4f fragToNDC = new Matrix4f()
			.translation(-1, -1, -1)
			.scale(2.0f / width, 2.0f / height, 2.0f);

		Matrix4f viewCoord = combinedInv.invert(new Matrix4f()).mul(fragToNDC);

		GL20.glUniformMatrix4fv(this.u_FragCoordToViewCoord, false, viewCoord.get(TEMP_BUFFER));
		GL20.glUniformMatrix4fv(this.u_ProjModelViewMat, false, combinedInv.get(TEMP_BUFFER));

		GL20.glUniform1i(this.u_TexId, 0);

		GL20.glUniform1f(this.u_FogEnd, noFog ? 1E+12F : MathExt.square(FogData.FOG_END));
		GL20.glUniform1f(this.u_FogStart, noFog ? 1E+12F : MathExt.square(FogData.FOG_START));

		float[] fogColor = FogData.FOG_COLOR;
		GL20.glUniform3f(this.u_FogColor, fogColor[0], fogColor[1], fogColor[2]);
	}

	public void setupRegionOffset(CameraData camera, int regionX, int regionY, int regionZ) {
		// First the integer substraction to avoid float precision loss.
		float offsetX = (regionX - camera.intX) - camera.fractX;
		float offsetY = (regionY - camera.intY) - camera.fractY;
		float offsetZ = (regionZ - camera.intZ) - camera.fractZ;

		float radius = (float) TerrainFormat.RADIUS;

		GL20.glUniform3f(this.u_RegionPos, offsetX - radius, offsetY - radius, offsetZ - radius);
	}
}
