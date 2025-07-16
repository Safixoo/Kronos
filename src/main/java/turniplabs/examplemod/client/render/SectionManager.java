package turniplabs.examplemod.client.render;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.PlayerLocal;
import net.minecraft.core.item.ItemEgg;
import net.minecraft.core.player.inventory.container.ContainerInventory;
import net.minecraft.core.world.World;
import org.lwjgl.opengl.GL11;
import turniplabs.examplemod.client.GlobalFlags;
import turniplabs.examplemod.client.render.cull.BFSCuller;
import turniplabs.examplemod.client.render.cull.FrustumCuller;
import turniplabs.examplemod.client.render.cull.UpdateQueue;
import turniplabs.examplemod.client.render.data.CameraData;
import turniplabs.examplemod.client.render.data.FogData;
import turniplabs.examplemod.client.render.meshing.BlockRenderer;
import turniplabs.examplemod.client.render.region.RegionManager;
import turniplabs.examplemod.client.render.region.RegionRender;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.util.Mth;

public class SectionManager {
	private static final int MAX_UPDATE_QUEUES = 10;

	private final Long2ReferenceOpenHashMap<SectionRender> sectionMap = new Long2ReferenceOpenHashMap<>();
	private final BFSCuller bfsCuller = new BFSCuller();
	private final RegionManager regionManager = new RegionManager();
	private final BlockRenderer blockRenderer = new BlockRenderer();
	private static SectionManager INSTANCE;

	private World worldObj;

