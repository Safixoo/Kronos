package turniplabs.examplemod.client.render.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import org.lwjgl.opengl.GL30;
import turniplabs.examplemod.client.render.SectionManager;

public class RegionManager {
	public final Long2ReferenceOpenHashMap<RegionRender> regionMap = new Long2ReferenceOpenHashMap<>();
	private final ReferenceList<RegionRender> regionRenders = new ReferenceArrayList<>();

	private long lastPosition = -1;
	private RegionRender lastRegion;

	public RegionRender getRegion(int sectionX, int sectionY, int sectionZ) {
		long position = SectionManager.asLong(sectionX >> 2, sectionY >> 2, sectionZ >> 2);
		RegionRender region;

		if (position == this.lastPosition) {
			region = this.lastRegion;
		} else {
			this.lastPosition = position;
			region = this.lastRegion = this.regionMap.getOrDefault(position, null);
		}

		if (region == null) {
			region = new RegionRender(this, sectionX, sectionY, sectionZ);
			this.regionMap.put(position, region);
		}

		return region;
	}

	public void addToDrawQueue(RegionRender render) {
		this.regionRenders.add(render);
	}

	public void update(boolean worldUpdated) {
		ReferenceCollection<RegionRender> regions = this.regionMap.values();

		if (worldUpdated) {
			for (RegionRender region : regions) {
				region.clear();
			}
			if (RegionAllocation.spareBuffer != null) {
				RegionAllocation.spareBuffer.clear();
				RegionAllocation.spareBuffer = null;
			}

			this.regionMap.clear();
		}
	}

	public void drawAllRegions(int renderPass) {
		if (renderPass == 0) {
			// Draw solid.
			for (RegionRender region : this.regionRenders) {
				if (region.solidEmptyDraw != 0) {
					region.bindSolid();
					region.draw(region.solidFirst, region.solidCount, region.solidEmptyDraw);
					region.solidEmptyDraw = 0;
				}
			}
		}

		if (renderPass == 1) {
			// Draw translucent.
			for (RegionRender region : this.regionRenders) {
				if (region.translucentEmptyDraw != 0) {
					region.bindTranslucent();
					region.draw(region.translucentFirst, region.translucentCount, region.translucentEmptyDraw);
					region.translucentEmptyDraw = 0;
				}
			}

			// Assuming translucent is rendered last.
			this.regionRenders.clear();
		}

		GL30.glBindVertexArray(0);
	}
 }
