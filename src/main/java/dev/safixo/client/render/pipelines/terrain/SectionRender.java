package dev.safixo.client.render.pipelines.terrain;

import dev.safixo.client.render.pipelines.terrain.meshing.task.SectionResult;
import dev.safixo.client.render.pipelines.terrain.region.RegionConstants;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;

import static dev.safixo.client.util.Direction.DOWN_BIT;
import static dev.safixo.client.util.Direction.UP_BIT;

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

	public void sendBuildResult(WorldManager manager, SectionResult result) {
		VertexWriter solidWriter = result.getSolidWriter();
		VertexWriter translucentWriter = result.getTranslucentWriter();

		boolean nonNullWriter = solidWriter != null || translucentWriter != null;

		this.uploadMeshesToRegion(manager, result);
		this.region.setDrawMask(this.regionIndex, result.getDrawMask());

		this.setFlags(SectionFlags.setSolidFaces(this.flags, result.getSolidMask() & getAdjacentMask(this.blockY >> 4)));
		this.setFlags(SectionFlags.setPassesNonEmpty(this.flags, nonNullWriter ? 1 : 0));

		result.delete();

		this.uploadAllTileEntities(manager, result);
		this.sendFlagsToSet();
	}

	public static int getAdjacentMask(int sectionY) {
		int adjacentMask = 0x3F;

		if (sectionY == 0) {
			adjacentMask &= ~DOWN_BIT;
		} else if (sectionY == 15) {
			adjacentMask &= ~UP_BIT;
		}

		return adjacentMask;
	}

	private void uploadMeshesToRegion(WorldManager manager, SectionResult result) {
		VertexWriter solidWriter = result.getSolidWriter();
		VertexWriter translucentWriter = result.getTranslucentWriter();

		if (solidWriter != null && solidWriter.getOffset() != 0) {
			if (this.region == RegionConstants.NULL) {
				this.region = manager.getRegion(this.blockX >> 4, this.blockY >> 4, this.blockZ >> 4);
			}

			this.region.setMeshOrder(this.regionIndex, result.getMeshOrder());
			this.region.addSolidMesh(this, solidWriter, result.getDrawData());
		}

		if (translucentWriter != null && translucentWriter.getOffset() != 0) {
			if (this.region == RegionConstants.NULL) {
				this.region = manager.getRegion(this.blockX >> 4, this.blockY >> 4, this.blockZ >> 4);
			}

			this.region.addTranslucentMesh(this, translucentWriter);
		}
	}

	private void uploadAllTileEntities(WorldManager manager, SectionResult result) {
		if (result.getTileEntities() == null) {
			if (this.region != RegionConstants.NULL) {
				this.region.getTileEntityManager().removeTileEntities(this.regionIndex);
			}
			return;
		}

		if (this.region == RegionConstants.NULL) {
			this.region = manager.getRegion(this.blockX >> 4, this.blockY >> 4, this.blockZ >> 4);
		}
		this.region.getTileEntityManager().addTileEntities(result.getTileEntities(), this.regionIndex);
	}
}
