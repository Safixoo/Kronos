package turniplabs.examplemod.client.render.region;


import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import org.lwjgl.opengl.GL30;
import turniplabs.examplemod.client.render.SectionManager;
import turniplabs.examplemod.client.render.SectionRender;

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
		}

		return region;
	}

	public void addToDrawQueue(RegionRender render) {
		this.regionRenders.add(render);
	}

	public void drawAllRegions() {
		// Draw solid.
		for (RegionRender region : this.regionRenders) {
			if (region.solidFirst.isEmpty()) {
				continue;
			}

			region.bindSolid();
			region.draw(region.solidFirst, region.solidCount);
		}

		// Draw translucent.
		for (RegionRender region : this.regionRenders) {
			if (region.translucentFirst.isEmpty()) {
				continue;
			}

			region.bindTranslucent();
			region.draw(region.translucentFirst, region.translucentCount);
		}

		GL30.glBindVertexArray(0);
		this.regionRenders.clear();
	}
 }
