package dev.safixo.client.render;

import dev.safixo.client.render.util.data.FogData;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemEgg;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import dev.safixo.client.render.cull.BFSCuller;
import dev.safixo.client.render.cull.FrustumCuller;
import dev.safixo.client.render.cull.UpdateQueue;
import dev.safixo.client.render.shader.ShaderSectionTerrain;
import dev.safixo.client.render.util.data.BlocksFlags;
import dev.safixo.client.render.util.data.CameraData;
import dev.safixo.client.render.region.RegionManager;
import dev.safixo.client.render.region.RegionRender;
import dev.safixo.client.render.util.Direction;
import dev.safixo.client.render.util.MathExt;

public class SectionManager {
	private static final int MAX_UPDATE_QUEUES = 15;

	private final Long2ReferenceOpenHashMap<SectionRender> sectionMap = new Long2ReferenceOpenHashMap<>();
	private final LongOpenHashSet chunkExistence = new LongOpenHashSet();

	private final BFSCuller bfsCuller = new BFSCuller();
	private final RegionManager regionManager = new RegionManager();
	private static SectionManager INSTANCE;

	private World worldObj;

	private CameraData camera;
	private double lastUpdateX, lastUpdateZ;
	private double lastRemoveX, lastRemoveZ;
	private int renderDistance;

	private long lastPositionCache = -1;
	private SectionRender lastSectionCache = null;

	private long vramUsed;
	private long vramAllocated;
	public int drawnSolidRenderers;

	private final long[] lastFrameSamples = new long[32];
	private int frameSampleInd;
	private long lastFrameTime;
	private long lastFrameBudget;

	private ShaderSectionTerrain terrainShader;

	public SectionManager(World world) {
		INSTANCE = this;

		this.worldObj = world;
	}

