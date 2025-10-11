package dev.safixo.core.hooks;

import dev.safixo.client.render.SectionManager;
import dev.safixo.client.render.vertex.VertexWriterManager;
import dev.safixo.core.HookUtils;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class RenderGlobalHook {
	public static SectionManager MANAGER;
	public static boolean SHOULD_RELOAD;

	public static boolean OPTIFINE_CHECKED = false;
	public static boolean OPTIFINE_ACTIVE = false;

	public static void loadRenderers(RenderGlobal renderGlobal) {
		if (!OPTIFINE_CHECKED) {
			checkOptifineExistence();
			OPTIFINE_CHECKED = true;
		}

		Block.leaves.setGraphicsLevel(Minecraft.getMinecraft().gameSettings.fancyGraphics);
		int renderDistance = Minecraft.getMinecraft().gameSettings.renderDistance;

		MANAGER = SectionManager.getCurrentInstance();
		MANAGER.setWorld(Minecraft.getMinecraft().theWorld);
		SHOULD_RELOAD = true;

		((List<?>) HookUtils.getFieldObj(renderGlobal, "tileEntities", "field_72762_a")).clear();

		VertexWriterManager.startDefaults();

		HookUtils.setField(renderGlobal, "renderEntitiesStartupCounter", "field_72740_G", 2);
		HookUtils.setField(renderGlobal, "renderDistance", "field_72739_F", renderDistance);
	}

	private static void checkOptifineExistence() {
		try {
			HookUtils.getField(Minecraft.getMinecraft().gameSettings, "ofRenderDistanceFine", "ofRenderDistanceFine");
			OPTIFINE_ACTIVE = true;
		} catch (Exception e) {
			OPTIFINE_ACTIVE = false;
		}
	}

	private void clearBuffers() {
		VertexWriterManager.clearBuffers();
	}

	public static void clipRenderersByFrustum(RenderGlobal renderGlobal, ICamera frustum, float partialTick) {
		double cameraX = (Double) HookUtils.getFieldObj(frustum, "xPosition", "field_78550_b");
		double cameraY = (Double) HookUtils.getFieldObj(frustum, "yPosition", "field_78551_c");
		double cameraZ = (Double) HookUtils.getFieldObj(frustum, "zPosition", "field_78549_d");

		Minecraft minecraft = Minecraft.getMinecraft();

		int realRenderDistance;

		if (!OPTIFINE_ACTIVE) {
			// This is more or less the real metric for chunk distance that the game uses.
			// 0 - Far, 1 - Normal, 2 - Short, 3 - Tiny.
			// In the future would be productive replace add a bigger slider for render distance,
			// like optifine.
			realRenderDistance = Math.min(400, ((64 << (3 - minecraft.gameSettings.renderDistance)) >> 5)) + 2;
		} else {
			realRenderDistance = (Integer) HookUtils.getFieldObj(minecraft.gameSettings, "ofRenderDistanceFine", "ofRenderDistanceFine") >> 4;
		}

		MANAGER.update(realRenderDistance, cameraX, cameraY, cameraZ, SHOULD_RELOAD, partialTick);
		SHOULD_RELOAD = false;
	}

	public static void sortAndRender(RenderGlobal global, EntityLivingBase player, int renderPass, double partialTick) {
		Minecraft minecraft = Minecraft.getMinecraft();

		if (OPTIFINE_ACTIVE) {
			if (renderPass == 1) {
				return;
			}
		}

		minecraft.entityRenderer.enableLightmap(partialTick);

		MANAGER.drawRenderPass(renderPass);

		if (renderPass == 0) {
			HookUtils.setField(global, "renderersBeingRendered", "field_72746_N", MANAGER.drawnSolidRenderers);
			MANAGER.drawnSolidRenderers = 0;
		}

		if (OPTIFINE_ACTIVE) {

			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glDepthMask(true);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glDisable(GL11.GL_CULL_FACE);

			MANAGER.drawRenderPass(1);

			GL11.glDisable(GL11.GL_BLEND);
		}

		minecraft.entityRenderer.disableLightmap(partialTick);
	}

	public static void markBlockForUpdate(RenderGlobal renderGlobal, int minX, int minY, int minZ) {
		MANAGER.blockUpdate(minX - 1, minY - 1, minZ - 1, minX + 1, minY + 1, minZ + 1);
	}

	public static void markBlockForRenderUpdate(RenderGlobal renderGlobal, int minX, int minY, int minZ) {
		MANAGER.blockUpdate(minX - 1, minY - 1, minZ - 1, minX + 1, minY + 1, minZ + 1);
	}

	public static void markBlockRangeForRenderUpdate(RenderGlobal renderGlobal, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		MANAGER.blockUpdate(minX - 1, minY - 1, minZ - 1,  maxX + 1, maxY + 1, maxZ + 1);
	}

	public static void markBlocksForUpdate(RenderGlobal renderGlobal, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		MANAGER.blockUpdate(minX - 1, minY - 1, minZ - 1,  maxX + 1, maxY + 1, maxZ + 1);
	}

	public static void renderAllSortedRenderers(RenderGlobal renderGlobal, int pass, double tick) {
		// NO-OP OptiFine method.
	}
}
