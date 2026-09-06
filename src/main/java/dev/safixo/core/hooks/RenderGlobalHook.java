package dev.safixo.core.hooks;

import dev.safixo.client.render.gfx.util.GpuFlags;
import dev.safixo.client.render.pipelines.clouds.CloudRenderer;
import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.render.pipelines.terrain.region.RegionConstants;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.PrimitivesFlags;
import dev.safixo.core.HookUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.Map;

// TODO: Replace the current system now by a simpler one, now that the two pass has been patched.
@SuppressWarnings("unused")
public  class RenderGlobalHook {
	public static WorldManager MANAGER;

	public static boolean SHOULD_RELOAD;
	public static boolean OPTIFINE_CHECKED = false;
	public static boolean OPTIFINE_ACTIVE = false;

	public static float PARTIAL_TICK;

	public static void loadRenderers(RenderGlobal renderGlobal) {
		// Check capabilities (TODO: This check is probably implemented too late)
		GpuFlags.checkModSupport();

		PrimitivesFlags.processLeavesSolid();

		if (!OPTIFINE_CHECKED) {
			checkOptifineExistence();
			OPTIFINE_CHECKED = true;
		}

		if (MANAGER != null) {
			WorldManager.freeInstance();
		}

		MANAGER = WorldManager.getCurrentInstance();
		GameSettings gameSettings = Minecraft.getMinecraft().gameSettings;

		Block.leaves.setGraphicsLevel(gameSettings.fancyGraphics);
		int renderDistance = gameSettings.renderDistance;

		renderGlobal.tileEntities.clear();
		renderGlobal.renderEntitiesStartupCounter = 2;
		renderGlobal.renderDistance = renderDistance;

		VertexWriter.startDefaults();
		SHOULD_RELOAD = true;

//		if (Launch.classLoader != null) {
//			try {
//				HookUtils.getField(Launch.classLoader, "resourceCache", "resourceCache")
//					.set(Launch.classLoader, new Object2ObjectOpenHashMap<>());
//				HookUtils.getField(Launch.classLoader, "negativeResourceCache", "negativeResourceCache")
//					.set(Launch.classLoader, new ObjectOpenHashSet<>());
//				HookUtils.getField(Launch.classLoader, "cachedClasses", "cachedClasses")
//					.set(Launch.classLoader, new Object2ObjectOpenHashMap<>());
//				HookUtils.getField(Launch.classLoader, "packageManifests", "packageManifests")
//					.set(Launch.classLoader, new Object2ObjectOpenHashMap<>());
//				System.out.println("Clearing cache!");
//			} catch (Exception e) {
//				throw new RuntimeException(e);
//			}
//		}
	}

	private static void checkOptifineExistence() {
		OPTIFINE_ACTIVE = HookUtils.existsField(Minecraft.getMinecraft().gameSettings, "ofRenderDistanceFine");
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

	public static void renderCloudsFancy(RenderGlobal renderGlobal, float partialTick) {
		CloudRenderer.renderCloudsFancy(partialTick);
	}

	public static void sortAndRender(RenderGlobal global, EntityLivingBase player, int renderPass, double partialTick) {
		Minecraft minecraft = Minecraft.getMinecraft();

		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_NEAREST);

		// Enable lightmap.
		minecraft.entityRenderer.enableLightmap(partialTick);
		GL11.glEnable(GL11.GL_CULL_FACE);

		if (!OPTIFINE_ACTIVE) {
			if (renderPass == 0) {
				// Render solid pass.
				MANAGER.drawRenderPass(RegionConstants.SOLID_PASS);
				global.renderersBeingRendered = MANAGER.drawnSolidRenderers;
				MANAGER.drawnSolidRenderers = 0;
			} else {
				// Render translucent pass.
				GL11.glDisable(GL11.GL_ALPHA_TEST);
				GL11.glColorMask(true, true, true, true);
				MANAGER.drawRenderPass(RegionConstants.TRANSLUCENT_PASS);
				GL11.glEnable(GL11.GL_ALPHA_TEST);
			}
		} else {
			// Render solid pass.
			MANAGER.drawRenderPass(RegionConstants.SOLID_PASS);
			global.renderersBeingRendered = MANAGER.drawnSolidRenderers;
			MANAGER.drawnSolidRenderers = 0;

			// Render translucent pass.
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glDisable(GL11.GL_ALPHA_TEST);

			GL11.glColorMask(true, true, true, true);
			MANAGER.drawRenderPass(RegionConstants.TRANSLUCENT_PASS);

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