	public static SectionManager getCurrentInstance() {
		if (INSTANCE == null) {
			INSTANCE = new SectionManager(Minecraft.getMinecraft().theWorld);
		}

		return INSTANCE;
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

	public int allocatedSections() {
		return this.sectionMap.size();
	}

	public void setWorld(World world) {
		this.worldObj = world;
	}

	public static long asLong(int x, int y, int z) {
		long pos = 0L;

		pos |= ((long) x & 0x3FFFFFL) << 42;
		pos |= ((long) y & 0xFFFFFL) << 0;
		pos |= ((long) z & 0x3FFFFFL) << 20;

		return pos;
	}

	public static long asLong(int x, int z) {
		return (x & 0xFFFF_FFFFL) | (z & 0xFFFF_FFFFL) << 32L;
	}

	public void removeRender(int posX, int posY, int posZ) {
		long position = asLong(posX, posY, posZ);

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
		long position = asLong(posX, posY, posZ);
		SectionRender sectionRender = this.sectionMap.get(position);

		if (sectionRender == null) {
			sectionRender = this.addRender(posX, posY, posZ, true);
		}

		if (this.camera != null && MathExt.squaredDistance(sectionRender, this.camera) < MathExt.square(48.0f)) {
			UpdateQueue.addToQueue(sectionRender);
		}

		sectionRender.flags = SectionFlags.setDirty(sectionRender.flags, true);
	}

	public SectionRender addRender(int posX, int posY, int posZ, boolean trulyNew) {
		long position = asLong(posX, posY, posZ);

		SectionRender sectionRender = trulyNew ? null : this.sectionMap.get(position);

		if (sectionRender == null) {
			sectionRender = new SectionRender(posX * 16, posY * 16, posZ * 16);

			this.sectionMap.put(position, sectionRender);
			this.connectNeighbors(sectionRender);
		}

		sectionRender.flags = SectionFlags.setDirty(sectionRender.flags, true);
		return sectionRender;
	}

	public void update(int renderDistance, double cameraX, double cameraY, double cameraZ, boolean worldChanged, float partialTick) {
		this.camera = extractCameraData(cameraX, cameraY, cameraZ, renderDistance);
		this.regionManager.update(this.camera, renderDistance, worldChanged);

		FrustumCuller.addFractToCamera(this.camera.fractX, this.camera.fractY, this.camera.fractZ);

		if (this.renderDistance != renderDistance || worldChanged) {
			this.renderDistance = renderDistance;
			this.lastUpdateX = cameraX;
			this.lastUpdateZ = cameraZ;

			this.lastRemoveX = cameraX;
			this.lastRemoveZ = cameraZ;

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

		if (inventory == null || inventory.getCurrentItem() == null || !(inventory.getCurrentItem().getItem() instanceof ItemEgg)) {
			this.bfsCuller.init(this.regionManager, this.camera.intX, this.camera.intZ, renderDistance);
			this.bfsCuller.updateRenderList(this.sectionMap, this.camera);
		}

		this.queueRebuilds(partialTick);
	}

	private static CameraData extractCameraData(double cameraX, double cameraY, double cameraZ, int renderDistance) {
		int playerX = (int) Math.floor(cameraX);
		int playerY = (int) Math.floor(cameraY);
		int playerZ = (int) Math.floor(cameraZ);

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
		long lerpedBudget = MathExt.lerp(this.lastFrameBudget, maxBudget, partialTick);

		this.lastFrameBudget = lerpedBudget;
		this.lastFrameTime = currentTime;

		int maxSize = Math.min(MAX_UPDATE_QUEUES, UpdateQueue.size());
		int i = 0;

		int samples = 0;
		long timePassed = 0L;
		long estimatedTime = 0L;

		SectionRender render = UpdateQueue.get(i++);

		BlocksFlags.processLeavesSolid();

		while (i < maxSize && MathExt.squaredDistance(render, this.camera) < MathExt.square(40.0f)) {
			render.rebuild(this.camera, this, this.worldObj);
			render = UpdateQueue.get(i++);
		}

		while (i < maxSize && timePassed < lerpedBudget && estimatedTime < lerpedBudget) {
			currentTime = System.nanoTime();

			render = UpdateQueue.get(i++);
			render.rebuild(this.camera, this, this.worldObj);

			samples++;
			timePassed += System.nanoTime() - currentTime;
			estimatedTime = (timePassed / samples) * (MAX_UPDATE_QUEUES - i);
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
		LongArrays.quickSort(this.lastFrameSamples);
		return this.lastFrameSamples[16];
	}

	private static int posToSectionIntegral(double position) {
		return MathExt.floor(position) >> 4;
	}

	private void generateSections() {
		int lastChunkCameraX = posToSectionIntegral(this.lastUpdateX);
		int lastChunkCameraZ = posToSectionIntegral(this.lastUpdateZ);

		int lastChunkRemoveX = posToSectionIntegral(this.lastRemoveX);
		int lastChunkRemoveZ = posToSectionIntegral(this.lastRemoveZ);

		int currentCameraX = posToSectionIntegral(this.camera.intX);
		int currentCameraZ = posToSectionIntegral(this.camera.intZ);

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
				long position = asLong(chunkX, chunkZ);

				// Add new sections in distance.
				if (newX >= safeDistanceCheck) {
					this.lastUpdateX = this.camera.cameraXD();

					if (!this.chunkExistence.contains(position)) {
						for (int y = 0; y < 16; y++) {
							this.addRender(chunkX, y, chunkZ, false);
						}

						this.chunkExistence.add(position);
					}
				}
				if (newZ >= safeDistanceCheck) {
					if (!this.chunkExistence.contains(position)) {
						this.lastUpdateZ = this.camera.cameraZD();

						for (int y = 0; y < 16; y++) {
							this.addRender(chunkX, y, chunkZ, false);
						}

						this.chunkExistence.add(position);
					}
				}

				// Remove distant sections.
				if (oldX >= renderDistance) {
					this.lastRemoveX = this.camera.cameraXD();

					for (int y = 0; y < 16; y++) {
						this.removeRender(chunkX, y, chunkZ);
					}
					this.chunkExistence.remove(position);
				}

				if (oldZ >= renderDistance) {
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
					this.addRender(cameraChunkX + x , y, cameraChunkZ + z, true);
				}
			}
		}
	}

	private void clearRenderer() {
		this.sectionMap.clear();
	}

	public void blockUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		minX = posToSectionIntegral(minX);
		minY = posToSectionIntegral(minY);
		minZ = posToSectionIntegral(minZ);

		maxX = posToSectionIntegral(maxX);
		maxY = posToSectionIntegral(maxY);
		maxZ = posToSectionIntegral(maxZ);

		int chunkMinX = Math.min(minX, maxX);
		int chunkMinY = Math.min(minY, maxY);
		int chunkMinZ = Math.min(minZ, maxZ);

		int chunkMaxX = Math.max(minX, maxX);
		int chunkMaxY = Math.max(minY, maxY);
		int chunkMaxZ = Math.max(minZ, maxZ);

		for (int x = chunkMinX; x <= chunkMaxX; x++) {
			for (int y = chunkMinY; y <= chunkMaxY; y++) {
				for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
					this.markDirty(x, y, z);
				}
			}
		}
	}

	public void drawRenderPass(int renderPass) {
		// Disables fog when option is active.
		boolean noFog = true;

		// Look like terrain display lists have some of these states baked.
		if (renderPass == 1) {
			GL11.glColorMask(true, true, true, true);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glEnable(GL11.GL_CULL_FACE);
		}

		if (this.terrainShader == null) {
			this.terrainShader = new ShaderSectionTerrain();
		}

		if (!this.lastEvent && Keyboard.getEventKey() == Keyboard.KEY_ADD) {
			this.terrainShader.prepareAndCompileShader();
		}

		this.lastEvent = Keyboard.getEventKey() == Keyboard.KEY_ADD;

		this.terrainShader.bindProgram();
		this.terrainShader.setupUniforms(noFog);

		this.regionManager.drawAllRegions(this.terrainShader, this.bfsCuller.bfsQueue, this.camera, renderPass);
		this.terrainShader.unbindProgram();
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

		return this.sectionMap.get(asLong(chunkX, chunkY, chunkZ));
	}

}
