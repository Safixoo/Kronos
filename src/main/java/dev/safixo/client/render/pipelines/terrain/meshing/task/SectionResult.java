package dev.safixo.client.render.pipelines.terrain.meshing.task;

import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.render.pipelines.terrain.region.RegionAllocation;
import dev.safixo.client.render.pipelines.terrain.region.RegionConstants;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.render.vertex.writers.TerrainFormat;
import dev.safixo.client.util.MeshDirection;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.memory.MemoryPool;
import dev.safixo.client.util.memory.MemoryReference;
import dev.safixo.client.util.memory.UnsafeUtil;
import net.minecraft.tileentity.TileEntity;

public class SectionResult {
	public final SectionTask task;

	private final TileEntity[] tileEntityArray;

	private final int meshOrder;
	private final int cullMask;
	private final int drawMask;
	private final long[] solidDrawData;

	private final VertexWriter solidWriter, translucentWriter;

	public SectionResult(MemoryPool pool, TileEntity[] tileEntityArray, VertexWriter[] writers, SectionTask task, int cullMask) {
		CameraData camera = task.camera;
		SectionRender section = task.section;

		int drawMask = setupDrawMask(writers);
		this.cullMask = cullMask;

		if ((drawMask & 0b1) != 0) {
			VertexWriter translucentWriter = task.getTranslucentWriter();
			MemoryReference translucentBuffer = pool.allocate(translucentWriter.getOffset());
			this.translucentWriter = translucentBuffer != null ? translucentWriter.copy(translucentBuffer) : translucentWriter.copy();
		} else {
			this.translucentWriter = null;
		}

		if ((drawMask & ~0b1) != 0) {
			int offsetSum = sumAllSolidOffsets(writers);

			MemoryReference solidBuffer = pool.allocate(offsetSum);
			VertexWriter joined;

			if (solidBuffer == null) {
				joined = new VertexWriter(offsetSum);
			} else {
				joined = new VertexWriter(solidBuffer);
			}

			int solidMask = RegionConstants.getSolidMask(drawMask);
			int visibleFaces = RegionConstants.getSectionVisibleFaces(camera.intX, camera.intY, camera.intZ, section.blockX, section.blockY, section.blockZ);
			int meshOrder = MeshDirection.getSortedMeshOrder(visibleFaces & solidMask);

			long[] drawData = this.joinAllSolidBuffers(writers, joined, meshOrder);

			this.meshOrder = meshOrder;
			this.solidDrawData = drawData;
			this.solidWriter = joined;
		} else {
			this.meshOrder = 0;
			this.solidDrawData = new long[MeshDirection.COUNT];
			this.solidWriter = null;
		}

		this.drawMask = drawMask;
		this.tileEntityArray = tileEntityArray;
		this.task = task;
	}

	private static int sumAllSolidOffsets(VertexWriter[] writers) {
		int sum = 0;

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			sum += writers[dir].getOffset();
		}

		return sum;
	}

	private static int setupDrawMask(VertexWriter[] writers) {
		VertexWriter translucentWriter = writers[MeshDirection.COUNT];
		int solidBits = 0b0;
		int translucentBit = 0b0;

		if (translucentWriter.getOffset() != 0) {
			translucentBit = 0b1;
		}

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			if (writers[dir].getOffset() != 0) {
				solidBits |= 1 << dir;
			}
		}
		return RegionConstants.getDrawMask(solidBits, translucentBit);
	}

	public int getCullMask() {
		return this.cullMask;
	}

	private long[] joinAllSolidBuffers(VertexWriter[] writers, VertexWriter joiner, int meshDrawOrder) {
		long[] drawData = new long[MeshDirection.COUNT];

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			int realMeshDir = MeshDirection.getByIndex(meshDrawOrder, dir);
			VertexWriter writer = writers[realMeshDir];

			int writerOffset = writer.getOffset();
			drawData[realMeshDir] = RegionAllocation.packDrawData(writerOffset / TerrainFormat.STRIDE, joiner.getOffset() / TerrainFormat.STRIDE);

			UnsafeUtil.memCopy(writer.getPtr(), joiner.getPtr() + joiner.getOffset(), writerOffset);
			joiner.offset += writerOffset;
		}

		joiner.vertices = joiner.offset / TerrainFormat.STRIDE;
		return drawData;
	}

	public TileEntity[] getTileEntities() {
		return this.tileEntityArray;
	}

	public VertexWriter getSolidWriter() {
		return this.solidWriter;
	}

	public VertexWriter getTranslucentWriter() {
		return this.translucentWriter;
	}

	public int getMeshOrder() {
		return this.meshOrder;
	}

	public int getDrawMask() {
		return this.drawMask;
	}

	public void delete() {
		if (this.translucentWriter != null) {
			this.translucentWriter.delete();
		}
		if (this.solidWriter != null) {
			this.solidWriter.delete();
		}
	}

	public long[] getDrawData() {
		return this.solidDrawData;
	}
}
