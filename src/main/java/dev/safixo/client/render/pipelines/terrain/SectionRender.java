package dev.safixo.client.render.pipelines.terrain;

import cpw.mods.fml.common.network.NetworkMod;
import dev.safixo.client.render.pipelines.terrain.region.RegionConstants;
import dev.safixo.client.util.MathExt;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;
import net.minecraft.tileentity.TileEntity;


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

	// Tile entities from the section.
	private ReferenceList<TileEntity> tileEntities;

	private boolean valid;

	public SectionRender(SectionSet sectionSet, int blockX, int blockY, int blockZ) {
		this.sectionSet = sectionSet;
		this.valid = true;
		this.setup(blockX, blockY, blockZ);
	}

	private void setup(int blockX, int blockY, int blockZ) {
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;

		this.globalPosition = MathExt.asLong(blockX >> 4, blockY >> 4, blockZ >> 4);
		this.regionIndex = RegionRender.regionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
	}

	public void invalidate() {
		if (this.valid) {
			this.clearAllocations();
			this.clearTileEntityList();
			this.flags = DEFAULT_FLAGS;
		} else {
			throw new RuntimeException("Tried to invalidate a already invalid SectionRender!");
		}

		this.valid = false;
	}

	public void revalidate(int blockX, int blockY, int blockZ) {
		if (this.valid) {
			throw new RuntimeException("Tried to re-validate a valid SectionRender!");
		}
		this.setup(blockX, blockY, blockZ);
	}

	public void markDirty(boolean state) {
		this.setFlags(SectionFlags.setDirty(this.flags, state));
	}

	public boolean isDirty() {
		return SectionFlags.isDirty(this.flags);
	}

	public void setFlags(int flags) {
		this.flags = flags;
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

	public void addTileEntity(TileEntity tileEntity) {
		if (this.tileEntities == null) {
			this.tileEntities = new ReferenceArrayList<>();
		}

		this.tileEntities.add(tileEntity);
	}

	public TileEntity[] getTileEntityArray() {
		return this.tileEntities == null ? null : this.tileEntities.toArray(new TileEntity[0]);
	}

	public void clearTileEntityList() {
		this.tileEntities = null;
	}
}
