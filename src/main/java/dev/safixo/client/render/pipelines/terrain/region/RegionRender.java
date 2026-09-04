package dev.safixo.client.render.pipelines.terrain.region;

import dev.safixo.client.render.gfx.util.RenderBuffer;
import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.render.pipelines.terrain.meshing.task.SectionResult;
import dev.safixo.client.render.pipelines.terrain.shader.TerrainProgram;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.util.MeshDirection;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.render.vertex.writers.TerrainFormat;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static dev.safixo.client.render.pipelines.terrain.region.RegionConstants.*;

public class RegionRender {
	protected static final int INDIRECT_STRUCT_SIZE = 16;
	private static final int INT_BYTES = 4;

	// Region coordinates in region space.
	public int regionX, regionY, regionZ;

	// The vertex-buffers and its arenas.
	private RegionAllocation translucentBuffer;
	private RegionAllocation solidBuffer;

	private final RegionManager regionManager;

	// Draw-data buffers for uploading, and the draw index.
	private long solidFirst = UnsafeUtil.NULL, solidCount = UnsafeUtil.NULL;
	private long translucentFirst = UnsafeUtil.NULL, translucentCount = UnsafeUtil.NULL;

	// struct SolidDrawData[REGION_SECTION_SIZE] {
	// 		uint first;
	//		uint count[MeshDirection.COUNT];
	// };
	private int[] solidDrawData;

	// struct TranslucentDrawData[REGION_SECTION_SIZE] {
	// 		uint first;
	//		uint count;
	// };
	private long[] translucentDrawData;

	// Each bit reference the visibility of the solid planes (2..8 bit) and
	// visibility of translucent pass (1 bit).
	private final byte[] drawDataMask = new byte[REGION_SECTION_SIZE];

	// Each time a section is queued for rendering, its region index is saved
	// in the drawIndex position of renderIndices, the top is signaled by drawInd.
	protected final short[] renderIndices = new short[REGION_SECTION_SIZE];

	// This is important as the Minecraft direction enum is layered in a way that makes it
	// fundamentally impossible batching draws without meshes meeting very specific criteria.
	private final int[] sortedMeshOffsets = new int[REGION_SECTION_SIZE];

	// Number of sections queued for rendering in the current frame.
	private int queuedSections;

	// Pointers for the indirect drawing commands.
	private long solidIndirectPtr = UnsafeUtil.NULL;
	private long translucentIndirectPtr = UnsafeUtil.NULL;

	// Memoize drawing context to check if the result of the last draw construction is still valid to cache.
	private final RegionDrawContext drawContext = new RegionDrawContext();

	private final RegionTileEntities tileEntities = new RegionTileEntities();

	protected RegionRender(RegionManager regionManager, int sectionX, int sectionY, int sectionZ) {
		this.regionX = sectionX >> (RegionConstants.BLOCK_SHIFT_X - 4);
		this.regionY = sectionY >> (RegionConstants.BLOCK_SHIFT_Y - 4);
		this.regionZ = sectionZ >> (RegionConstants.BLOCK_SHIFT_Z - 4);

		this.regionManager = regionManager;

		if (RegionManager.SUPPORT_INDIRECT) {
			this.solidIndirectPtr = NativeBuffer.nmemAlloc(SOLID_DRAWS * REGION_SECTION_SIZE * INDIRECT_STRUCT_SIZE);
			this.translucentIndirectPtr = NativeBuffer.nmemAlloc(TRANSLUCENT_DRAWS * REGION_SECTION_SIZE * INDIRECT_STRUCT_SIZE);
		}
	}

	public long getFirstPtr(int pass) {
		return pass != 0 ? this.translucentFirst : this.solidFirst;
	}

	public long getCountPtr(int pass) {
		return pass != 0 ? this.translucentCount : this.solidCount;
	}

	public RenderBuffer getRenderBuffer(int pass) {
		return pass != 0 ? this.translucentBuffer.vertexBuffer : this.solidBuffer.vertexBuffer;
	}

