package dev.safixo.client.render.pipelines.terrain.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.*;
import org.lwjgl.opengl.*;
import dev.safixo.client.render.pipelines.terrain.SectionManager;
import dev.safixo.client.render.pipelines.terrain.shader.TerrainProgram;
import dev.safixo.client.render.pipelines.terrain.cull.BFSQueue;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.MathExt;

public class RegionManager {
	public static boolean SUPPORT_INDIRECT = true;

	public final Long2ReferenceOpenHashMap<RegionRender> regionMap = new Long2ReferenceOpenHashMap<>();
	private double lastUpdateX;
	private double lastUpdateZ;

	public RegionManager() {
		// TODO: Disable indirect drawing in Intel if it destroys performance, with my draw batching approach
		//  it shouldn't suffer so much in theory.
		// String vendor = GL11.glGetString(GL11.GL_VENDOR);
		SUPPORT_INDIRECT = GLContext.getCapabilities().GL_ARB_multi_draw_indirect; // && !vendor.contains("Intel");
	}

	public RegionRender getRegion(int sectionX, int sectionY, int sectionZ) {
		int regionX = sectionX >> (RegionRender.BLOCK_SHIFT_X - 4);
		int regionY = sectionY >> (RegionRender.BLOCK_SHIFT_Y - 4);
		int regionZ = sectionZ >> (RegionRender.BLOCK_SHIFT_Z - 4);

		long position = MathExt.asLong(regionX, regionY, regionZ);
		RegionRender region = this.regionMap.get(position);

		if (region == null) {
			region = new RegionRender(sectionX, sectionY, sectionZ);
			this.regionMap.put(position, region);
		}

		return region;
	}

	public void clear() {
		for (RegionRender region : this.regionMap.values()) {
			region.clear();
		}

		if (RegionAllocation.SPARE_BUFFER != null) {
			RegionAllocation.SPARE_BUFFER.delete();
			RegionAllocation.SPARE_BUFFER = null;
		}

		this.regionMap.clear();
	}

	public void update(CameraData camera, int renderDistance, boolean worldUpdated) {
		if (worldUpdated) {
			this.lastUpdateX = camera.cameraXD();
			this.lastUpdateZ = camera.cameraZD();

			this.clear();
			return;
		}

		double diffX = Math.abs(camera.cameraXD() - this.lastUpdateX);
		double diffZ = Math.abs(camera.cameraZD() - this.lastUpdateZ);

		if (diffX + diffZ >= 80) {
			this.lastUpdateX = camera.cameraXD();
			this.lastUpdateZ = camera.cameraZD();

			this.sanitizeRegions(camera, renderDistance);
		}
	}

	public void sanitizeRegions(CameraData camera, int renderDistance) {
		ReferenceCollection<RegionRender> regions = this.regionMap.values();
		int renderDistanceBlocks = (renderDistance + 1) * 16;

		LongArrayList removedList = new LongArrayList();

		for (RegionRender region : regions) {
			if (MathExt.manhattanDistance(region, camera) > MathExt.square(renderDistanceBlocks)) {
				long regionPos = MathExt.asLong(region.regionX, region.regionY, region.regionZ);

				removedList.add(regionPos);
				region.clear();
			}
		}

		for (long position : removedList) {
			this.regionMap.remove(position);
		}
	}

	public void drawAllRegions(TerrainProgram shader, BFSQueue queue, CameraData camera, int pass) {
		RegionRender[] regionRenders = queue.regionRenders;

		if (regionRenders == null) {
			return;
		}

		int index;
		int end;
		int inc;

		if (pass == 1) {
			index = queue.regionPos - 1;
			end = -1;
			inc = -1;
		} else {
			index = 0;
			end = queue.regionPos;
			inc = 1;
		}

		while (index != end) {
			RegionRender region = regionRenders[index];

			region.prepareAndDraw(shader, camera, pass);

			if (pass == 0) {
				SectionManager.getCurrentInstance().drawnSolidRenderers += region.sectionsToRender;
			}

			index += inc;
		}

		GL30.glBindVertexArray(0);
	}
 }
