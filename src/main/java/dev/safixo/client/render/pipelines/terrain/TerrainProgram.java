package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.render.gfx.shader.GlProgram;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.core.hooks.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import org.lwjgl.Sys;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import dev.safixo.client.render.pipelines.terrain.cull.FrustumCuller;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.data.FogData;
import dev.safixo.client.render.vertex.writers.TerrainFormat;

import java.nio.Buffer;
import java.nio.FloatBuffer;

public class TerrainProgram extends GlProgram {
	private int u_RegionPos;
	private int u_TexId, u_LightTex;
	private int u_ProjMat, u_ModelViewMat;
	private int u_FogNegInvRadius, u_FogEndInvRad, u_FogColor;

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

		this.u_FogEndInvRad = GL20.glGetUniformLocation(this.getHandle(), "u_FogEndInvRad");
		this.u_FogNegInvRadius = GL20.glGetUniformLocation(this.getHandle(), "u_FogNegInvRadius");
		this.u_FogColor = GL20.glGetUniformLocation(this.getHandle(), "u_FogColor");

		this.u_ProjMat = GL20.glGetUniformLocation(this.getHandle(), "u_ProjMat");
		this.u_ModelViewMat = GL20.glGetUniformLocation(this.getHandle(), "u_ModelViewMat");
	}

	public void setupUniforms(boolean noFog) {
		GL20.glUniformMatrix4(this.u_ProjMat, false, FrustumCuller.projectionBuff);
		GL20.glUniformMatrix4(this.u_ModelViewMat, false, FrustumCuller.modelViewBuff);

		GL20.glUniform1i(this.u_TexId, 0);
		GL20.glUniform1i(this.u_LightTex, 1);

		// (u_FogEnd - v_Distance) / (u_FogEnd - u_FogStart)
		// (u_FogNegInvRadius * v_Distance) + u_FogEndNegInvRadius;

		float start = GlStateManager.FOG_START;
		float end = GlStateManager.FOG_END;

		float radius = end - start;
		float fogNegInvRadius = 1.0F / radius;
		float fogEndInvRad = end * fogNegInvRadius;

		GL20.glUniform1f(this.u_FogEndInvRad, noFog ? 1E+12F : fogEndInvRad);
		GL20.glUniform1f(this.u_FogNegInvRadius, noFog ? 1E+12F : -fogNegInvRadius);

		GL20.glUniform3f(this.u_FogColor, GlStateManager.FOG_COLOR_R, GlStateManager.FOG_COLOR_G, GlStateManager.FOG_COLOR_B);
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
