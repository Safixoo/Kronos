package dev.safixo.core.hooks;

import dev.safixo.client.render.pipelines.cloud.CloudRenderer;
import dev.safixo.client.render.pipelines.terrain.SectionManager;
import dev.safixo.client.render.vertex.VertexWriterManager;
import dev.safixo.core.HookUtils;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

import java.util.List;

// The way it works is kind of hacky, it tries to work around the two pass
// EntityRenderer.renderWorld system by using some trivial global state, it sucks
// but it is worth it.
public class RenderGlobalHook {
	public static SectionManager MANAGER;
	public static boolean SHOULD_RELOAD;

	public static boolean OPTIFINE_CHECKED = false;
	public static boolean OPTIFINE_ACTIVE = false;

	static float PARTIAL_TICK;
	static boolean FIRST_PASS;

	public static boolean PROCESS_RENDER_INFO = true;

	public static void loadRenderers(RenderGlobal renderGlobal) {
		if (!OPTIFINE_CHECKED) {
			checkOptifineExistence();
			OPTIFINE_CHECKED = true;
		}

		if (MANAGER != null) {
			SectionManager.destroyInstance();
			clearBuffers();
		}

		Block.leaves.setGraphicsLevel(Minecraft.getMinecraft().gameSettings.fancyGraphics);
		int renderDistance = Minecraft.getMinecraft().gameSettings.renderDistance;

		MANAGER = SectionManager.getCurrentInstance();
		MANAGER.setWorld(Minecraft.getMinecraft().theWorld);

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

		SectionManager.getCurrentInstance().update(realRenderDistance, cameraX, cameraY, cameraZ, SHOULD_RELOAD, PARTIAL_TICK);

		{
			FIRST_PASS = true;
			SHOULD_RELOAD = false;
			PROCESS_RENDER_INFO = false;
		}

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

		if (!FIRST_PASS) {
			PROCESS_RENDER_INFO = true;
			return;
		}

		PROCESS_RENDER_INFO = false;

		// Enable lightmap.
		minecraft.entityRenderer.enableLightmap(partialTick);

		if (renderPass == 0) {
			// Render solid pass.
			MANAGER.drawRenderPass(0);
			HookUtils.setField(global, "renderersBeingRendered", "field_72746_N", MANAGER.drawnSolidRenderers);
			MANAGER.drawnSolidRenderers = 0;
		} else {
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			FIRST_PASS = false;

			// Render translucent passes.
			if (minecraft.gameSettings.fancyGraphics) {
				// Pre-pass, replace with correct sorting.
				MANAGER.drawRenderPass(1);
			}

			GL11.glColorMask(true, true, true, true);
			MANAGER.drawRenderPass(1);
			GL11.glEnable(GL11.GL_ALPHA_TEST);
		}

		// Disable lightmap.
		minecraft.entityRenderer.disableLightmap(partialTick);
	}

	public static void markBlockForUpdate(RenderGlobal renderGlobal, int minX, int minY, int minZ) {
		SectionManager.getCurrentInstance().blockUpdate(minX - 1, minY - 1, minZ - 1, minX + 1, minY + 1, minZ + 1);
	}

	public static void markBlockForRenderUpdate(RenderGlobal renderGlobal, int minX, int minY, int minZ) {
		SectionManager.getCurrentInstance().blockUpdate(minX - 1, minY - 1, minZ - 1, minX + 1, minY + 1, minZ + 1);
	}

	public static void markBlockRangeForRenderUpdate(RenderGlobal renderGlobal, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		SectionManager.getCurrentInstance().blockUpdate(minX - 1, minY - 1, minZ - 1,  maxX + 1, maxY + 1, maxZ + 1);
	}

	public static void markBlocksForUpdate(RenderGlobal renderGlobal, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		SectionManager.getCurrentInstance().blockUpdate(minX - 1, minY - 1, minZ - 1,  maxX + 1, maxY + 1, maxZ + 1);
	}

	public static void renderAllSortedRenderers(RenderGlobal renderGlobal, int pass, double tick) {
		// NO-OP OptiFine method.
	}
}
