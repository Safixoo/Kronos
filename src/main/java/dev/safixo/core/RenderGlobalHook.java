package dev.safixo.core;

import dev.safixo.client.render.SectionManager;
import dev.safixo.client.render.vertex.VertexWriterManager;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.Frustrum;

import java.util.List;

public class RenderGlobalHook {
	static SectionManager MANAGER;
	static boolean SHOULD_RELOAD;

	public static void loadRenderers() {
		RenderGlobal rg = Minecraft.getMinecraft().renderGlobal;

		Block.leaves.setGraphicsLevel(Minecraft.getMinecraft().gameSettings.fancyGraphics);
		int renderDistance = Minecraft.getMinecraft().gameSettings.renderDistance;

		MANAGER = SectionManager.getCurrentInstance();
		MANAGER.setWorld(Minecraft.getMinecraft().theWorld);
		SHOULD_RELOAD = true;

		((List<?>) HookUtils.getFieldObj(rg, "worldRenderersToUpdate")).clear();
		((List<?>) HookUtils.getFieldObj(rg, "tileEntities")).clear();

		VertexWriterManager.startDefaults();

		HookUtils.setField(rg, "renderEntitiesStartupCounter", 2);
		HookUtils.setField(rg, "renderDistance", renderDistance);
	}

	private void clearBuffers() {
		VertexWriterManager.clearBuffers();
	}

	public static void clipRenderersByFrustum(Frustrum frustum, float partialTick) {
		Minecraft mc = Minecraft.getMinecraft();

		double cameraX = (Float) HookUtils.getFieldObj(frustum, "xPosition");
		double cameraY = (Float) HookUtils.getFieldObj(frustum, "yPosition");
		double cameraZ = (Float) HookUtils.getFieldObj(frustum, "zPosition");

		Minecraft minecraft = Minecraft.getMinecraft();

		MANAGER.update(minecraft.gameSettings.renderDistance, cameraX, cameraY, cameraZ, SHOULD_RELOAD, partialTick);
		SHOULD_RELOAD = false;
	}

	public int sortAndRender(int renderPass) {
		MANAGER.drawRenderPass(renderPass);

		if (renderPass == 0) {
//			this.renderersBeingRendered = this.manager.drawnSolidRenderers;
//			this.renderersLoaded = this.manager.drawnSolidRenderers;
			MANAGER.drawnSolidRenderers = 0;
		}

		return 0;
	}

	public void blockChanged(int x, int y, int z) {
		MANAGER.blockUpdate(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
	}

	public void setBlocksDirty(int x0, int y0, int z0, int x1, int y1, int z1) {
		MANAGER.blockUpdate(x0 - 1, y0 - 1, z0 - 1, x1 + 1, y1 + 1, z1 + 1);
	}
}
