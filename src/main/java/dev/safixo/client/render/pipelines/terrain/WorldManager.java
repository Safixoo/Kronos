package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.render.gfx.state.GlFogTracker;
import dev.safixo.client.render.pipelines.terrain.meshing.MesherManager;
import dev.safixo.client.render.pipelines.terrain.shader.ExpFogProgram;
import dev.safixo.client.render.pipelines.terrain.shader.LinearFogProgram;
import dev.safixo.client.render.pipelines.terrain.shader.TerrainProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import org.lwjgl.input.Keyboard;
import dev.safixo.client.render.pipelines.terrain.cull.BFSCuller;
import dev.safixo.client.render.pipelines.terrain.cull.FrustumCuller;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.render.pipelines.terrain.region.RegionManager;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class WorldManager {
	private static final boolean DELETE_SHADERS = false;

	public static final int MAX_FULL_UPDATES = 7;
	public static final int MAX_UPDATES_TRIES = 40;
	public static final int MAX_TASK_CONCURRENTLY = 10;

	private static final Item DEBUG_ITEM = null;

	private LinearFogProgram linearFogProgram;
	private ExpFogProgram expFogProgram;

	private final MesherManager mesherManager = new MesherManager();
	private final SectionSet sectionSet = new SectionSet();
	private final BFSCuller bfsCuller = new BFSCuller();
	private final RegionManager regionManager = new RegionManager();

	private World worldObj;
	private CameraData camera;

	private long vramUsed, vramAllocated;
	private int renderDistance;
	private boolean graphUpdated;

	public int drawnSolidRenderers;

	private boolean terrainDirty;

	private static WorldManager INSTANCE;

	public WorldManager(WorldClient world) {
		INSTANCE = this;
		this.worldObj = world;
	}

	public static WorldManager getCurrentInstance() {
		if (INSTANCE == null) {
			INSTANCE = new WorldManager(Minecraft.getMinecraft().theWorld);
		}

		return INSTANCE;
	}

	public World getWorld() {
		return this.worldObj;
	}

	public CameraData getCamera() {
		return this.camera;
	}

	public static RegionManager getRegionManager() {
		return getCurrentInstance().regionManager;
	}

	public RegionRender getRegion(int sectionX, int sectionY, int sectionZ) {
		return this.regionManager.getRegion(sectionX, sectionY, sectionZ);
	}

	public int getRegionCount() {
		return this.regionManager.regionMap.size();
	}

	public void addUsedMemory(int bytes) {
		this.vramUsed += bytes;
	}

	public void removeUsedMemory(int bytes) {
		this.vramUsed -= bytes;
	}

	public void addMemory(int bytes) {
		this.vramAllocated += bytes;
	}

	public void removeMemory(int bytes) {
		this.vramAllocated -= bytes;
	}

	public long getMemoryUsed() {
		return (this.vramUsed / 1024L) / 1024L;
	}

	public long getMemoryTotal() {
		return (this.vramAllocated / 1024L) / 1024L;
	}

	public void markDirty(int sectionX, int sectionY, int sectionZ) {
		if (sectionY < 0 || sectionY >= 16) {
			return;
		}

		this.sectionSet.markDirty(sectionX, sectionY, sectionZ);
		this.terrainDirty = true;
	}

	public static void freeInstance() {
		if (INSTANCE == null) {
			return;
		}
		INSTANCE.clearInstance();
	}

	private void clearInstance() {
		if (DELETE_SHADERS) {
			if (this.expFogProgram != null) {
				this.expFogProgram.delete();
				this.expFogProgram = null;
			}
			if (this.linearFogProgram != null) {
				this.linearFogProgram.delete();
				this.linearFogProgram = null;
			}
		}

		this.mesherManager.clear();
		this.regionManager.clear();
	}

	public boolean hasGraphUpdated() {
		return this.graphUpdated;
	}

	public void update(WorldClient world, int renderDistance, double cameraX, double cameraY, double cameraZ, boolean worldChanged, float partialTick) {
		CameraData camera = new CameraData(cameraX, cameraY, cameraZ, renderDistance);

		boolean shouldUpdateGraph = !camera.equals(this.camera) || this.terrainDirty;
		this.camera = camera;

		FrustumCuller.addFractToCamera(this.camera.fractX, this.camera.fractY, this.camera.fractZ);
		Profiler profiler = Minecraft.getMinecraft().mcProfiler;

		if (this.renderDistance != renderDistance || worldChanged) {
			this.renderDistance = renderDistance;
			this.worldObj = world;
		}

		IChunkProvider provider = world.getChunkProvider();

		EntityClientPlayerMP playerLocal = Minecraft.getMinecraft().thePlayer;
		InventoryPlayer inventory = playerLocal.inventory;

		profiler.endStartSection("setup_sections");
		this.sectionSet.updateSet(this, camera, worldChanged);
		profiler.endStartSection("culling");

		Item playerItem = null;

		if (inventory != null && inventory.getCurrentItem() != null) {
			playerItem = inventory.getCurrentItem().getItem();
		}

		this.bfsCuller.clearUpdateIndices();

		// For debugging occ culling.
		//noinspection ConstantValue
		if ((playerItem != DEBUG_ITEM || DEBUG_ITEM == null) && shouldUpdateGraph) {
			this.bfsCuller.resetRegionCounters(this.regionManager);
			this.bfsCuller.updateRenderList(this, this.camera);
			this.graphUpdated = true;
		} else {
			this.graphUpdated = false;
		}

		profiler.endStartSection("updatechunks");

		this.regionManager.update(this.camera, renderDistance, worldChanged);
		this.mesherManager.queueRebuilds(this);

		profiler.endStartSection("ticking");

		@SuppressWarnings("unchecked")
		List<TileEntity> tileEntities = (List<TileEntity>) Minecraft.getMinecraft().renderGlobal.tileEntities;
		this.regionManager.iterateAllTileEntities(tileEntities);

		profiler.endStartSection("updatechunks");
	}

	public void tryToUploadResults() {
		if (this.mesherManager.areImportantResultsScheduled()) {
			this.mesherManager.readAsyncResults(this);
		}
	}

	public SectionSet getSectionSet() {
		return this.sectionSet;
	}

	public void setTerrainDirty(boolean flag) {
		this.terrainDirty = flag;
	}

	public void drawRenderPass(int renderPass) {
		// Disables fog when option is active.
		boolean noFog = false;
		boolean expFog = GlFogTracker.FOG_MODE == GL11.GL_EXP;
		boolean linearFog = GlFogTracker.FOG_MODE == GL11.GL_LINEAR;

		this.tryToUploadResults();

		TerrainProgram terrainShader;

		if (expFog && this.expFogProgram == null) {
			this.expFogProgram = new ExpFogProgram();
		} else if (linearFog && this.linearFogProgram == null) {
			this.linearFogProgram = new LinearFogProgram();
		}

		terrainShader = expFog ? this.expFogProgram : this.linearFogProgram;

		if (!this.lastEvent && Keyboard.getEventKey() == Keyboard.KEY_ADD) {
			terrainShader.compile();
		}

		this.lastEvent = Keyboard.getEventKey() == Keyboard.KEY_ADD;

		terrainShader.useProgram();
		terrainShader.setupUniforms(noFog);

		this.regionManager.drawAllRegions(this, terrainShader, this.camera, renderPass);
		terrainShader.disableProgram();
	}

	boolean lastEvent = false;

	static { // free 10mb
		Minecraft.memoryReserve = null;
	}
}
