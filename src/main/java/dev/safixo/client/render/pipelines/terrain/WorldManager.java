package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.render.gfx.state.GlFogTracker;
import dev.safixo.client.render.pipelines.terrain.meshing.SectionMesher;
import dev.safixo.client.render.pipelines.terrain.shader.ExpFogProgram;
import dev.safixo.client.render.pipelines.terrain.shader.LinearFogProgram;
import dev.safixo.client.render.pipelines.terrain.shader.TerrainProgram;
import dev.safixo.client.util.ClientChunkListener;
import dev.safixo.core.hooks.RenderGlobalHook;
import dev.safixo.core.hooks.VertexRedirector;
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
import dev.safixo.client.render.pipelines.terrain.cull.RebuildList;
import dev.safixo.client.util.data.PrimitivesFlags;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.render.pipelines.terrain.region.RegionManager;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;
import dev.safixo.client.util.MathExt;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class WorldManager {
	private static final boolean DELETE_SHADERS = false;

	public static final int MAX_FULL_UPDATES = 7;
	public static final int MAX_UPDATES_TRIES = 48;
	private static final Item DEBUG_ITEM = null;

	private LinearFogProgram linearFogProgram;
	private ExpFogProgram expFogProgram;

	private final SectionMesher mesher = new SectionMesher();
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
		this.queueRebuilds();

		profiler.endStartSection("ticking");

		@SuppressWarnings("unchecked")
		List<TileEntity> tileEntities = (List<TileEntity>) Minecraft.getMinecraft().renderGlobal.tileEntities;
		this.regionManager.iterateAllTileEntities(tileEntities);

		profiler.endStartSection("updatechunks");
	}

	public SectionSet getSectionSet() {
		return this.sectionSet;
	}

	private long lastFrameNano;
	private long lastFrameBuildTime;
	private long lerpFrameBudget;

	private static final long MAX_TIME = (long) (1E+9D / 240);

	private long calculateFrameBudgetNs(long current) {
		long currentFrameTimeNs = (current - this.lastFrameNano) - this.lastFrameBuildTime;
		long budget = Math.min(currentFrameTimeNs / 8, MAX_TIME);

		// If the budget is over the one in the current frame, start giving more budget slowly,
		// but if the budget is lower simply give lower budget.
		if (budget > this.lerpFrameBudget && this.lerpFrameBudget != 0L) {
			// adds 10ms of budget per sec.
			budget = (long) Math.min(budget, this.lerpFrameBudget + (RenderGlobalHook.PARTIAL_TICK * 1E+7));
		}

		this.lerpFrameBudget = budget;
		return budget;
	}

	private void queueRebuilds() {
		int rebuildSize = RebuildList.size();
		int maxSize = Math.min(WorldManager.MAX_UPDATES_TRIES, rebuildSize);

		if (rebuildSize == 0) {
			this.terrainDirty = false;
		}

		long current = System.nanoTime();
		long budget = calculateFrameBudgetNs(current);

		PrimitivesFlags.processLeavesSolid();
		PrimitivesFlags.REDIRECT_DRAWING = true;
		VertexRedirector.ORGANIZE_NORMALS = true;

		int updateIndex = 0, nonEmptyUpdates = 0;

		while (updateIndex < maxSize && nonEmptyUpdates < WorldManager.MAX_FULL_UPDATES) {
			long position = RebuildList.getSectionPos(this.camera, updateIndex++);

			int sectionX = MathExt.decodeX(position);
			int sectionY = MathExt.decodeY(position);
			int sectionZ = MathExt.decodeZ(position);

			SectionRender section = this.sectionSet.getSectionInstance(this, sectionX, sectionY, sectionZ);

			if (section == null) {
				continue;
			}

			if (section.isDirty()) {
				boolean nonEmpty = this.mesher.buildMesh(section, this.camera, this, this.worldObj);

				if (nonEmpty) {
					nonEmptyUpdates++;
				}
			}

			if (this.isBudgetOver(current, budget)) {
				int distance = distanceToSection(section, this.camera);
				int minUpdates = distance <= 20*20 ? 2 : 1;

				if (nonEmptyUpdates >= minUpdates) {
					break;
				}
			}
		}

		VertexRedirector.ORGANIZE_NORMALS = false;
		PrimitivesFlags.REDIRECT_DRAWING = false;

		long diff = System.nanoTime() - current;
		float partialTick = RenderGlobalHook.PARTIAL_TICK;

		this.lastFrameBuildTime = diff > this.lastFrameBuildTime
			? diff
			: (long) Math.max(diff, this.lastFrameBuildTime - 5 * 1E+6D * partialTick);
		this.lastFrameNano = current;
	}

	private boolean isBudgetOver(long current, long budget) {
		long timeBuilding = System.nanoTime() - current;
		// if we are over budget, or we have passed 0.2ms more time building than last
		// frame, stop it.
		return timeBuilding >= budget || timeBuilding - (1E+6 / 5) >= this.lastFrameBuildTime;
	}

	private static int distanceToSection(SectionRender render, CameraData camera) {
		int dX = render.blockX - camera.intX + 8;
		int dY = render.blockY - camera.intY + 8;
		int dZ = render.blockZ - camera.intZ + 8;

		return MathExt.square(dX) + MathExt.square(dY) + MathExt.square(dZ);
	}

	public void drawRenderPass(int renderPass) {
		// Disables fog when option is active.
		boolean noFog = false;
		boolean expFog = GlFogTracker.FOG_MODE == GL11.GL_EXP;
		boolean linearFog = GlFogTracker.FOG_MODE == GL11.GL_LINEAR;

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
