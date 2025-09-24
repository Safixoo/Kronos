package turniplabs.examplemod.client.render.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.*;
import org.lwjgl.opengl.GL30;
import turniplabs.examplemod.client.render.SectionManager;
import turniplabs.examplemod.client.render.ShaderSectionTerrain;
import turniplabs.examplemod.client.render.cull.BFSQueue;
import turniplabs.examplemod.client.render.data.CameraData;
import turniplabs.examplemod.client.util.MathExt;

public class RegionManager {
	public final Long2ReferenceOpenHashMap<RegionRender> regionMap = new Long2ReferenceOpenHashMap<>();

	private double lastUpdateX;
	private double lastUpdateZ;

	public RegionRender getRegion(int sectionX, int sectionY, int sectionZ) {
		int regionX = sectionX >> (RegionRender.BLOCK_SHIFT_X - 4);
		int regionY = sectionY >> (RegionRender.BLOCK_SHIFT_Y - 4);
		int regionZ = sectionZ >> (RegionRender.BLOCK_SHIFT_Z - 4);

		long position = SectionManager.asLong(regionX, regionY, regionZ);
		RegionRender region = this.regionMap.getOrDefault(position, null);

		if (region == null) {
			region = new RegionRender(sectionX, sectionY, sectionZ);
			this.regionMap.put(position, region);
		}

		return region;
	}

	public void update(CameraData camera, int renderDistance, boolean worldUpdated) {
		ReferenceCollection<RegionRender> regions = this.regionMap.values();

		if (worldUpdated) {
			for (RegionRender region : regions) {
				region.clear();
			}
			if (RegionAllocation.SPARE_BUFFER != null) {
				RegionAllocation.SPARE_BUFFER.clear();
				RegionAllocation.SPARE_BUFFER = null;
			}

			this.regionMap.clear();
		}

		double diffX = Math.abs(camera.cameraXD() - this.lastUpdateX);
		double diffZ = Math.abs(camera.cameraZD() - this.lastUpdateZ);

		if (diffX + diffZ >= 128) {
			this.lastUpdateX = camera.cameraXD();
			this.lastUpdateZ = camera.cameraZD();

			// TODO: Fix crashes drawing because of region removal.
			//this.sanitizeRegions(camera);
		}

	}

	public void sanitizeRegions(CameraData camera) {
		ReferenceCollection<RegionRender> regions = this.regionMap.values();

		int maxRadius = Math.max(RegionRender.RADIUS_Z, RegionRender.RADIUS_X);
		int maxDistance = MathExt.square((camera.renderDistance << 4) + maxRadius + 64);
		LongArrayList toRemoveList = new LongArrayList();

		for (RegionRender region : regions) {
			if (MathExt.squaredDistance(region, camera) > maxDistance) {
				toRemoveList.add(SectionManager.asLong(region.regionX, region.regionY, region.regionZ));
				region.clear();
			}
		}

		for (long position : toRemoveList) {
			this.regionMap.remove(position);
		}
	}

	public void drawAllRegions(ShaderSectionTerrain shader, BFSQueue queue, CameraData camera, int pass) {
		RegionRender[] regionRenders = queue.regionRenders;

		if (regionRenders == null) {
			return;
		}

		for (int i = 0; i < regionRenders.length; i++) {
			RegionRender region = regionRenders[i];

			if (region == null) {
				break;
			}

			region.prepareAndDraw(shader, camera, pass);

			if (pass == 0) {
				SectionManager.getCurrentInstance().drawnSolidRenderers += region.sectionsToRender;
			}
		}

		GL30.glBindVertexArray(0);
	}
 }
