package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.render.pipelines.terrain.cull.BFSCuller;
import dev.safixo.client.util.MathExt;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;
import dev.safixo.client.util.Direction;
import net.minecraft.tileentity.TileEntity;

// Saves basic info for each section from the world, is used mostly for culling and
// meshing, rendering is almost only managed in the RegionRender in an objectless fashion.
public class SectionRender {
	// Most of the section data, flags is a bit-mask from SectionFlag encoding.
	public int currentFrame, flags = SectionFlags.setDirty(0b0, true) |
									SectionFlags.setCullFaces(0b0, 0b111_111);

	// Section position relative to blocks.
	public int blockX, blockY, blockZ;

	public int regionIndex;
	public long sectionPos;

	// Adjacent nodes that searched during BFS culling, done like this
	// to avoid array dereferences and checks, inspired from Sodium.
	public SectionRender adjacentDown, adjacentUp, adjacentNorth,
                        adjacentSouth, adjacentWest, adjacentEast;

	// Section main data structures.
	public RegionRender region = RegionRender.NULL;

	// Used in BFS for the grid based visibility technique.
	public int gridInd = BFSCuller.MAX_PRECISION;

	// Tile entities from the section.
	public final ReferenceList<TileEntity> tileEntities = new ReferenceArrayList<>();

	public SectionRender(int blockX, int blockY, int blockZ) {
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;

		this.sectionPos = MathExt.asLong(blockX >> 4, blockY >> 4, blockZ >> 4);
		this.regionIndex = RegionRender.regionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
	}

	public void markDirty(boolean state) {
		if (state) {
			this.currentFrame = Integer.MIN_VALUE;
		}

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

		region.deleteRenderAllocation(this);
	}
}
