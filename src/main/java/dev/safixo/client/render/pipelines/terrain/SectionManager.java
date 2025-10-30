package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.render.pipelines.terrain.meshing.ChunkListener;
import dev.safixo.client.util.data.FogData;
import dev.safixo.core.HookUtils;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemEgg;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import dev.safixo.client.render.pipelines.terrain.cull.BFSCuller;
import dev.safixo.client.render.pipelines.terrain.cull.FrustumCuller;
import dev.safixo.client.render.pipelines.terrain.cull.UpdateQueue;
import dev.safixo.client.util.data.BlocksFlags;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.render.pipelines.terrain.region.RegionManager;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;

public class SectionManager {
	private static final boolean GENERATE_SECTIONS_MANUALLY = false;
	private static final int MAX_UPDATE_QUEUES = 30;

	private final Long2ObjectOpenHashMap<SectionRender> sectionMap = new Long2ObjectOpenHashMap<>(4096);

	// Basically if there is a lot of queued sections and the player is not even active yet,
	// instead of discarding sections save them in an array to check later.
	private final LongArrayFIFOQueue queuedSections = new LongArrayFIFOQueue();

	// The generation logic is fucked up so is simply complemented with some extra structures.
	private final LongOpenHashSet chunkExistence = new LongOpenHashSet();

	private final BFSCuller bfsCuller = new BFSCuller();
	private final RegionManager regionManager = new RegionManager();
	private static SectionManager INSTANCE;
	private World worldObj;
	private CameraData camera;

	private double lastUpdateX, lastUpdateZ;
	private double lastRemoveX, lastRemoveZ;
	private int renderDistance;

	private long vramUsed;
	private long vramAllocated;
	public int drawnSolidRenderers;

	private final long[] lastFrameSamples = new long[32];
	private long lastFrameTime, lastFrameBudget;
	private int frameSampleInd;

	private TerrainProgram terrainShader;

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

	public void setWorld(World world) {
		this.worldObj = world;
	}

