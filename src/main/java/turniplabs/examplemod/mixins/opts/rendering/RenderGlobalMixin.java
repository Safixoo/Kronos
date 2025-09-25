package turniplabs.examplemod.mixins.opts.rendering;

import net.minecraft.client.GLAllocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.options.ScreenOptions;
import net.minecraft.client.render.RenderGlobal;
import net.minecraft.client.render.block.model.BlockModelLeaves;
import net.minecraft.client.render.camera.ICamera;
import net.minecraft.client.render.culling.CameraFrustum;
import net.minecraft.client.render.terrain.ChunkRenderer;
import net.minecraft.client.render.terrain.ChunkRendererLegacy;
import net.minecraft.client.world.WorldClient;
import net.minecraft.core.block.entity.TileEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import turniplabs.examplemod.client.render.SectionManager;
import turniplabs.examplemod.client.render.shader.ShaderLoader;
import turniplabs.examplemod.client.render.vertex.VertexWriterManager;

import java.util.List;

@Mixin(value = RenderGlobal.class, remap = false)
public abstract class RenderGlobalMixin {
	@Shadow
	private WorldClient worldObj;
	@Shadow
	private Integer renderDistance;
	@Shadow
	@Final
	private Minecraft mc;
	@Shadow
	private int renderEntitiesStartupCounter;
	@Shadow
	public abstract void updateStars();
	@Shadow
	public abstract void deleteRenderListBase();
	@Shadow
	public List<TileEntity> tileEntities;
	@Shadow
	private ChunkRenderer[] chunkRenderers;
	@Shadow
	private int glRenderListBase;
	@Shadow
	private int renderersBeingRendered;
	@Shadow
	private int renderersLoaded;
	private SectionManager manager;
	private boolean shouldReload;

	/**
	 * @author Safixo
	 * @reason Rewire to our impl.
	 */
	@Overwrite
	public void loadRenderers() {
		this.renderDistance = this.mc.gameSettings.renderDistance.value;

		// Many functions access this array, to make it work add a dummy value.
		this.chunkRenderers = new ChunkRenderer[] {
			new ChunkRendererLegacy(null, null, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, 0, 0)
		};

		this.manager = SectionManager.getCurrentInstance();
		this.manager.setWorld(this.worldObj);
		this.shouldReload = true;

		ScreenOptions.queueReload = false;
		this.updateStars();
		this.deleteRenderListBase();

		this.tileEntities.clear();

		// Don't add as many display lists as vanilla, as they won't be used for chunks.
		this.glRenderListBase = GLAllocation.generateDisplayLists(4096);

		VertexWriterManager.startDefaults();

		BlockModelLeaves.setGraphicsLevel(this.mc.gameSettings.fancyGraphics.value == 1);

		this.renderEntitiesStartupCounter = 2;
	}

	@Inject(method = "deleteRenderListBase", at = @At("HEAD"))
	private void clearBuffers(CallbackInfo ci) {
		VertexWriterManager.clearBuffers();
	}

	/**
	 * @author Safixo
	 * @reason Rewire to our impl.
	 */
	@Overwrite
	public void clipRenderersByFrustum(CameraFrustum frustum, float partialTick) {
		ICamera camera = this.mc.activeCamera;

		double cameraX = camera.getX(partialTick);
		double cameraY = camera.getY(partialTick);
		double cameraZ = camera.getZ(partialTick);

		this.manager.update(this.renderDistance, cameraX, cameraY, cameraZ, this.shouldReload, partialTick);
		this.shouldReload = false;
	}

	/**
	 * @author Safixo
	 * @reason Rewire to our impl.
	 */
	@Overwrite
	public int sortAndRender(ICamera camera, int renderPass, double partialTick) {
		this.manager.drawRenderPass(renderPass);

		if (renderPass == 0) {
			this.renderersBeingRendered = this.manager.drawnSolidRenderers;
			this.renderersLoaded = this.manager.drawnSolidRenderers;
			this.manager.drawnSolidRenderers = 0;
		}

		return 0;
	}

	/**
	 * @author Safixo
	 * @reason Rewire to our impl.
	 */
	@Overwrite
	public void blockChanged(int x, int y, int z) {
		this.manager.blockUpdate(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
	}

	/**
	 * @author Safixo
	 * @reason Rewire to our impl.
	 */
	@Overwrite
	public void setBlocksDirty(int x0, int y0, int z0, int x1, int y1, int z1) {
		this.manager.blockUpdate(x0 - 1, y0 - 1, z0 - 1, x1 + 1, y1 + 1, z1 + 1);
	}
}
