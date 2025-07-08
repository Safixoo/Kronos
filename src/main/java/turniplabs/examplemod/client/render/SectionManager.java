package turniplabs.examplemod.client.render;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceCollection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import turniplabs.examplemod.client.GlobalFlags;
import turniplabs.examplemod.client.render.cull.BFSCuller;
import turniplabs.examplemod.client.render.gl.GlVertexBuffer;
import turniplabs.examplemod.client.render.meshing.BlockRenderer;
import turniplabs.examplemod.client.render.region.RegionManager;
import turniplabs.examplemod.client.render.region.RegionRender;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.util.Mth;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public class SectionManager {
	// Could be replaced in a future with a smart indexed array, but without reusing objects as vanilla.
	// (as that becomes a real pain mostly with bfs culling).
	private final Long2ReferenceOpenHashMap<SectionRender> sectionMap = new Long2ReferenceOpenHashMap<>();

	private final BFSCuller bfsCuller = new BFSCuller();
	private static SectionManager INSTANCE;

	private final RegionManager regionManager = new RegionManager();
	private final Queue<SectionRender> updateList = new ArrayDeque<>(1024);

	private final BlockRenderer blockRenderer = new BlockRenderer();
	private World worldObj;

	private int renderDistance = -1;

	private double cameraX, cameraY, cameraZ;
	private double lastUpdateX, lastUpdateZ;

	public int drawnSolidRenderers = 0;

	private ShaderSectionTerrain terrainShader;

	private long lastPositionCache = -1;
	private SectionRender lastSectionCache;

	private long vramAllocated;

	public SectionManager(World world) {
		INSTANCE = this;

		this.bfsCuller.setRenderingLists(this.regionManager, this.updateList);
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
			sectionRender.clearRenderer();
		}
	}

	public void addMemory(int vertices, int stride) {
		this.vramAllocated += (long) vertices * stride;
	}

	public void removeMemory(int vertices, int stride) {
		this.vramAllocated -= (long) vertices * stride;
	}

	public long getMemory() {
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

		sectionRender.dirty = true;
	}

	public SectionRender addRender(int posX, int posY, int posZ, boolean trulyNew) {
		long position = asLong(posX, posY, posZ);

		SectionRender sectionRender = trulyNew ? null : this.sectionMap.getOrDefault(position, null);

		if (sectionRender == null) {
			sectionRender = new SectionRender(posX * 16, posY * 16, posZ * 16);

			this.sectionMap.put(position, sectionRender);
			this.connectNeighbors(sectionRender);
		}

		sectionRender.dirty = true;
		return sectionRender;
	}

	public void update(int renderDistance, double cameraX, double cameraY, double cameraZ, boolean worldChanged) {
		this.cameraX = cameraX;
		this.cameraY = cameraY;
		this.cameraZ = cameraZ;

		if (this.renderDistance != renderDistance || worldChanged) {
			this.renderDistance = renderDistance;
			this.lastUpdateX = cameraX;
			this.lastUpdateZ = cameraZ;

			this.clearRenderer();
			this.generateWholeVolume(cameraX, cameraZ);

			return;
		}

		double diffX = Mth.square(cameraX - this.lastUpdateX);
		double diffZ = Mth.square(cameraZ - this.lastUpdateZ);

		if (diffX + diffZ >= Mth.square(4.0)) {
			this.generateSections();
		}

		this.bfsCuller.init(Mth.square(GL11.glGetFloat(GL11.GL_FOG_END)));
		this.bfsCuller.updateRenderList(this.sectionMap, (float) cameraX, (float) cameraY, (float) cameraZ);

		this.queueRebuilds();
	}

	// TODO: Implement off-thread chunk updates.
	private void queueRebuilds() {
		GlobalFlags.MESHING = true;

		SectionRender render;
		while ((render = this.updateList.poll()) != null) {
			render.rebuild(this, this.blockRenderer, this.worldObj);
		}

		GlobalFlags.MESHING = false;
	}

	private void generateSections() {
		int lastChunkCameraX = Math.floorDiv((int) this.lastUpdateX, 16);
		int lastChunkCameraZ = Math.floorDiv((int) this.lastUpdateZ, 16);

		int currentCameraX = Math.floorDiv((int) this.cameraX, 16);
		int currentCameraZ = Math.floorDiv((int) this.cameraZ, 16);

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
					this.lastUpdateX = this.cameraX;

					for (int y = 0; y < 16; y++) {
						this.addRender(currentCameraX + x, y, currentCameraZ + z, false);
					}
				}
				if (newZ <= -this.renderDistance || newZ >= this.renderDistance) {
					this.lastUpdateZ = this.cameraZ;

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
		ReferenceCollection<SectionRender> sectionRenders = this.sectionMap.values();

		for (SectionRender render : sectionRenders) {
			render.clearRenderer();
		}

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
		this.terrainShader.setupUniforms((float) this.cameraX, (float) this.cameraY, (float) this.cameraZ, noFog);

		this.regionManager.drawAllRegions(renderPass);
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
