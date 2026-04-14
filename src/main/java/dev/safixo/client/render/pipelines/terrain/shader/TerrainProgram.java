package dev.safixo.client.render.pipelines.terrain.shader;

import dev.safixo.client.render.gfx.shader.GlProgram;
import dev.safixo.client.util.data.PrimitivesFlags;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.core.hooks.GlStateTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import dev.safixo.client.render.pipelines.terrain.cull.FrustumCuller;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.render.vertex.writers.TerrainFormat;

import java.nio.FloatBuffer;

public class TerrainProgram extends GlProgram {
	private int u_RegionPos;
	private int u_TexId, u_LightTex;
	private int u_ProjModelViewMat, u_FogMat;
	public int u_FogColor;

	public TerrainProgram() {
		super("terrain/terrain_vertex.glsl", "terrain/terrain_fragment.glsl");

		if (PrimitivesFlags.DEV_ENVIRONMENT) {
			GuiNewChat chat = Minecraft.getMinecraft().ingameGUI.getChatGUI();
			chat.printChatMessage("Terrain shaders reloaded!");
		}
	}

	@Override
	public void processUniformLocations() {
		this.u_RegionPos = GL20.glGetUniformLocation(this.getHandle(), "u_RegionPos");
		this.u_TexId = GL20.glGetUniformLocation(this.getHandle(), "u_TexId");
		this.u_LightTex = GL20.glGetUniformLocation(this.getHandle(), "u_LightTex");
		this.u_FogColor = GL20.glGetUniformLocation(this.getHandle(), "u_FogColor");

		this.u_ProjModelViewMat = GL20.glGetUniformLocation(this.getHandle(), "u_ProjModelViewMat");
		this.u_FogMat = GL20.glGetUniformLocation(this.getHandle(), "u_FogMat");
	}

	private static final FloatBuffer MATRIX = NativeBuffer.memAllocFloat(16);

	public void setupUniforms(boolean noFog) {
		Matrix4f projMvp = FrustumCuller.projectionMatrix.mul(FrustumCuller.modelViewMatrix, new Matrix4f());

		float width = Minecraft.getMinecraft().displayWidth;
		float height = Minecraft.getMinecraft().displayHeight;

		final Matrix4f fragToNDC = new Matrix4f()
			.translation(-1, -1, -1)
			.scale(2.0f / width, 2.0f / height, 2.0f);

		Matrix4f fogMat = projMvp.invert(new Matrix4f()).mul(fragToNDC);

		GL20.glUniformMatrix4(this.u_ProjModelViewMat, false, projMvp.scale((float) (1.0 / TerrainFormat.SCALE)).get(MATRIX));
		GL20.glUniformMatrix4(this.u_FogMat, false, fogMat.get(MATRIX));

		GL20.glUniform1i(this.u_TexId, 0);
		GL20.glUniform1i(this.u_LightTex, 1);
		GL20.glUniform3f(this.u_FogColor, GlStateTracker.FOG_COLOR_R, GlStateTracker.FOG_COLOR_G, GlStateTracker.FOG_COLOR_B);
	}

	public void setupRegionOffset(CameraData camera, int regionX, int regionY, int regionZ) {
		// First the integer subtraction to avoid float precision loss.
		float offsetX = (regionX - camera.intX) - camera.fractX;
		float offsetY = (regionY - camera.intY) - camera.fractY;
		float offsetZ = (regionZ - camera.intZ) - camera.fractZ;

		float radius = TerrainFormat.RADIUS;
		float scale = TerrainFormat.SCALE;

		GL20.glUniform3f(this.u_RegionPos, (offsetX - radius) * scale, (offsetY - radius) * scale, (offsetZ - radius) * scale);
	}
}
