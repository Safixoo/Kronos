package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.util.MathExt;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;
import net.minecraft.tileentity.TileEntity;

// Container of essential data for each section of the renderer, is mostly used as a convenient
// carriage of section data, besides that is only used in meshing as it is avoided in all hot-spots
// such as culling or region draw setup.
public class SectionRender {
	// Some important section data as a bit-mask from SectionFlag encoding.
	public int flags = SectionFlags.setDirty(0b0, true) |
		SectionFlags.setCullFaces(0b0, 0b111_111);

	// Section position relative to blocks.
	public int blockX, blockY, blockZ;

	// Section position relative to the region.
	public int regionIndex;

	// Global position in the world.
	public long globalPosition;

	// Section main data structures.
	public RegionRender region = RegionRender.NULL;

	// Tile entities from the section.
	public ReferenceList<TileEntity> tileEntities;

	public SectionRender(int blockX, int blockY, int blockZ) {
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;

		this.globalPosition = MathExt.asLong(blockX >> 4, blockY >> 4, blockZ >> 4);
		this.regionIndex = RegionRender.regionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);

		SectionManager.getCurrentInstance().queueFlagChange(this);
	}

	public void markDirty(boolean state) {
		this.setFlags(SectionFlags.setDirty(this.flags, state));
	}

	public boolean isDirty() {
		return SectionFlags.isDirty(this.flags);
	}

	public void setFlags(int flags) {
		this.flags = flags;
		SectionManager.getCurrentInstance().queueFlagChange(this);
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
		this.region = RegionRender.NULL;

		if (region == RegionRender.NULL) {
			return;
		}

		this.region.activeSections--;
		region.deleteRenderAllocation(this);
	}
}
