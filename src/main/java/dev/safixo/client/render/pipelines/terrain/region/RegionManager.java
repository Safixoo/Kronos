package dev.safixo.client.render.pipelines.terrain.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.*;
import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.render.pipelines.terrain.shader.TerrainProgram;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.MathExt;

import java.util.List;

public class RegionManager {
	public static boolean SUPPORT_INDIRECT;

	public final Long2ReferenceOpenHashMap<RegionRender> regionMap = new Long2ReferenceOpenHashMap<>();

	private double lastUpdateX;
	private double lastUpdateZ;

	public RegionManager() {
		String vendor = GL11.glGetString(GL11.GL_VENDOR);
		SUPPORT_INDIRECT = GLContext.getCapabilities().GL_ARB_multi_draw_indirect && !vendor.contains("Intel");
	}

	public RegionRender getRegion(int sectionX, int sectionY, int sectionZ) {
		int regionX = sectionX >> (RegionConstants.BLOCK_SHIFT_X - 4);
		int regionY = sectionY >> (RegionConstants.BLOCK_SHIFT_Y - 4);
		int regionZ = sectionZ >> (RegionConstants.BLOCK_SHIFT_Z - 4);

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
		int regionCameraX = camera.intX >> RegionConstants.BLOCK_SHIFT_X;
		int regionCameraZ = camera.intZ >> RegionConstants.BLOCK_SHIFT_Z;

		int renderDiameter = camera.renderDistance * 2 + 1 + (2 << 3);

		int factorXZ = renderDiameter >> 3;
		int factorY = 256 >> RegionConstants.BLOCK_SHIFT_Y;

		RegionRender[] indexedRegions = new RegionRender[MathExt.square(factorXZ * 2 + 1) * factorY];

		for (RegionRender region : this.regionMap.values()) {
			int regionX = region.regionX - regionCameraX + factorXZ;
			int regionY = region.regionY;
			int regionZ = region.regionZ - regionCameraZ + factorXZ;

			// Region out-of-bounds.
			if (regionX < 0 || regionX > factorXZ * 2 || regionZ < 0 || regionZ > factorXZ * 2) {
				continue;
			}

			indexedRegions[regionX + (regionY + regionZ * factorY) * factorXZ] = region;
		}

		return indexedRegions;
	}

	public static RegionRender getRegionFromIndexed(RegionRender[] indexedRegions, int renderDiameter, int regionX, int regionY, int regionZ) {
		int factorXZ = renderDiameter >> 3;
		int factorY = 256 >> RegionConstants.BLOCK_SHIFT_Y;

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
		int pX = camera.intX >> RegionConstants.BLOCK_SHIFT_X;
		int pY = camera.intY >> RegionConstants.BLOCK_SHIFT_Y;
		int pZ = camera.intZ >> RegionConstants.BLOCK_SHIFT_Z;

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

	public void iterateAllTileEntities(List<TileEntity> globalList) {
		globalList.clear();

		for (RegionRender region : this.regionMap.values()) {
			if (!region.hasTileEntities()) {
				continue;
			}

			region.getTileEntityManager().iterateTileEntities(globalList);
		}
	}

	public void sanitizeRegions(CameraData camera, int renderDistance) {
		LongArrayList forRemoval = new LongArrayList();
		int distanceSquared = MathExt.square((renderDistance + 3) << 4);

		for (RegionRender region : this.regionMap.values()) {
			if (nearestDistanceToRegion(camera, region) > distanceSquared && region.getRenderIndex() == 0) {
				long regionPos = MathExt.asLong(region.regionX, region.regionY, region.regionZ);

				forRemoval.add(regionPos);
				region.clear();
			}
		}

		for (long position : forRemoval) {
			this.regionMap.remove(position);
		}
	}

	private static int nearestDistanceToRegion(CameraData camera, RegionRender region) {
		int minX = (region.regionX << RegionConstants.BLOCK_SHIFT_X) - camera.intX;
		int minZ = (region.regionZ << RegionConstants.BLOCK_SHIFT_Z) - camera.intZ;

		int maxX = minX + RegionConstants.DIAMETER_X;
		int maxZ = minZ + RegionConstants.DIAMETER_Z;

		int diffX = Math.min(maxX, Math.max(0, minX));
		int diffZ = Math.min(maxZ, Math.max(0, minZ));

		return MathExt.square(diffX) + MathExt.square(diffZ);
	}

	public void drawAllRegions(WorldManager manager, TerrainProgram shader, CameraData camera, int pass) {
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
				manager.drawnSolidRenderers += region.getRenderIndex();
			}

			index += inc;
		}

		GL30.glBindVertexArray(0);
	}
 }
