package turniplabs.examplemod.client.render;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceCollection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.world.World;
import org.lwjgl.opengl.GL11;
import turniplabs.examplemod.client.GlobalFlags;
import turniplabs.examplemod.client.render.cull.BFSCuller;
import turniplabs.examplemod.client.render.meshing.BlockRenderer;
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

	private final List<SectionRender> renderList = new ObjectArrayList<>();
	private final Queue<SectionRender> updateList = new ArrayDeque<>(1024);

	private final BlockRenderer blockRenderer = new BlockRenderer();
	private World worldObj;

	private int renderDistance = -1;

	private double cameraX, cameraY, cameraZ;
	private double lastUpdateX, lastUpdateZ;

	public SectionManager(World world) {
		INSTANCE = this;

		this.bfsCuller.setRenderingLists(this.renderList, this.updateList);
		this.worldObj = world;
	}

	public static SectionManager getCurrentInstance() {
		if (INSTANCE == null) {
			INSTANCE = new SectionManager(Minecraft.getMinecraft().currentWorld);
		}

		return INSTANCE;
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
		sectionRender.clearRenderer();
	}

	public void addRender(int posX, int posY, int posZ) {
		long position = asLong(posX, posY, posZ);

		SectionRender sectionRender = this.sectionMap.getOrDefault(position, null);

		if (sectionRender == null) {
			sectionRender = new SectionRender(posX, posY, posZ);
			sectionRender.dirty = true;

			this.sectionMap.put(position, sectionRender);
		}
	}

	public void update(int renderDistance, double cameraX, double cameraY, double cameraZ, boolean worldChanged) {
		if (this.renderDistance != renderDistance || worldChanged) {
			this.generateWholeVolume(cameraX, cameraZ);
			this.clearRenderer();
			return;
		}

		double diffX = Mth.square(cameraX - this.lastUpdateX);
		double diffZ = Mth.square(cameraZ - this.lastUpdateZ);

		if (diffX + diffZ >= Mth.square(4.0)) {
			this.generateSections();
			this.lastUpdateX = cameraX;
			this.lastUpdateZ = cameraZ;
		}

		this.cameraX = cameraX;
		this.cameraY = cameraY;
		this.cameraZ = cameraZ;
		this.renderDistance = renderDistance;

		this.bfsCuller.init(GL11.glGetFloat(GL11.GL_FOG_END));
		this.bfsCuller.updateRenderList(this.sectionMap, (float) cameraX, (float) cameraY, (float) cameraZ);
	}

	// TODO: Implement off-thread chunk updates.
	private void queueRebuilds() {
		GlobalFlags.MESHING = true;

		SectionRender render;
		while ((render = this.updateList.poll()) != null) {
			render.rebuild(this.blockRenderer, this.worldObj);
		}

		GlobalFlags.MESHING = false;
	}

	private void generateSections() {
		int lastChunkCameraX = (int) this.lastUpdateX >>> 4;
		int lastChunkCameraZ = (int) this.lastUpdateZ >>> 4;

		int currentCameraX = (int) this.cameraX >>> 4;
		int currentCameraZ = (int) this.cameraZ >>> 4;

		// If current > last the diff is the amount of chunks added from +X (or +Z) to -X
		// that would have to added, and if current < last, then the same but changed sign.
		int diffX = (currentCameraX - lastChunkCameraX);
		int diffZ = (currentCameraZ - lastChunkCameraZ);

		int signX = sign(diffX);
		int signZ = sign(diffZ);

		// Nothing has changed.
		if (signX == 0 && signZ == 0) {
			return;
		}

		// We scan all the render distance volume and if the diff between the last
		// camera pos summed the xz pos index of the render distance volume goes out
		// of bounds from the xz min-max index it means that it's a new or old section.
		for (int x = -this.renderDistance; x < this.renderDistance; x -= signX) {
			for (int z = -this.renderDistance; z < this.renderDistance; z -= signZ) {
				int newX = x + diffX;
				int newZ = z + diffZ;

				// Add new sections in distance.
				if (newX > this.renderDistance || newX < -this.renderDistance) {
					for (int y = 0; y < 16; y++) {
						this.addRender(newX + lastChunkCameraX, y, newZ + lastChunkCameraZ);
					}
				}
				if (newZ > this.renderDistance || newZ < -this.renderDistance) {
					for (int y = 0; y < 16; y++) {
						this.addRender(newX + lastChunkCameraX, y, newZ + lastChunkCameraZ);
					}
				}

				int oldX = x - diffX;
				int oldZ = x - diffX;

				// Remove out of render distance sections.
				if (oldX > this.renderDistance || oldX < -this.renderDistance) {
					for (int y = 0; y < 16; y++) {
						this.removeRender(newX + lastChunkCameraX, y, newZ + lastChunkCameraZ);
					}
				}
				if (oldZ > this.renderDistance || oldZ < -this.renderDistance) {
					for (int y = 0; y < 16; y++) {
						this.addRender(newX + lastChunkCameraX, y, newZ + lastChunkCameraZ);
					}
				}
			}
		}
	}

	private void generateWholeVolume(double cameraX, double cameraZ) {
		int cameraChunkX = (int) cameraX >>> 4;
		int cameraChunkZ = (int) cameraZ >>> 4;

		for (int x = -this.renderDistance; x < this.renderDistance; x++) {
			for (int z = -this.renderDistance; z < this.renderDistance; z++) {
				for (int y = 0; y < 16; y++) {
					this.addRender(cameraChunkX + x , y, cameraChunkZ + z);
				}
			}
		}
	}

	private static int sign(int num) {
		return (num >> 31) | 1;
	}

	private void clearRenderer() {
		ReferenceCollection<SectionRender> sectionRenders = this.sectionMap.values();

		for (SectionRender render : sectionRenders) {
			render.clearRenderer();
		}

		this.sectionMap.clear();
	}

	public void blockUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {


	}

	public void queueSectionUpdates() {
		GlobalFlags.MESHING = true;

		for (SectionRender render : this.updateList) {
			render.rebuild(this.blockRenderer, this.worldObj);
		}

		GlobalFlags.MESHING = false;
	}

	public void drawRenderPass(int renderPass) {

	}

	public void connectNeighbors(SectionRender render) {
		for (int dir = 0; dir < Direction.COUNT; dir++) {
			SectionRender renderer = this.getSection(render, dir);

			if (renderer != null) {
				renderer.setAdjacentNeighbor(render, Direction.opposite(dir));
			}

			render.setAdjacentNeighbor(renderer, dir);
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
		int chunkX = (section.posX >> 4) + Direction.x(direction);
		int chunkY = (section.posY >> 4) + Direction.y(direction);
		int chunkZ = (section.posZ >> 4) + Direction.z(direction);

		return this.sectionMap.get(asLong(chunkX, chunkY, chunkZ));
	}

}