	public ByteBuffer getIndirectBuffer(int pass) {
		return NativeBuffer.wrap(pass == 0 ? this.solidIndirectPtr : this.translucentIndirectPtr);
	}

	public void setDrawMask(int sectionIndex, int mask) {
		this.drawDataMask[sectionIndex] = (byte) mask;
	}

	public void resetRenderIndex() {
		this.queuedSections = 0;
	}

	public void addToRenderList(int regionIndex) {
		this.renderIndices[this.queuedSections++] = (short) regionIndex;
	}

	public int getRenderIndex() {
		return this.queuedSections;
	}

	public RegionTileEntities getTileEntityManager() {
		return this.tileEntities;
	}

	public boolean hasTileEntities() {
		return this.tileEntities.hasTileEntities();
	}

	public void clear() {
		this.drawContext.invalidatePass(SOLID_PASS);
		this.drawContext.invalidatePass(TRANSLUCENT_PASS);

		if (this.solidDrawData != null){
			Arrays.fill(this.solidDrawData, 0);
		}
		if (this.translucentDrawData != null) {
			Arrays.fill(this.translucentDrawData, 0L);
		}

		if (this.solidBuffer != null) {
			this.solidBuffer.clear();
			this.solidBuffer = null;
		}
		if (this.translucentBuffer != null) {
			this.translucentBuffer.clear();
			this.translucentBuffer = null;
		}

		if (this.solidFirst != UnsafeUtil.NULL) {
			NativeBuffer.nmemFree(this.solidFirst);
			NativeBuffer.nmemFree(this.solidCount);
			this.solidFirst = this.solidCount = UnsafeUtil.NULL;
		}

		if (this.translucentFirst != UnsafeUtil.NULL) {
			NativeBuffer.nmemFree(this.translucentFirst);
			NativeBuffer.nmemFree(this.translucentCount);
			this.translucentFirst = this.translucentCount = UnsafeUtil.NULL;
		}

		if (this.solidIndirectPtr != UnsafeUtil.NULL) {
			NativeBuffer.nmemFree(this.solidIndirectPtr);
		}
		if (this.translucentIndirectPtr != UnsafeUtil.NULL) {
			NativeBuffer.nmemFree(this.translucentIndirectPtr);
		}
	}

