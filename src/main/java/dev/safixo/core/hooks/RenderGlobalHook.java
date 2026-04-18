package dev.safixo.core.hooks;

import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.render.gfx.util.GpuFlags;
import dev.safixo.client.render.pipelines.cloud.CloudRenderer;
import dev.safixo.client.render.pipelines.entity_model.AdvModelRenderer;
import dev.safixo.client.render.pipelines.terrain.SectionManager;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.FastLongHashMap;
import dev.safixo.client.util.MathExt;
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

import java.lang.reflect.Field;
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

	private static final Field RENDERERS_BEING_LOADED = HookUtils.getField(RenderGlobal.class, "renderersBeingRendered", "field_72746_N");

	static float PARTIAL_TICK;

	public static void loadRenderers(RenderGlobal renderGlobal) {
		// Check capabilities (TODO: This check is probably implemented too late)
		GpuFlags.checkModSupport();

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

		VertexWriter.startDefaults();

		HookUtils.setField(renderGlobal, "renderEntitiesStartupCounter", "field_72740_G", 2);
		HookUtils.setField(renderGlobal, "renderDistance", "field_72739_F", renderDistance);

		SHOULD_RELOAD = true;
	}

	private static void checkOptifineExistence() {
		OPTIFINE_ACTIVE = HookUtils.existsField(Minecraft.getMinecraft().gameSettings, "ofRenderDistanceFine");
	}

	private static void clearBuffers() {
		VertexWriter.clearBuffers();
		AdvModelRenderer.cleanupEntityModelPool();
	}

	// Executed only in the first pass of renderWorld.
	public static boolean updateRenderers(RenderGlobal renderGlobal, EntityLivingBase player, boolean idk) {
		double cameraX = MathExt.lerp(player.lastTickPosX, player.posX, PARTIAL_TICK);
		double cameraY = MathExt.lerp(player.lastTickPosY, player.posY, PARTIAL_TICK);
		double cameraZ = MathExt.lerp(player.lastTickPosZ, player.posZ, PARTIAL_TICK);

		MinecraftHook.MAIN_THREAD = Thread.currentThread();

		Minecraft minecraft = Minecraft.getMinecraft();
		int realRenderDistance = MathExt.getCanonicalRenderDistance(minecraft.gameSettings);

		if (PrimitivesFlags.DEV_ENVIRONMENT) {
			minecraft.thePlayer.capabilities.setFlySpeed(0.25f);
		}

		MANAGER.update(minecraft.theWorld, realRenderDistance, cameraX, cameraY, cameraZ, SHOULD_RELOAD, PARTIAL_TICK);
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
				HookUtils.setFieldValue(RENDERERS_BEING_LOADED, global, MANAGER.drawnSolidRenderers);
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
			HookUtils.setFieldValue(RENDERERS_BEING_LOADED, global, MANAGER.drawnSolidRenderers);
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
