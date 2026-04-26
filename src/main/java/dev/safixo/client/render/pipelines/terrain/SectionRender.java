package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.render.pipelines.terrain.cull.BFSCuller;
import dev.safixo.client.util.MathExt;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;
import dev.safixo.client.util.Direction;
import net.minecraft.tileentity.TileEntity;

// Saves basic info for each section from the world, is used mostly for culling and meshing,
// rendering is almost all managed in RegionRender with the least use of objects as possible.
public class SectionRender {
	// Some important section data as a bit-mask from SectionFlag encoding.
	public int flags = SectionFlags.setDirty(0b0, true) |
		SectionFlags.setCullFaces(0b0, 0b111_111);

	// Index of the frame in which the section was last visited by the BFSCuller, a mismatch
	// with the lastest index means that is yet not visible in this frame.
	public int currentFrame;

	// Section position relative to blocks.
	public int blockX, blockY, blockZ;

	// Section position relative to the region.
	public int regionIndex;

	// Adjacent nodes relative to the current, some may be null if they don't exist yet.
	public SectionRender adjacentDown, adjacentUp, adjacentNorth,
                        adjacentSouth, adjacentWest, adjacentEast;

	// Section main data structures.
	public RegionRender region = RegionRender.NULL;

	// Saves the factor of visibility in a 3D BFS grid, using the concept from Quick And Clear Look at Grid-Based Visibility article.
	public int gridFactor = BFSCuller.MAX_PRECISION;

	// Tile entities from the section.
	public final ReferenceList<TileEntity> tileEntities = new ReferenceArrayList<>();

	public long globalSectionPos;

	public SectionRender(int blockX, int blockY, int blockZ) {
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;

		this.globalSectionPos = MathExt.asLong(blockX >> 4, blockY >> 4, blockZ >> 4);
		this.regionIndex = RegionRender.regionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
	}

	public void markDirty(boolean state) {
		this.flags = SectionFlags.setDirty(this.flags, state);
	}

	public boolean isDirty() {
		return SectionFlags.isDirty(this.flags);
	}

	public void setAdjacentNeighbor(SectionRender render, int direction) {
		int adjacentMask = SectionFlags.getAdjacentMask(this.flags);

		if (render == null) {
			adjacentMask &= ~(1 << direction);
		} else {
			adjacentMask |= (1 << direction);
		}

		this.flags = SectionFlags.setAdjacentMask(this.flags, adjacentMask);

		if (direction == Direction.DOWN) {
			this.adjacentDown = render;
		} else if (direction == Direction.UP) {
			this.adjacentUp = render;
		} else if (direction == Direction.WEST) {
			this.adjacentWest = render;
		} else if (direction == Direction.EAST) {
			this.adjacentEast = render;
		} else if (direction == Direction.NORTH) {
			this.adjacentNorth = render;
		} else {
			this.adjacentSouth = render;
		}
	}

	public SectionRender getAdjacent(int direction) {
		if (direction == Direction.DOWN) {
			return this.adjacentDown;
		} else if (direction == Direction.UP) {
			return this.adjacentUp;
		} else if (direction == Direction.WEST) {
			return this.adjacentWest;
		} else if (direction == Direction.EAST) {
			return this.adjacentEast;
		} else if (direction == Direction.NORTH) {
			return this.adjacentNorth;
		} else {
			return this.adjacentSouth;
		}
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
