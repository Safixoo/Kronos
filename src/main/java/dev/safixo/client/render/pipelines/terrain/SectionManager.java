package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.util.ClientChunkListener;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
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
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;

import java.util.List;
import java.util.Set;

public class SectionManager {
	public static final int MAX_UPDATE_QUEUES = 6;
	private static final Item DEBUG_ITEM = null;

	private final Long2ReferenceOpenHashMap<SectionRender> sectionMap = new Long2ReferenceOpenHashMap<>(4096);

	private static SectionManager INSTANCE;

	private TerrainProgram terrainShader;
	private final BFSCuller bfsCuller = new BFSCuller();
	private final RegionManager regionManager = new RegionManager();
	private World worldObj;
	private CameraData camera;

	private long vramUsed, vramAllocated;
	private int renderDistance;
	public int drawnSolidRenderers;

	private final ReferenceOpenHashSet<TileEntity> tileEntitiesSet = new ReferenceOpenHashSet<>();

	public SectionManager(WorldClient world) {
		INSTANCE = this;
		this.worldObj = world;
	}

	public static SectionManager getCurrentInstance() {
		if (INSTANCE == null) {
			INSTANCE = new SectionManager(Minecraft.getMinecraft().theWorld);
		}

		return INSTANCE;
	}

	public CameraData getCamera() {
		return this.camera;
	}

	public static RegionManager getRegionManager() {
		return getCurrentInstance().regionManager;
	}

	public int getLastFrame() {
		return this.bfsCuller.getActiveFrame();
	}

	public RegionRender getRegion(int sectionX, int sectionY, int sectionZ) {
		return this.regionManager.getRegion(sectionX, sectionY, sectionZ);
	}

	public int getRegionCount() {
		return this.regionManager.regionMap.size();
	}

	public int allocatedSections() {
		return this.sectionMap.size();
	}

	public void removeRender(int posX, int posY, int posZ) {
		long position = MathExt.asLong(posX, posY, posZ);

		SectionRender sectionRender = this.sectionMap.remove(position);

		if (sectionRender != null) {
			sectionRender.clearAllocations();
			this.disconnectNeighbors(sectionRender);
		}
	}

	public void addUsedMemory(int bytes) {
		this.vramUsed += bytes;
	}

	public void removeUsedMemory(long bytes) {
		this.vramUsed -= bytes;
	}

	public void addMemory(long bytes) {
		this.vramAllocated += bytes;
	}

	public void removeMemory(long bytes) {
		this.vramAllocated -= bytes;
	}

	public long getMemoryUsed() {
		return (this.vramUsed / 1024L) / 1024L;
	}

	public long getMemoryTotal() {
		return (this.vramAllocated / 1024L) / 1024L;
	}

	public void markDirty(int posX, int posY, int posZ) {
		long position = MathExt.asLong(posX, posY, posZ);

		SectionRender sectionRender = this.sectionMap.get(position);

		if (sectionRender == null) {
			sectionRender = new SectionRender(posX * 16, posY * 16, posZ * 16);

			this.sectionMap.put(position, sectionRender);
			this.connectNeighbors(sectionRender);
		}

		sectionRender.markDirty(true);
	}

	public static void destroyInstance() {
		if (INSTANCE == null) {
			return;
		}

		if (INSTANCE.terrainShader != null) {
			INSTANCE.terrainShader.delete();
		}

		INSTANCE.clearRenderer();
		INSTANCE = null;
	}

	public void update(WorldClient world, int renderDistance, double cameraX, double cameraY, double cameraZ, boolean worldChanged, float partialTick) {
		this.camera = extractCameraData(cameraX, cameraY, cameraZ, renderDistance);
		this.regionManager.update(this.camera, renderDistance, worldChanged);

		FrustumCuller.addFractToCamera(this.camera.fractX, this.camera.fractY, this.camera.fractZ);
		Profiler profiler = Minecraft.getMinecraft().mcProfiler;

		if (this.renderDistance != renderDistance || worldChanged) {
			this.renderDistance = renderDistance;
			this.worldObj = world;

			this.tileEntitiesSet.clear();
			this.generateWholeVolume(cameraX, cameraZ);
		}

		IChunkProvider provider = world.getChunkProvider();

		if (provider.getClass() == ClientChunkListener.class) {
			((ClientChunkListener)provider).processAllQueuedSections();
		}

		EntityClientPlayerMP playerLocal = Minecraft.getMinecraft().thePlayer;
		InventoryPlayer inventory = playerLocal.inventory;

		profiler.endStartSection("culling");

		Item playerItem = null;

		if (inventory != null && inventory.getCurrentItem() != null) {
			playerItem = inventory.getCurrentItem().getItem();
		}

		// For debugging occ culling.
		//noinspection ConstantValue
		if (playerItem != DEBUG_ITEM || DEBUG_ITEM == null) {
			this.bfsCuller.init(this.regionManager, renderDistance);
			this.bfsCuller.updateRenderList(this.sectionMap, this.camera);
		}

		profiler.endStartSection("updatechunks");

		this.queueRebuilds(this.tileEntitiesSet);

		profiler.endStartSection("ticking");

		@SuppressWarnings("unchecked")
		List<TileEntity> tileEntities = (List<TileEntity>) Minecraft.getMinecraft().renderGlobal.tileEntities;

		tileEntities.clear();
		tileEntities.addAll(this.tileEntitiesSet);

		profiler.endStartSection("updatechunks");
	}

