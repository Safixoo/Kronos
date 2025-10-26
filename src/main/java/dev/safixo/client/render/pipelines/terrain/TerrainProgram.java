package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.render.gfx.shader.GlProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import org.lwjgl.opengl.GL20;
import dev.safixo.client.render.pipelines.terrain.cull.FrustumCuller;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.data.FogData;
import dev.safixo.client.render.vertex.writers.TerrainFormat;

public class TerrainProgram extends GlProgram {
	private int u_RegionPos;
	private int u_TexId, u_LightTex;
	private int u_ProjMat, u_ModelViewMat;
	private int u_FogEnd, u_FogStart, u_FogColor;

	public TerrainProgram() {
		super("terrain/terrain_vertex.glsl", "terrain/terrain_fragment.glsl");

		GuiNewChat chat = Minecraft.getMinecraft().ingameGUI.getChatGUI();
		chat.printChatMessage("Terrain shaders reloaded!");
	}

	@Override
	public void processUniformLocations() {
		this.u_RegionPos = GL20.glGetUniformLocation(this.getHandle(), "u_RegionPos");
		this.u_TexId = GL20.glGetUniformLocation(this.getHandle(), "u_TexId");
		this.u_LightTex = GL20.glGetUniformLocation(this.getHandle(), "u_LightTex");

		this.u_FogEnd = GL20.glGetUniformLocation(this.getHandle(), "u_FogEnd");
		this.u_FogStart = GL20.glGetUniformLocation(this.getHandle(), "u_FogStart");
		this.u_FogColor = GL20.glGetUniformLocation(this.getHandle(), "u_FogColor");

		this.u_ProjMat = GL20.glGetUniformLocation(this.getHandle(), "u_ProjMat");
		this.u_ModelViewMat = GL20.glGetUniformLocation(this.getHandle(), "u_ModelViewMat");
	}

	public void setupUniforms(boolean noFog) {
		GL20.glUniformMatrix4(this.u_ProjMat, false, FrustumCuller.projectionBuff);
		GL20.glUniformMatrix4(this.u_ModelViewMat, false, FrustumCuller.modelViewBuff);

		GL20.glUniform1i(this.u_TexId, 0);
		GL20.glUniform1i(this.u_LightTex, 1);

		GL20.glUniform1f(this.u_FogEnd, noFog ? 1E+12F : FogData.FOG_END);
		GL20.glUniform1f(this.u_FogStart, noFog ? 1E+12F : FogData.FOG_START);

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
