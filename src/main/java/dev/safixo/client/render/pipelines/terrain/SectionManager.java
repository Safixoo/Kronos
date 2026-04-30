package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.render.pipelines.terrain.meshing.SectionMesher;
import dev.safixo.client.render.pipelines.terrain.shader.ExpFogProgram;
import dev.safixo.client.render.pipelines.terrain.shader.LinearFogProgram;
import dev.safixo.client.render.pipelines.terrain.shader.TerrainProgram;
import dev.safixo.client.util.ClientChunkListener;
import dev.safixo.core.hooks.GlStateTracker;
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
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class SectionManager {
	static {
		// free 10mb
		Minecraft.memoryReserve = null;
	}

	public static final int MAX_FULL_UPDATES = 3;
	public static final int MAX_UPDATES_TRIES = 32;
	private static final Item DEBUG_ITEM = null;

	private final Long2ReferenceOpenHashMap<SectionRender> sectionMap = new Long2ReferenceOpenHashMap<>(4096);

	public int[] sectionFlags;
	public short[] visibilitySet;

	private LinearFogProgram linearFogProgram;
	private ExpFogProgram expFogProgram;

	private final ReferenceOpenHashSet<TileEntity> tileEntitiesSet = new ReferenceOpenHashSet<>();
	private final BFSCuller bfsCuller = new BFSCuller();
	private final RegionManager regionManager = new RegionManager();
	private World worldObj;
	private CameraData camera;

	private long vramUsed, vramAllocated;
	private int renderDistance;
	public int drawnSolidRenderers;

	private boolean terrainDirty;

	private static SectionManager INSTANCE;

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

	public void markDirty(int posX, int posY, int posZ) {
		if (posY < 0 || posY >= 16) {
			return;
		}

		long position = MathExt.asLong(posX, posY, posZ);

		SectionRender sectionRender = this.sectionMap.get(position);

		if (sectionRender == null) {
			sectionRender = new SectionRender(posX * 16, posY * 16, posZ * 16);

			this.sectionMap.put(position, sectionRender);
			this.connectNeighbors(sectionRender);
		}

		this.terrainDirty = true;
		sectionRender.markDirty(true);
	}

	public static void destroyInstance() {
		if (INSTANCE == null) {
			return;
		}

		if (INSTANCE.expFogProgram != null) {
			INSTANCE.expFogProgram.delete();
		}
		if (INSTANCE.linearFogProgram != null) {
			INSTANCE.linearFogProgram.delete();
		}

		INSTANCE.clearRenderer();
		INSTANCE = null;
	}

	public void update(WorldClient world, int renderDistance, double cameraX, double cameraY, double cameraZ, boolean worldChanged, float partialTick) {
		CameraData camera = extractCameraData(cameraX, cameraY, cameraZ, renderDistance);

		boolean shouldUpdateGraph = !camera.equals(this.camera) || this.terrainDirty;

		this.camera = camera;
		this.regionManager.update(this.camera, renderDistance, worldChanged);

		FrustumCuller.addFractToCamera(this.camera.fractX, this.camera.fractY, this.camera.fractZ);
		Profiler profiler = Minecraft.getMinecraft().mcProfiler;

		if (this.renderDistance != renderDistance || worldChanged) {
			this.renderDistance = renderDistance;
			this.worldObj = world;

			this.tileEntitiesSet.clear();
			this.sectionMap.clear();
			this.generateWholeVolume(cameraX, cameraZ);
		}

		IChunkProvider provider = world.getChunkProvider();

		if (provider instanceof ClientChunkListener) {
			((ClientChunkListener) provider).processAllQueuedSections();
		}

		EntityClientPlayerMP playerLocal = Minecraft.getMinecraft().thePlayer;
		InventoryPlayer inventory = playerLocal.inventory;

		profiler.endStartSection("prepareCulling");

		this.initializeFlagData(renderDistance);
		this.resetVisibilityState();

		profiler.endStartSection("culling");

		Item playerItem = null;

		if (inventory != null && inventory.getCurrentItem() != null) {
			playerItem = inventory.getCurrentItem().getItem();
		}

		// For debugging occ culling.
		//noinspection ConstantValue
		if ((playerItem != DEBUG_ITEM || DEBUG_ITEM == null) && shouldUpdateGraph) {
			this.bfsCuller.init(this.regionManager);
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

	private static final int FLAG_NULL = SectionFlags.setCullFaces(0b0, 0b111_111);

	private void initializeFlagData(int renderDistance) {
		int size = MathExt.square(renderDistance * 2 + 1) * 16;

		if (this.sectionFlags == null || this.sectionFlags.length != size){
			this.sectionFlags = new int[size];
		}

		Arrays.fill(this.sectionFlags, FLAG_NULL);
		CameraData camera = this.camera;

		for (SectionRender section : this.sectionMap.values()) {
			int diffChunkX = (section.blockX >> 4) - (camera.intX >> 4);
			int diffChunkZ = (section.blockZ >> 4) - (camera.intZ >> 4);

			if (Math.abs(diffChunkX) > renderDistance || Math.abs(diffChunkZ) > renderDistance) {
				continue;
			}

			this.updateFlag(diffChunkX + renderDistance, section.blockY >> 4, diffChunkZ + renderDistance, section.flags);
		}
	}

	private void resetVisibilityState() {
		int rd = this.renderDistance;
		int size = MathExt.square(rd * 2 + 1) * 16;

		if (this.visibilitySet == null || this.visibilitySet.length != size){
			this.visibilitySet = new short[size];
		}

		Arrays.fill(this.visibilitySet, (short) 0);
	}

	public static int getFlagIndex(int offsetX, int sectionY, int offsetZ, int renderDistance) {
		int rd = (renderDistance * 2 + 1);
		return offsetX + (sectionY * rd) + (offsetZ * rd * 16);
	}

	public void updateFlag(int offsetX, int sectionY, int offsetZ, int flag) {
		if (this.sectionFlags == null) {
			return;
		}

		int flagIndex = getFlagIndex(offsetX, sectionY, offsetZ, this.camera.renderDistance);
		this.sectionFlags[flagIndex] = flag;
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
		int maxSize = Math.min(SectionManager.MAX_UPDATES_TRIES, rebuildSize);

		if (rebuildSize == 0) {
			this.terrainDirty = false;
		}

		long[] sectionPositions = RebuildList.getBackedArray();
		PrimitivesFlags.processLeavesSolid();
		PrimitivesFlags.REDIRECT_DRAWING = true;

		int i = 0, j = 0;

		while (i < maxSize && j < SectionManager.MAX_FULL_UPDATES) {
			SectionRender section = this.sectionMap.get(sectionPositions[i++]);

			if (section != null && section.isDirty()) {
				boolean nonEmpty = SectionMesher.rebuild(section, this.camera, this, this.worldObj, tileSet);

				if (nonEmpty) {
					j++;
				}
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
		this.regionManager.clear();
		this.sectionMap.clear();
		this.tileEntitiesSet.clear();
	}

	public void drawRenderPass(int renderPass) {
		// Disables fog when option is active.
		boolean noFog = false;
		boolean expFog = GlStateTracker.FOG_MODE == GL11.GL_EXP;
		boolean linearFog = GlStateTracker.FOG_MODE == GL11.GL_LINEAR;

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

		this.regionManager.drawAllRegions(terrainShader, this.camera, renderPass);
		terrainShader.disableProgram();
	}

	boolean lastEvent = false;

	public void connectNeighbors(SectionRender render) {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			SectionRender adjacent = this.getAdjacent(render, dir);

			if (adjacent != null) {
				adjacent.setAdjacentNeighbor(render, Direction.opposite(dir));
			}

			render.setAdjacentNeighbor(adjacent, dir);
		}
	}

	public void disconnectNeighbors(SectionRender render) {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			SectionRender renderer = this.getAdjacent(render, dir);

			if (renderer != null) {
				renderer.setAdjacentNeighbor(null, Direction.opposite(dir));
			}

			render.setAdjacentNeighbor(null, dir);
		}
	}

	private SectionRender getAdjacent(SectionRender section, int direction) {
		int chunkX = (section.blockX >> 4) + Direction.x(direction);
		int chunkY = (section.blockY >> 4) + Direction.y(direction);
		int chunkZ = (section.blockZ >> 4) + Direction.z(direction);

		return this.sectionMap.get(MathExt.asLong(chunkX, chunkY, chunkZ));
	}

}
