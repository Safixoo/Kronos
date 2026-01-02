package dev.safixo.core.hooks;

import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.render.pipelines.cloud.CloudRenderer;
import dev.safixo.client.render.pipelines.terrain.SectionManager;
import dev.safixo.client.render.vertex.VertexWriterManager;
import dev.safixo.client.util.data.PrimitivesFlags;
import dev.safixo.core.HookUtils;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

import java.util.List;

// The way it works is kind of hacky, it tries to work around the two pass
// EntityRenderer.renderWorld system by using some trivial global state, it sucks,
// but it is worth it.

// TODO: Replace the current system now by a simpler one, now that the two pass has been patched.
@SuppressWarnings("unused")
public  class RenderGlobalHook {
	public static SectionManager MANAGER;

	public static boolean SHOULD_RELOAD;
	public static boolean OPTIFINE_CHECKED = false;
	public static boolean OPTIFINE_ACTIVE = false;

	static float PARTIAL_TICK;

	public static void loadRenderers(RenderGlobal renderGlobal) {
		if (!OPTIFINE_CHECKED) {
			checkOptifineExistence();
			OPTIFINE_CHECKED = true;
		}

		if (MANAGER != null) {
			SectionManager.destroyInstance();
			clearBuffers();
		} else {
			Tessellator.instance = ImprovedTessellator.TESSELLATOR;
		}

		MANAGER = SectionManager.getCurrentInstance();
		GameSettings gameSettings = Minecraft.getMinecraft().gameSettings;

		Block.leaves.setGraphicsLevel(gameSettings.fancyGraphics);
		int renderDistance = gameSettings.renderDistance;

		((List<?>) HookUtils.getFieldObj(renderGlobal, "tileEntities", "field_72762_a")).clear();

		VertexWriterManager.startDefaults();

		HookUtils.setField(renderGlobal, "renderEntitiesStartupCounter", "field_72740_G", 2);
		HookUtils.setField(renderGlobal, "renderDistance", "field_72739_F", renderDistance);

		SHOULD_RELOAD = true;
	}

	private static void checkOptifineExistence() {
		OPTIFINE_ACTIVE = HookUtils.existsField(Minecraft.getMinecraft().gameSettings, "ofRenderDistanceFine");
	}

	private static void clearBuffers() {
		VertexWriterManager.clearBuffers();
	}

	// Executed only in the first pass of renderWorld.
	public static boolean updateRenderers(RenderGlobal renderGlobal, EntityLivingBase player, boolean idk) {
		double cameraX = player.lastTickPosX + (player.posX - player.lastTickPosX) * PARTIAL_TICK;
		double cameraY = player.lastTickPosY + (player.posY - player.lastTickPosY) * PARTIAL_TICK;
		double cameraZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * PARTIAL_TICK;

		Minecraft minecraft = Minecraft.getMinecraft();

		int realRenderDistance;

		if (!OPTIFINE_ACTIVE) {
			// This is more or less the real metric for chunk distance that the game uses.
			// 0 - Far, 1 - Normal, 2 - Short, 3 - Tiny.
			// In the future would be productive replace add a bigger slider for render distance,
			// like optifine.
			realRenderDistance = ((64 << (3 - minecraft.gameSettings.renderDistance)) >> 5) + 2;
		} else {
			realRenderDistance = (Integer) HookUtils.getFieldObj(minecraft.gameSettings, "ofRenderDistanceFine", "ofRenderDistanceFine") >> 4;
		}

		if (PrimitivesFlags.DEV_ENVIRONMENT) {
			Minecraft.getMinecraft().thePlayer.capabilities.setFlySpeed(0.25f);
		}

		MANAGER.update(Minecraft.getMinecraft().theWorld, realRenderDistance, cameraX, cameraY, cameraZ, SHOULD_RELOAD, PARTIAL_TICK);
		SHOULD_RELOAD = false;

		return true;
	}

	public static void clipRenderersByFrustum(RenderGlobal renderGlobal, ICamera frustum, float partialTick) {
		PARTIAL_TICK = partialTick;
	}

	static long samples = 0;
	static long time;

	public static void renderCloudsFancy(RenderGlobal renderGlobal, float partialTick) {
		CloudRenderer.renderCloudsFancy(partialTick);
	}

	public static void sortAndRender(RenderGlobal global, EntityLivingBase player, int renderPass, double partialTick) {
		Minecraft minecraft = Minecraft.getMinecraft();

		// Enable lightmap.
		minecraft.entityRenderer.enableLightmap(partialTick);
		GL11.glEnable(GL11.GL_CULL_FACE);

		if (!OPTIFINE_ACTIVE) {
			if (renderPass == 0) {
				// Render solid pass.
				MANAGER.drawRenderPass(0);
				HookUtils.setField(global, "renderersBeingRendered", "field_72746_N", MANAGER.drawnSolidRenderers);
				MANAGER.drawnSolidRenderers = 0;
			} else {
				// Render translucent pass.
				GL11.glDisable(GL11.GL_ALPHA_TEST);
				GL11.glColorMask(true, true, true, true);
				MANAGER.drawRenderPass(1);
				GL11.glEnable(GL11.GL_ALPHA_TEST);
			}
		} else {
			// Render solid pass.
			MANAGER.drawRenderPass(0);
			HookUtils.setField(global, "renderersBeingRendered", "field_72746_N", MANAGER.drawnSolidRenderers);
			MANAGER.drawnSolidRenderers = 0;

			// Render translucent pass.
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glDisable(GL11.GL_ALPHA_TEST);

			GL11.glColorMask(true, true, true, true);
			MANAGER.drawRenderPass(1);

			GL11.glEnable(GL11.GL_ALPHA_TEST);
			GL11.glDisable(GL11.GL_BLEND);
		}

		// Disable lightmap.
		minecraft.entityRenderer.disableLightmap(partialTick);
		GL11.glDisable(GL11.GL_CULL_FACE);
	}

	public static void renderAllSortedRenderers(RenderGlobal renderGlobal, int pass, double tick) {
		// NO-OP OptiFine method.
	}
}
