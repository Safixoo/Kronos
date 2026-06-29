package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.render.pipelines.terrain.region.RegionConstants;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;


public class SectionRender {
	private static final int DEFAULT_FLAGS = SectionFlags.setCullFaces(0b0, 0b111_111);

	private final SectionSet sectionSet;

	// Some important section data as a bit-mask from SectionFlag encoding.
	public int flags = DEFAULT_FLAGS;

	// Section position relative to blocks.
	public int blockX, blockY, blockZ;

	// Section position relative to the region.
	public int regionIndex;

	// Global position in the world.
	public long globalPosition;

	// Section main data structures.
	public RegionRender region = RegionConstants.NULL;

	// To avoid instancing too much sections, the ones no used are marked as invalid, and re-used.
	private boolean valid;

	public SectionRender(SectionSet sectionSet, int blockX, int blockY, int blockZ) {
		this.sectionSet = sectionSet;
		this.valid = true;
		this.setup(blockX, blockY, blockZ);
	}

	public SectionRender(SectionSet sectionSet) {
		this.sectionSet = sectionSet;
		this.valid = false;
	}

	public static SectionRender invalidInstance(SectionSet sectionSet) {
		return new SectionRender(sectionSet);
	}

	public void setup(int blockX, int blockY, int blockZ) {
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;

		this.globalPosition = MathExt.asLong(blockX >> 4, blockY >> 4, blockZ >> 4);
		this.regionIndex = RegionRender.regionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
	}

	public void invalidate() {
		if (this.valid) {
			this.clearAllocations();
			this.flags = DEFAULT_FLAGS;
		}
		this.valid = false;
	}

	public void revalidate(int blockX, int blockY, int blockZ) {
		if (this.valid) {
			throw new RuntimeException("Tried to re-validate a valid SectionRender!");
		}
		this.valid = true;
		this.setup(blockX, blockY, blockZ);
	}

	public void markDirty(boolean state) {
		this.setFlags(SectionFlags.setDirty(this.flags, state));
	}

	public boolean isDirty() {
		return SectionFlags.isDirty(this.flags);
	}

	public boolean isInvalid() {
		return !this.valid;
	}

	public void setFlags(int flags) {
		this.flags = flags;
		this.sendFlagsToSet();
	}

	public void setAdjacentNeighbor(SectionRender render, int direction) {
		int adjacentMask = SectionFlags.getAdjacentMask(this.flags);

		if (render == null) {
			adjacentMask &= ~(1 << direction);
		} else {
			adjacentMask |= (1 << direction);
		}

		this.setFlags(SectionFlags.setAdjacentMask(this.flags, adjacentMask));
	}

	public void clearAllocations() {
		RegionRender region = this.region;
		this.region = RegionConstants.NULL;

		if (region != RegionConstants.NULL) {
			region.deleteRenderAllocation(this);
		}
	}

	public void sendFlagsToSet() {
		this.sectionSet.queueFlagForSet(this.globalPosition, this.flags);
	}
}
