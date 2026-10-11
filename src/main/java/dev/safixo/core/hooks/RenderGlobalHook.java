package dev.safixo.core.hooks;

import dev.safixo.client.render.gfx.util.GpuFlags;
import dev.safixo.client.render.pipelines.clouds.CloudRenderer;
import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.render.pipelines.terrain.region.RegionConstants;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.PrimitivesFlags;
import dev.safixo.core.HookUtils;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

// TODO: Replace the current system now by a simpler one, now that the two pass has been patched.
@SuppressWarnings("unused")
public class RenderGlobalHook {
	public static WorldManager MANAGER;

	public static boolean SHOULD_RELOAD;
	public static boolean OPTIFINE_CHECKED = false;
	public static boolean OPTIFINE_ACTIVE = false;

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

	public static boolean updateRenderers(RenderGlobal renderGlobal, EntityLivingBase player, boolean idk) {
		return true;
	}

	public static void clipRenderersByFrustum(RenderGlobal renderGlobal, ICamera frustum, float partialTick) {
		Minecraft minecraft = Minecraft.getMinecraft();
		EntityLivingBase player = minecraft.renderViewEntity;

		double cameraX = MathExt.lerp(player.lastTickPosX, player.posX, partialTick);
		double cameraY = MathExt.lerp(player.lastTickPosY, player.posY, partialTick);
		double cameraZ = MathExt.lerp(player.lastTickPosZ, player.posZ, partialTick);

		MinecraftHook.MAIN_THREAD = Thread.currentThread();

		int realRenderDistance = MathExt.getCanonicalRenderDistance(minecraft.gameSettings);

		if (PrimitivesFlags.DEV_ENVIRONMENT) {
			minecraft.thePlayer.capabilities.setFlySpeed(0.25f);
		}

		MANAGER.update(minecraft.theWorld, realRenderDistance, cameraX, cameraY, cameraZ, SHOULD_RELOAD, partialTick);
		SHOULD_RELOAD = false;
	}

	public static void renderCloudsFancy(RenderGlobal renderGlobal, float partialTick) {
		CloudRenderer.renderCloudsFancy(partialTick);
	}

	public static int sortAndRender(RenderGlobal renderGlobal, EntityLivingBase player, int pass, double tick) {
		Minecraft minecraft = Minecraft.getMinecraft();

		RenderHelper.disableStandardItemLighting();

		GL11.glEnable(GL11.GL_CULL_FACE);
		minecraft.entityRenderer.enableLightmap(tick);

		if (pass == RegionConstants.SOLID_PASS) {
			GL11.glDisable(GL11.GL_BLEND);
			MANAGER.drawRenderPass(RegionConstants.SOLID_PASS);
			renderGlobal.renderersBeingRendered = MANAGER.drawnSolidRenderers;
		} else {
			GL11.glColorMask(true, true, true, true);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(770, 771);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			MANAGER.drawRenderPass(RegionConstants.TRANSLUCENT_PASS);
			GL11.glEnable(GL11.GL_ALPHA_TEST);
		}
		MANAGER.drawnSolidRenderers = 0;

		minecraft.entityRenderer.disableLightmap(tick);
		GL11.glDisable(GL11.GL_CULL_FACE);

		return 0;
	}

	public static void renderAllRenderLists(RenderGlobal renderGlobal, int pass, double tick) {

	}

	public static void renderAllSortedRenderers(RenderGlobal renderGlobal, int pass, double tick) {

	}
}
