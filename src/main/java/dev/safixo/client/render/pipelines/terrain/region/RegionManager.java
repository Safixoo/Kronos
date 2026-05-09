package dev.safixo.client.render.pipelines.terrain.region;

import dev.safixo.client.render.pipelines.terrain.region.allocation.NewRegionAllocator;
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
		// TODO: Test the performance of indirect draws in Intel drivers.
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
			region = new RegionRender(this, sectionX, sectionY, sectionZ);
			this.regionMap.put(position, region);
		}

		return region;
	}

	// Creates a mapping between position and regions, useful for BFS only.
	public RegionRender[] getIndexedRegions(CameraData camera) {
		int regionCameraX = camera.intX >> RegionRender.BLOCK_SHIFT_X;
		int regionCameraZ = camera.intZ >> RegionRender.BLOCK_SHIFT_Z;

		int renderDiameter = Math.min(3 << 3, camera.renderDistance * 2 + 1);

		int factorXZ = renderDiameter >> 2;
		int factorY = 256 >> RegionRender.BLOCK_SHIFT_Y;

		RegionRender[] indexedRegions = new RegionRender[MathExt.square(factorXZ * 2 + 1) * factorY];

		for (RegionRender region : this.regionMap.values()) {
			int regionX = region.regionX - regionCameraX + factorXZ;
			int regionY = region.regionY;
			int regionZ = region.regionZ - regionCameraZ + factorXZ;

			indexedRegions[regionX + (regionY + regionZ * factorY) * factorXZ] = region;
		}

		return indexedRegions;
	}

	public static RegionRender getRegionFromIndexed(RegionRender[] indexedRegions, int renderDiameter, int regionX, int regionY, int regionZ) {
		int factorXZ = renderDiameter >> 2;
		int factorY = 256 >> RegionRender.BLOCK_SHIFT_Y;

		regionX += factorXZ;
		regionZ += factorXZ;

		return indexedRegions[regionX + (regionY + regionZ * factorY) * factorXZ];
	}

	public void clear() {
		for (RegionRender region : this.regionMap.values()) {
			region.clear();
		}

		if (RegionAllocation.SPARE_BUFFER != null) {
			RegionAllocation.SPARE_BUFFER.delete();
			RegionAllocation.SPARE_BUFFER = null;
		}

		if (NewRegionAllocator.COPY_BUFFER != null) {
			NewRegionAllocator.COPY_BUFFER.delete();
			NewRegionAllocator.COPY_BUFFER = null;
		}

		this.regionMap.clear();
	}

	private RegionRender[] getRegionsSorted(CameraData camera) {
		if (this.regionMap.isEmpty()) {
			return null;
		}

		RegionRender[] regions = this.regionMap.values().toArray(new RegionRender[0]);
		int[] distances = new int[regions.length];
		int maxRegionDistance = Integer.MIN_VALUE;

		for (int i = 0; i < regions.length; i++) {
			maxRegionDistance = Math.max(maxRegionDistance, distances[i] = manhattanDistance(regions[i], camera));
		}
		maxRegionDistance += 1;

		int[] indices = new int[regions.length];
		int[] hist = new int[maxRegionDistance];

		for (int i = 0; i < regions.length; i++) {
			hist[distances[i]]++;
		}

		// turns histogram into a prefix-sum array.
		for (int i = 1; i < maxRegionDistance; i++) {
			hist[i] += hist[i - 1];
		}

		for (int i = 0; i < regions.length; i++) {
			indices[--hist[distances[i]]] = i;
		}

		RegionRender[] regionSorted = new RegionRender[regions.length];

		for (int i = 0; i < indices.length; i++) {
			regionSorted[i] = regions[indices[i]];
		}

		return regionSorted;
	}

	private static int manhattanDistance(RegionRender region, CameraData camera) {
		int pX = camera.intX >> RegionRender.BLOCK_SHIFT_X;
		int pY = camera.intY >> RegionRender.BLOCK_SHIFT_Y;
		int pZ = camera.intZ >> RegionRender.BLOCK_SHIFT_Z;

		int rX = region.regionX;
		int rY = region.regionY;
		int rZ = region.regionZ;

		return Math.abs(pX - rX) + Math.abs(pY - rY) + Math.abs(pZ - rZ);
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

		if (Math.max(diffX, diffZ) >= 16) {
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
			if (region.sectionsToRender == 0 && (MathExt.euclideanDistance(region, camera) > MathExt.square(renderDistanceBlocks) || region.activeSections == 0)) {
				long regionPos = MathExt.asLong(region.regionX, region.regionY, region.regionZ);

				removedList.add(regionPos);
				region.clear();
			}
		}

		for (long position : removedList) {
			this.regionMap.remove(position);
		}
	}

	public void drawAllRegions(SectionManager manager, TerrainProgram shader, CameraData camera, int pass) {
		RegionRender[] regionRenders = this.getRegionsSorted(camera);

		if (regionRenders == null) {
			return;
		}

		int index;
		int end;
		int inc;

		if (pass == 1) {
			index = regionRenders.length - 1;
			end = -1;
			inc = -1;
		} else {
			index = 0;
			end = regionRenders.length;
			inc = 1;
		}

		while (index != end) {
			RegionRender region = regionRenders[index];

			region.prepareAndDraw(manager, shader, camera, pass);

			if (pass == 0) {
				manager.drawnSolidRenderers += region.sectionsToRender;
			}

			index += inc;
		}

		GL30.glBindVertexArray(0);
	}
 }