	public void addSolidMesh(SectionRender render, VertexWriter manager, SectionResult buildResult) {
		this.drawContext.invalidatePass(SOLID_PASS);

		this.setMeshOrder(render.regionIndex, buildResult.getMeshOrder());
		long[] packedDrawData = buildResult.getDrawData();

		if (this.solidDrawData == null) {
			this.solidDrawData = new int[REGION_SECTION_SIZE * (MeshDirection.COUNT + 1)];
		}
		if (this.solidBuffer == null) {
			this.solidBuffer = new RegionAllocation(manager.getVertices() * TerrainFormat.STRIDE, RegionConstants.SOLID_PASS);
		}
		if (this.solidFirst == UnsafeUtil.NULL) {
			this.prepareSolidPtr();
		}

		long drawData = this.solidBuffer.allocate(render.globalPosition, manager.getNioPtr(), manager.getVertices());
		int sectionFirst = RegionAllocation.unpackFirst(drawData);

		int drawDataIndex = render.regionIndex * TOTAL_DRAWS;
		this.solidDrawData[drawDataIndex] = sectionFirst;

		// Instead of saving the draw data in Direction enum order, do it in the sorted order.
		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			int sortedDirection = MeshDirection.getByIndex(buildResult.getMeshOrder(), dir);

			int index = (drawDataIndex + 1) + dir;
			int count = RegionAllocation.unpackCount(packedDrawData[sortedDirection]);

			this.solidDrawData[index] = count;
		}
	}

	private void prepareSolidPtr() {
		this.solidFirst = NativeBuffer.nmemAlloc(REGION_SECTION_SIZE * INT_BYTES * SOLID_DRAWS);
		this.solidCount = NativeBuffer.nmemAlloc(REGION_SECTION_SIZE * INT_BYTES * SOLID_DRAWS);
	}

	public void addTranslucentMesh(SectionRender render, VertexWriter manager) {
		this.drawContext.invalidatePass(TRANSLUCENT_PASS);

		if (this.translucentDrawData == null) {
			this.translucentDrawData = new long[REGION_SECTION_SIZE];
		}
		if (this.translucentBuffer == null) {
			this.translucentBuffer = new RegionAllocation(manager.getVertices() * TerrainFormat.STRIDE, RegionConstants.TRANSLUCENT_PASS);
		}
		if (this.translucentFirst == UnsafeUtil.NULL) {
			this.prepareTranslucentPtr();
		}

		this.translucentDrawData[render.regionIndex] = this.translucentBuffer.allocate(render.globalPosition, manager.getNioPtr(), manager.getVertices());
	}

	private void prepareTranslucentPtr() {
		this.translucentFirst = NativeBuffer.nmemAlloc(REGION_SECTION_SIZE * INT_BYTES);
		this.translucentCount = NativeBuffer.nmemAlloc(REGION_SECTION_SIZE * INT_BYTES);
	}

	/*
	public void deleteRenderAllocation(long position) {
		long[] drawData = this.regionDrawData;

		int sectionX = MathExt.decodeX(position);
		int sectionY = MathExt.decodeY(position);
		int sectionZ = MathExt.decodeZ(position);

		int regionIndex = RegionConstants.regionIndex(sectionX, sectionY, sectionZ);

		int translucentDrawData = (regionIndex * TOTAL_DRAWS) + SOLID_DRAWS;
		int solidDrawData = (regionIndex * TOTAL_DRAWS);

		if (drawData[translucentDrawData] != 0L) {
			this.translucentBuffer.remove(position);
			drawData[translucentDrawData] = 0L;
		}

		if (this.solidBuffer != null) {
			this.solidBuffer.remove(position);
			for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
				drawData[solidDrawData + dir] = 0L;
			}
		}
	}
	 */

	// Processing draw data now and not in the BFS, allows decoupling the system and doing the extra
	// work between draw which doesn't pressure the driver immediately, also as we work in a "small"
	// and contiguous data-set we don't get penalized too much for pulling SectionRenders from memory.
	public void prepareAndDraw(WorldManager manager, TerrainProgram shader, CameraData camera, int pass) {
		if ((pass == 0 && this.solidFirst == UnsafeUtil.NULL) || (pass == 1 && this.translucentFirst == UnsafeUtil.NULL)) {
			return;
		}

		// Try to re-use the last draw command setup.
		if (!manager.hasGraphUpdated() || (this.drawContext.isPassCacheable(pass) && this.drawContext.mismatchAndCopy(this, camera, pass))) {
			this.drawContext.multiDrawData(this, camera, shader, pass);
			return;
		}

		final short[] renderIndices = this.renderIndices;
		final byte[] drawDataMask = this.drawDataMask;

		int index;
		int end;
		int inc;

		// Change iteration order based in current render-pass.
		if (pass == 1) {
			index = this.queuedSections - 1;
			end = -1;
			inc = -1;
		} else {
			index = 0;
			end = this.queuedSections;
			inc = 1;
		}

		int drawCount = 0;

		while (index != end) {
			int regionIndex = renderIndices[index];
			int drawMask = drawDataMask[regionIndex];

			drawCount = pass == 0
						  ? this.prepareSolidBatch(camera, regionIndex, getSolidMask(drawMask), drawCount)
						  : this.prepareTranslucentBatch(regionIndex, getTranslucentMask(drawMask), drawCount);

			index += inc;
		}

		this.drawContext.setDrawCount(pass, drawCount);
		this.drawContext.validatePass(pass);
		this.drawContext.multiDrawData(this, camera, shader, pass);
	}

	private int prepareSolidBatch(CameraData camera, int regionIndex, int solidMask, int drawCount) {
		if (solidMask == 0) {
			return drawCount;
		}

		int blockX = (getLocalSectionX(regionIndex) << 4) + (this.regionX << BLOCK_SHIFT_X);
		int blockY = (getLocalSectionY(regionIndex) << 4) + (this.regionY << BLOCK_SHIFT_Y);
		int blockZ = (getLocalSectionZ(regionIndex) << 4) + (this.regionZ << BLOCK_SHIFT_Z);

		int sortedMeshOffsets = this.sortedMeshOffsets[regionIndex];
		int visibleFaces = getSectionVisibleFaces(sortedMeshOffsets, camera.intX, camera.intY, camera.intZ, blockX, blockY, blockZ) & solidMask;

		if (visibleFaces == 0) {
			return drawCount;
		}

		int[] solidDrawData = this.solidDrawData;

		regionIndex *= 8;
		int batchedFirst = solidDrawData[regionIndex++];
		int batchedCount = 0;

		visibleFaces <<= 1;

		while (visibleFaces != 0) {
			int count = solidDrawData[regionIndex++];
			visibleFaces >>= 1;

			if ((visibleFaces & 1) == 0) {
				this.addCommandSolid(drawCount, batchedFirst, batchedCount);

				drawCount += -batchedCount >>> 31;
				batchedFirst += batchedCount + count;
				batchedCount = 0;
				continue;
			}

			batchedCount += count;
		}

		this.addCommandSolid(drawCount, batchedFirst, batchedCount);
		drawCount += -batchedCount >>> 31;

		return drawCount;
	}

	private void addCommandSolid(int drawCount, int first, int count) {
		if (RegionManager.SUPPORT_INDIRECT) {
			addIndirectCommand(this.solidIndirectPtr, drawCount, first, count);
		} else {
			addDirectCommand(this.solidFirst, this.solidCount, drawCount, first, count);
		}
	}

	private void addCommandTranslucent(int drawCount, int first, int count) {
		if (RegionManager.SUPPORT_INDIRECT) {
			addIndirectCommand(this.translucentIndirectPtr, drawCount, first, count);
		} else {
			addDirectCommand(this.translucentFirst, this.translucentCount, drawCount, first, count);
		}
	}

	@SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
	private static void addDirectCommand(long firstPtr, long countPtr, int drawCount, int first, int count) {
		UnsafeUtil.memPutInt(firstPtr + (drawCount << 2), first);
		UnsafeUtil.memPutInt(countPtr + (drawCount << 2), count);
	}

	//	typedef  struct {
	//		uint  count;
	//		uint  instanceCount;
	//		uint  first;
	//		uint  baseInstance;
	//	} DrawArraysIndirectCommand;
	@SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
	private static void addIndirectCommand(long indirectPtr, int drawCount, int first, int count) {
		long ptr = indirectPtr + (drawCount << 4);

		UnsafeUtil.memPutLong(ptr + 0, count | (1L << 32L));
		UnsafeUtil.memPutLong(ptr + 8, first);
	}

	private int prepareTranslucentBatch(int regionIndex, int translucentBit, int drawCount) {
		if (translucentBit == 0) {
			return drawCount;
		}

		long drawData = this.translucentDrawData[regionIndex];
		int first = RegionAllocation.unpackFirst(drawData);
		int count = RegionAllocation.unpackCount(drawData);

		this.addCommandTranslucent(drawCount, first, count);
		return ++drawCount;
	}

	public void setMeshOrder(int regionIndex, int meshOrder) {
		this.sortedMeshOffsets[regionIndex] = MeshDirection.getOffsetsPerFacing(meshOrder);
	}

	public int centerBlockX() {
		return (this.regionX << BLOCK_SHIFT_X) + RADIUS_X;
	}

	public int centerBlockY() {
		return (this.regionY << BLOCK_SHIFT_Y) + RADIUS_Y;
	}

	public int centerBlockZ() {
		return (this.regionZ << BLOCK_SHIFT_Z) + RADIUS_Z;
	}

	public int blockX() {
		return (this.regionX << BLOCK_SHIFT_X);
	}

	public int blockY() {
		return (this.regionY << BLOCK_SHIFT_Y);
	}

	public int blockZ() {
		return (this.regionZ << BLOCK_SHIFT_Z);
	}
}
