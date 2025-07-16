package turniplabs.examplemod.client.render.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import org.lwjgl.opengl.GL30;
import turniplabs.examplemod.client.render.SectionManager;
import turniplabs.examplemod.client.render.data.CameraData;
import turniplabs.examplemod.client.util.Mth;

public class RegionManager {
	public final Long2ReferenceOpenHashMap<RegionRender> regionMap = new Long2ReferenceOpenHashMap<>();

	private long lastPosition = -1;
	private RegionRender lastRegion;

	private double lastUpdateX;
	private double lastUpdateZ;

	public RegionRender getRegion(int sectionX, int sectionY, int sectionZ) {
		long position = SectionManager.asLong(sectionX >> 3, sectionY >> 2, sectionZ >> 3);
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

		double diffX = Mth.square(camera.cameraX() - this.lastUpdateX);
		double diffZ = Mth.square(camera.cameraZ() - this.lastUpdateZ);

		if (diffX + diffZ >= 128) {
			this.sanitizeRegions(camera);
		}

	}

	public void sanitizeRegions(CameraData camera) {

	}

	public void drawAllRegions(CameraData camera, int pass) {
		ReferenceCollection<RegionRender> aliveRegions = this.regionMap.values();

		for (RegionRender region : aliveRegions) {
			if (region.sectionsToRender == 0) {
				continue;
			}

			region.prepareAndDraw(camera, pass);

			if (pass == 0) {
				SectionManager.getCurrentInstance().drawnSolidRenderers += region.sectionsToRender;
			}
		}

		GL30.glBindVertexArray(0);
	}
 }