	private CameraData camera;
	private double lastUpdateX, lastUpdateZ;
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
			INSTANCE = new SectionManager(Minecraft.getMinecraft().currentWorld);
		}

		return INSTANCE;
	}

	public static RegionManager getRegionManager() {
		return getCurrentInstance().regionManager;
	}

	public RegionRender getRegion(int sectionX, int sectionY, int sectionZ) {
		return this.regionManager.getRegion(sectionX, sectionY, sectionZ);
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

	public void removeRender(int posX, int posY, int posZ) {
		long position = asLong(posX, posY, posZ);

		SectionRender sectionRender = this.sectionMap.remove(position);

		if (sectionRender != null) {
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
		SectionRender sectionRender;

		// Cache last entry, IDR if this were really necessary.
		if (position == this.lastPositionCache) {
			sectionRender = this.lastSectionCache;
		} else {
			this.lastSectionCache = sectionRender = this.sectionMap.get(position);
			this.lastPositionCache = position;
		}

		if (sectionRender == null) {
			sectionRender = this.addRender(posX, posY, posZ, true);
		}

		sectionRender.flags = SectionFlags.setDirty(sectionRender.flags, true);
	}

	public SectionRender addRender(int posX, int posY, int posZ, boolean trulyNew) {
		long position = asLong(posX, posY, posZ);

		SectionRender sectionRender = trulyNew ? null : this.sectionMap.getOrDefault(position, null);

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

		FrustumCuller.addFractCamera(this.camera.fractX, this.camera.fractY, this.camera.fractZ);

		if (this.renderDistance != renderDistance || worldChanged) {
			this.renderDistance = renderDistance;
			this.lastUpdateX = cameraX;
			this.lastUpdateZ = cameraZ;

			this.clearRenderer();
			this.generateWholeVolume(cameraX, cameraZ);
		}

		double diffX = Mth.square(cameraX - this.lastUpdateX);
		double diffZ = Mth.square(cameraZ - this.lastUpdateZ);

		if (diffX + diffZ >= Mth.square(4.0)) {
			this.generateSections();
		}

		PlayerLocal playerLocal = Minecraft.getMinecraft().thePlayer;
		ContainerInventory inventory = playerLocal.inventory;

		if (inventory == null || inventory.getCurrentItem() == null || !(inventory.getCurrentItem().getItem() instanceof ItemEgg)) {
			this.bfsCuller.init(this.regionManager, this.camera.intX, this.camera.intZ, renderDistance);
			this.bfsCuller.updateRenderList(this.sectionMap, this.camera);
		}

		this.queueRebuilds(partialTick);
	}

	private static CameraData extractCameraData(double cameraX, double cameraY, double cameraZ, int renderDistance) {
		int playerX = (int) cameraX;
		int playerY = (int) cameraY;
		int playerZ = (int) cameraZ;

		float fractX = (float) (cameraX - playerX);
		float fractY = (float) (cameraY - playerY);
		float fractZ = (float) (cameraZ - playerZ);

		return new CameraData(fractX, fractY, fractZ, playerX, playerY, playerZ, renderDistance);
	}

	// TODO:
	//  - Implement off-thread chunk updates.
	//  - Separate in meshing and writing to batch copies.
	private void queueRebuilds(float partialTick) {
		long currentTime = System.nanoTime();
		long currentDiff = this.lastFrameTime == 0 ? 200_000_000 : currentTime - this.lastFrameTime;

		this.addFrameSample(currentDiff);

		long maxBudget = Math.min((this.getFrameMedian() * 3) >>> 3, 250_000_000);
		long lerpedBudget = Mth.lerp(this.lastFrameBudget, maxBudget, partialTick);
		long smoothedBudget = (long) (Mth.smoothStep(lerpedBudget / 250_001.0) * 250_000.0);

		this.lastFrameBudget = smoothedBudget;
		this.lastFrameTime = currentTime;

		int maxSize = Math.min(MAX_UPDATE_QUEUES, UpdateQueue.size() - 1);
		int i = 0;

		GlobalFlags.MESHING = true;

		int samples = 0;
		long timePassed = 0L;
		long estimatedTime = 0L;

		while (i < maxSize && timePassed < smoothedBudget && estimatedTime < smoothedBudget) {
			currentTime = System.nanoTime();

			UpdateQueue.get(i++).rebuild(this, this.blockRenderer, this.worldObj);

			samples++;
			timePassed += System.nanoTime() - currentTime;
			estimatedTime = (timePassed / samples) * (MAX_UPDATE_QUEUES - i);
		}

		UpdateQueue.clear();

		GlobalFlags.MESHING = false;
	}

	public void addFrameSample(long currentDiff) {
		this.lastFrameSamples[this.frameSampleInd++] = currentDiff;
		this.frameSampleInd &= 31;
	}

	// Median should give a better result than prom for
	// avoiding lag spikes it seems.
	public long getFrameMedian() {
		LongArrays.unstableSort(this.lastFrameSamples);
		return this.lastFrameSamples[16];
	}

	private void generateSections() {
		int lastChunkCameraX = Math.floorDiv((int) this.lastUpdateX, 16);
		int lastChunkCameraZ = Math.floorDiv((int) this.lastUpdateZ, 16);

		int currentCameraX = Math.floorDiv(this.camera.intX, 16);
		int currentCameraZ = Math.floorDiv(this.camera.intZ, 16);

		// Doing currentCamera - lastChunkCamera is like generating a vector
		// from the last camera check pos to the current.
		int offsetX = (currentCameraX - lastChunkCameraX);
		int offsetZ = (currentCameraZ - lastChunkCameraZ);

		// Nothing has changed.
		if (offsetX == 0 && offsetZ == 0) {
			return;
		}

		// We scan all the render distance volume and if the diff between the last camera pos summed the xz
		// pos index of the render distance volume goes out of bounds from the xz min-max index it means
		// that it's a new or old section.
		for (int x = -this.renderDistance; x <= this.renderDistance; x++) {
			for (int z = -this.renderDistance; z <= this.renderDistance; z++) {
				int newX = x + offsetX;
				int newZ = z + offsetZ;

				// Add new sections in distance.
				if (newX <= -this.renderDistance || newX >= this.renderDistance) {
					this.lastUpdateX = this.camera.intX;

					for (int y = 0; y < 16; y++) {
						this.addRender(currentCameraX + x, y, currentCameraZ + z, false);
					}
				}
				if (newZ <= -this.renderDistance || newZ >= this.renderDistance) {
					this.lastUpdateZ = this.camera.intZ;

					for (int y = 0; y < 16; y++) {
						this.addRender(currentCameraX + x, y, currentCameraZ + z, false);
					}
				}

				// Remove sections in the symmetric opposite direction from the point we are adding sections, this
				// doesn't help tremendously, but it does something.
				if (newX < -this.renderDistance || newX > this.renderDistance || newZ < -this.renderDistance || newZ > this.renderDistance) {
					for (int y = 0; y < 16; y++) {
						this.removeRender(currentCameraX - x, y, currentCameraZ - z);
					}
				}
			}
		}
	}

	private void generateWholeVolume(double cameraX, double cameraZ) {
		int cameraChunkX = (int) cameraX >>> 4;
		int cameraChunkZ = (int) cameraZ >>> 4;

		for (int x = -this.renderDistance; x <= this.renderDistance; x++) {
			for (int z = -this.renderDistance; z <= this.renderDistance; z++) {
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
		minX = Math.floorDiv(minX, 16);
		minY = Math.floorDiv(minY, 16);
		minZ = Math.floorDiv(minZ, 16);

		maxX = Math.floorDiv(maxX, 16);
		maxY = Math.floorDiv(maxY, 16);
		maxZ = Math.floorDiv(maxZ, 16);

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
		boolean noFog = !Minecraft.getMinecraft().gameSettings.fog.value;

		// Look like terrain display lists have some of these states baked.
		// With my VBO rendering this isn't the case.
		if (renderPass == 1) {
			GL11.glColorMask(true, true, true, true);
			GL11.glEnable(GL11.GL_CULL_FACE);
		}

		if (this.terrainShader == null) {
			this.terrainShader = new ShaderSectionTerrain();
		}

		this.terrainShader.bindProgram();
		this.terrainShader.setupUniforms(this.camera.cameraX(), this.camera.cameraY(), this.camera.cameraZ(), noFog);

		this.regionManager.drawAllRegions(this.camera, renderPass);
		this.terrainShader.unbindProgram();
	}

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
		int chunkX = (section.blockX >>> 4) + Direction.x(direction);
		int chunkY = (section.blockY >>> 4) + Direction.y(direction);
		int chunkZ = (section.blockZ >>> 4) + Direction.z(direction);

		return this.sectionMap.getOrDefault(asLong(chunkX, chunkY, chunkZ), null);
	}

}