	public void removeRender(int posX, int posY, int posZ) {
		long position = MathExt.asLong(posX, posY, posZ);

		SectionRender sectionRender = this.sectionMap.containsKey(position) ? this.sectionMap.remove(position) : null;

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

	public void prepareRender(int posX, int posY, int posZ) {
		this.prepareRender(posX, posY, posZ, false);
	}

	public void markDirty(int posX, int posY, int posZ) {
		this.prepareRender(posX, posY, posZ, true);
	}

	public void prepareRender(int posX, int posY, int posZ, boolean markDirty) {
		long position = MathExt.asLong(posX, posY, posZ);

		if (this.camera == null) {
			return;
		}

		CameraData camera = this.camera;

		if (MathExt.squaredDistanceXZ(posX * 16, posZ * 16, camera) > MathExt.square(camera.renderDistance * 16)) {
			return;
		}

		SectionRender sectionRender = this.sectionMap.get(position);

		if (sectionRender == null) {
			sectionRender = new SectionRender(posX * 16, posY * 16, posZ * 16);

			this.sectionMap.put(position, sectionRender);
			this.chunkExistence.add(MathExt.asLong(posX, posZ));
			this.connectNeighbors(sectionRender);
		}

		if (markDirty) {
			sectionRender.flags = SectionFlags.setDirty(sectionRender.flags, true);
		}

		if (this.camera != null && MathExt.squaredDistanceXZ(sectionRender, this.camera) < MathExt.square(24.0f)) {
			UpdateQueue.addToQueue(sectionRender);
		}
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

		if (this.renderDistance != renderDistance || worldChanged) {
			this.renderDistance = renderDistance;
			this.lastUpdateX = cameraX;
			this.lastUpdateZ = cameraZ;

			this.lastRemoveX = cameraX;
			this.lastRemoveZ = cameraZ;

			this.worldObj = world;

			this.clearRenderer();
			this.generateWholeVolume(cameraX, cameraZ);
		}

		double diffX = MathExt.square(cameraX - this.lastUpdateX);
		double diffZ = MathExt.square(cameraZ - this.lastUpdateZ);

		if (diffX + diffZ >= MathExt.square(4.0)) {
			this.generateSections();
		}

		EntityClientPlayerMP playerLocal = Minecraft.getMinecraft().thePlayer;
		InventoryPlayer inventory = playerLocal.inventory;
		Profiler profiler = Minecraft.getMinecraft().mcProfiler;

		profiler.endStartSection("culling");

		if (inventory == null || inventory.getCurrentItem() == null || !(inventory.getCurrentItem().getItem() instanceof ItemEgg)) {
			extractFogData();

			this.bfsCuller.init(this.regionManager, this.camera.intX, this.camera.intZ, renderDistance);
			this.bfsCuller.updateRenderList(this.sectionMap, this.camera);
		}

		profiler.endStartSection("updatechunks");

		this.queueRebuilds(partialTick);
	}

	// This is done as injecting with ASM to get fog properties is harder and a
	// trivial way to get fog parameters without affecting *too* much performance
	// is simply using glGetFloat.
	private static void extractFogData() {
		FogData.FOG_END = GL11.glGetFloat(GL11.GL_FOG_END);
		FogData.FOG_START = GL11.glGetFloat(GL11.GL_FOG_START);

		Minecraft minecraft = Minecraft.getMinecraft();

		FogData.FOG_COLOR[0] = (Float) HookUtils.getFieldObj(minecraft.entityRenderer, "fogColorRed", "field_78518_n");
		FogData.FOG_COLOR[1] = (Float) HookUtils.getFieldObj(minecraft.entityRenderer, "fogColorGreen", "field_78519_o");
		FogData.FOG_COLOR[2] = (Float) HookUtils.getFieldObj(minecraft.entityRenderer, "fogColorBlue", "field_78533_p");
		FogData.FOG_COLOR[3] = 1.0f;
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

	private void queueRebuilds(float partialTick) {
		long currentTime = System.nanoTime();
		long currentDiff = this.lastFrameTime == 0 ? 200_000_000 : currentTime - this.lastFrameTime;

		this.addFrameSample(currentDiff);

		long maxBudget = Math.min(this.getFrameMedian() >>> 1, 550_000_000);
		long lerpedBudget = MathExt.lerp(this.lastFrameBudget, maxBudget, (double) partialTick);

		this.lastFrameBudget = lerpedBudget;
		this.lastFrameTime = currentTime;

		int maxSize = Math.min(MAX_UPDATE_QUEUES, UpdateQueue.size());
		int i = 0;

		int samples = 0;
		long timePassed = 0L;
		long estimatedTime = 0L;

		SectionRender render = UpdateQueue.get(i++);

		BlocksFlags.processLeavesSolid();

		while (i < maxSize && SectionFlags.isDirty(render.flags) && MathExt.squaredDistanceXZ(render, this.camera) < MathExt.square(24.0f)) {
			render.rebuild(this.camera, this, this.worldObj);
			render = UpdateQueue.get(i++);
		}

		while (i < maxSize && timePassed < lerpedBudget && estimatedTime < lerpedBudget) {
			currentTime = System.nanoTime();

			render = UpdateQueue.get(i++);

			if (SectionFlags.isDirty(render.flags) && render.currentFrame == this.bfsCuller.getActiveFrame()) {
				render.rebuild(this.camera, this, this.worldObj);
				samples++;
				timePassed += System.nanoTime() - currentTime;
				estimatedTime = (timePassed / samples) * (MAX_UPDATE_QUEUES - i);
			}
		}

		UpdateQueue.clear();
	}

	public void addFrameSample(long currentDiff) {
		this.lastFrameSamples[this.frameSampleInd++] = currentDiff;
		this.frameSampleInd &= 31;
	}

	// Median should give a better result than prom for
	// avoiding lag spikes it seems.
	public long getFrameMedian() {
		LongArrays.radixSort(this.lastFrameSamples);
		return this.lastFrameSamples[16];
	}

	private void generateSections() {
		int lastChunkCameraX = MathExt.posToSectionIntegral(this.lastUpdateX);
		int lastChunkCameraZ = MathExt.posToSectionIntegral(this.lastUpdateZ);

		int lastChunkRemoveX = MathExt.posToSectionIntegral(this.lastRemoveX);
		int lastChunkRemoveZ = MathExt.posToSectionIntegral(this.lastRemoveZ);

		int currentCameraX = MathExt.posToSectionIntegral(this.camera.intX);
		int currentCameraZ = MathExt.posToSectionIntegral(this.camera.intZ);

		int renderDistance = this.renderDistance;

		// Doing currentCamera - lastChunkCamera is like generating a vector
		// from the last camera check pos to the current.
		int offsetX = (currentCameraX - lastChunkCameraX);
		int offsetZ = (currentCameraZ - lastChunkCameraZ);

		int removalX = (currentCameraX - lastChunkRemoveX);
		int removalZ = (currentCameraZ - lastChunkRemoveZ);

		// Nothing has changed.
		if ((offsetX == 0 && offsetZ == 0) && (removalX == 0 && removalZ == 0)) {
			return;
		}

		// We scan all the render distance volume and if the diff between the last camera pos summed the xz
		// pos index of the render distance volume goes out of bounds from the xz min-max index it means
		// that it's a new or old section.
		for (int x = -renderDistance; x <= renderDistance; x++) {
			for (int z = -renderDistance; z <= renderDistance; z++) {
				int newX = Math.abs(x + offsetX);
				int newZ = Math.abs(z + offsetZ);

				int oldX = Math.abs(x - removalX);
				int oldZ = Math.abs(z - removalZ);

				int chunkX = currentCameraX + x;
				int chunkZ = currentCameraZ + z;

				int safeDistanceCheck = Math.max(0, renderDistance - 5);
				long position = MathExt.asLong(chunkX, chunkZ);

				if (GENERATE_SECTIONS_MANUALLY) {
					// Add new sections in distance.
					if (newX >= safeDistanceCheck) {
						this.lastUpdateX = this.camera.cameraXD();

						if (!this.chunkExistence.contains(position)) {
							for (int y = 0; y < 16; y++) {
								this.prepareRender(chunkX, y, chunkZ);
							}

							this.chunkExistence.add(position);
						}
					}
					if (newZ >= safeDistanceCheck) {
						if (!this.chunkExistence.contains(position)) {
							this.lastUpdateZ = this.camera.cameraZD();

							for (int y = 0; y < 16; y++) {
								this.prepareRender(chunkX, y, chunkZ);
							}

							this.chunkExistence.add(position);
						}
					}
				}

				// Remove distant sections.
				if (oldX >= renderDistance && !ChunkListener.canLoadChunk(chunkX, chunkZ)) {
					this.lastRemoveX = this.camera.cameraXD();

					for (int y = 0; y < 16; y++) {
						this.removeRender(chunkX, y, chunkZ);
					}
					this.chunkExistence.remove(position);
				} else if (oldZ >= renderDistance && !ChunkListener.canLoadChunk(chunkX, chunkZ)) {
					this.lastRemoveZ = this.camera.cameraZD();

					for (int y = 0; y < 16; y++) {
						this.removeRender(chunkX, y, chunkZ);
					}
					this.chunkExistence.remove(position);
				}
			}
		}
	}

	private void generateWholeVolume(double cameraX, double cameraZ) {
		int cameraChunkX = (int) cameraX >> 4;
		int cameraChunkZ = (int) cameraZ >> 4;

		int renderDistance = this.renderDistance;

		for (int x = -renderDistance; x <= renderDistance; x++) {
			for (int z = -renderDistance; z <= renderDistance; z++) {
				for (int y = 0; y < 16; y++) {
					this.prepareRender(cameraChunkX + x , y, cameraChunkZ + z);
				}
			}
		}
	}

	private void clearRenderer() {
		this.sectionMap.clear();
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
			SectionRender renderer = render.getAdjacent(dir);

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
