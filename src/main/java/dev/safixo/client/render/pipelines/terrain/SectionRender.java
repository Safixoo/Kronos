package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.render.pipelines.terrain.region.RegionConstants;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;

public class SectionRender {
	private final SectionSet sectionSet;

	// Some important section data as a bit-mask from SectionFlag encoding.
	public int flags;

	// Section position relative to blocks.
	public int blockX, blockY, blockZ;

	// Section position relative to the region.
	public int regionIndex;

	// Global position in the world.
	public long globalPosition;

	// Section main data structures.
	public RegionRender region = RegionConstants.NULL;

	public SectionRender(WorldManager manager, SectionSet sectionSet, int blockX, int blockY, int blockZ, int flags) {
		this.sectionSet = sectionSet;
		this.flags = flags;

		this.setup(manager, blockX, blockY, blockZ);
	}

	public void setup(WorldManager manager, int blockX, int blockY, int blockZ) {
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;

		this.globalPosition = MathExt.asLong(blockX >> 4, blockY >> 4, blockZ >> 4);
		this.regionIndex = RegionRender.regionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);

		if (SectionFlags.hasPassesNonEmpty(this.flags)) {
			this.region = manager.getRegion(blockX >> 4, blockY >> 4, blockZ >> 4);
		}
	}

	public boolean isDirty() {
		return SectionFlags.isDirty(this.flags);
	}

	public void setFlags(int flags) {
		this.flags = flags;
	}

	public void sendFlagsToSet() {
		this.sectionSet.setSectionInfo(this.blockX >> 4, this.blockY >> 4, this.blockZ >> 4, this.flags);
	}
}