	private static CameraData extractCameraData(double cameraX, double cameraY, double cameraZ, int renderDistance) {
		int playerX = MathExt.floor(cameraX);
		int playerY = MathExt.floor(cameraY);
		int playerZ = MathExt.floor(cameraZ);

		float fractX = (float) (cameraX - playerX);
		float fractY = (float) (cameraY - playerY);
		float fractZ = (float) (cameraZ - playerZ);

		return new CameraData(fractX, fractY, fractZ, playerX, playerY, playerZ, renderDistance);
	}

	private void queueRebuilds(Set<TileEntity> tileSet) {
		int rebuildSize = RebuildList.size();
		int maxSize = Math.min(MAX_UPDATE_QUEUES, rebuildSize);

		SectionRender[] updateArray = RebuildList.getBackedArray();
		PrimitivesFlags.processLeavesSolid();
		PrimitivesFlags.REDIRECT_DRAWING = true;

		for (int i = 0; i < maxSize; i++) {
			SectionRender section = updateArray[i];

			if (section.currentFrame == this.bfsCuller.getActiveFrame() && section.isDirty()) {
				section.rebuild(this.camera, this, this.worldObj, tileSet);
			}
		}

		PrimitivesFlags.REDIRECT_DRAWING = false;
		RebuildList.clear();
	}
	private void generateWholeVolume(double cameraX, double cameraZ) {
		int cameraChunkX = MathExt.posToSectionIntegral(cameraX);
		int cameraChunkZ = MathExt.posToSectionIntegral(cameraZ);

		int renderDistance = this.renderDistance + 2;
		ClientChunkListener provider = (ClientChunkListener) this.worldObj.getChunkProvider();

		for (int x = -renderDistance; x <= renderDistance; x++) {
			for (int z = -renderDistance; z <= renderDistance; z++) {
				if (!provider.shouldLoadChunk(cameraChunkX + x, cameraChunkZ + z)) {
					continue;
				}

				for (int y = 0; y < 16; y++) {
					this.markDirty(cameraChunkX + x, y, cameraChunkZ + z);
				}
			}
		}
	}

	private void clearRenderer() {
		this.sectionMap.clear();
		this.tileEntitiesSet.clear();
	}

	public void drawRenderPass(int renderPass) {
		// Disables fog when option is active.
		boolean noFog = false;

		if (this.terrainShader == null) {
			this.terrainShader = new TerrainProgram();
		}

		if (!this.lastEvent && Keyboard.getEventKey() == Keyboard.KEY_ADD) {
			this.terrainShader.compile();
		}

		this.lastEvent = Keyboard.getEventKey() == Keyboard.KEY_ADD;

		this.terrainShader.useProgram();
		this.terrainShader.setupUniforms(noFog);

		this.regionManager.drawAllRegions(this.terrainShader, this.bfsCuller.bfsQueue, this.camera, renderPass);
		this.terrainShader.disableProgram();
	}

	boolean lastEvent = false;

	public void connectNeighbors(SectionRender render) {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			SectionRender adjacent = this.getSection(render, dir);

			if (adjacent != null) {
				adjacent.setAdjacentNeighbor(render, Direction.opposite(dir));
			}

			render.setAdjacentNeighbor(adjacent, dir);
		}
	}

	public void disconnectNeighbors(SectionRender render) {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			SectionRender renderer = this.getSection(render, dir);

			if (renderer != null) {
				renderer.setAdjacentNeighbor(null, Direction.opposite(dir));
			}

			render.setAdjacentNeighbor(null, dir);
		}
	}

	private SectionRender getSection(SectionRender section, int direction) {
		int chunkX = (section.blockX >> 4) + Direction.x(direction);
		int chunkY = (section.blockY >> 4) + Direction.y(direction);
		int chunkZ = (section.blockZ >> 4) + Direction.z(direction);

		return this.sectionMap.get(MathExt.asLong(chunkX, chunkY, chunkZ));
	}

}
