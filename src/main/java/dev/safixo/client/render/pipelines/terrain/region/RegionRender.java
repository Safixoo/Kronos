package dev.safixo.client.render.pipelines.terrain.region;

import dev.safixo.client.render.gfx.buffer.GlBuffer;
import dev.safixo.client.render.gfx.buffer.GlIndexBuffer;
import dev.safixo.client.render.gfx.util.RenderBuffer;
import dev.safixo.client.render.gfx.util.StagingBuffer;
import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.render.pipelines.terrain.meshing.task.SectionResult;
import dev.safixo.client.render.pipelines.terrain.shader.TerrainProgram;
import dev.safixo.client.render.pipelines.terrain.sorting.BSPReader;
import dev.safixo.client.render.pipelines.terrain.sorting.BSPWriter;
import dev.safixo.client.render.pipelines.terrain.sorting.IndexBufferWriter;
import dev.safixo.client.render.vertex.DefaultVertexFormats;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.util.MeshDirection;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.render.vertex.writers.TerrainFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;

import static dev.safixo.client.render.pipelines.terrain.region.GlAllocator.*;
import static dev.safixo.client.render.pipelines.terrain.region.RegionConstants.*;

public class RegionRender {
	private static final boolean ALWAYS_SORT = false;

	protected static final int INDIRECT_ARRAYS_STRUCT_SIZE = 16;
	protected static final int INDIRECT_ELEMENTS_STRUCT_SIZE = 20;

	private static final int INT_BYTES = 4;

	// Region coordinates in region space.
	public int regionX, regionY, regionZ;

	// The vertex-buffers and its arenas.
	private GlAllocator<RenderBuffer> regionBuffer;

	// The index-buffer shared in all terrain.
	private GlAllocator<GlIndexBuffer> globalIndexBuffer;

	private final RegionManager regionManager;

	// Draw-data buffers for uploading, and the draw index.
	private long solidFirst = UnsafeUtil.NULL, solidCount = UnsafeUtil.NULL;
	private long translucentFirst = UnsafeUtil.NULL, translucentCount = UnsafeUtil.NULL, translucentIndices = UnsafeUtil.NULL;

	// Saves all sections translucency data.
	private BSPReader[] sectionsTrees;

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

	// Byte offsets of each section indices.
	private int[] sectionIndicesData;

	// Each bit reference the visibility of the solid planes (2..8 bit) and
	// visibility of translucent pass (1 bit).
	private final byte[] drawDataMask = new byte[REGION_SECTION_SIZE];

	// Each time a section is queued for rendering, its region index is saved
	// in the drawIndex position of renderIndices, the top is signaled by drawInd.
	protected final short[] renderIndices = new short[REGION_SECTION_SIZE];

	// This is important as the Minecraft direction enum is layered in a way that makes it
	// fundamentally impossible batching draws without meshes meeting very specific criteria.
	private final int[] sortedMeshOffsets = new int[REGION_SECTION_SIZE];

	// The visible faces that the section had during the last time it was meshed.
	private final byte[] visibleFaces = new byte[REGION_SECTION_SIZE];

	// Number of sections queued for rendering in the current frame.
	private int queuedSections;

	// Pointers for the indirect drawing commands.
	private long solidIndirectPtr = UnsafeUtil.NULL;
	private long translucentIndirectPtr = UnsafeUtil.NULL;

	// Memoize drawing context to check if the result of the last draw construction is still valid to cache.
	private final RegionDrawContext drawContext = new RegionDrawContext();
	private final RegionTileEntities tileEntities;

	private CameraData lastCamera;

	private boolean translucentReady;
	private boolean solidReady;

	protected RegionRender(RegionManager regionManager, int sectionX, int sectionY, int sectionZ) {
		this.regionX = sectionX >> (RegionConstants.BLOCK_SHIFT_X - 4);
		this.regionY = sectionY >> (RegionConstants.BLOCK_SHIFT_Y - 4);
		this.regionZ = sectionZ >> (RegionConstants.BLOCK_SHIFT_Z - 4);

		this.tileEntities = new RegionTileEntities(this.regionX, this.regionY, this.regionZ);
		this.regionManager = regionManager;

		if (regionManager != null) {
			this.globalIndexBuffer = regionManager.getGlobalIndexBuffer();
		}
	}

	public long getIndexPtr() {
		return this.translucentIndices;
	}

	public long getFirstPtr(int pass) {
		return pass != 0 ? this.translucentFirst : this.solidFirst;
	}

	public long getCountPtr(int pass) {
		return pass != 0 ? this.translucentCount : this.solidCount;
	}

	public RenderBuffer getBuffer() {
		return this.regionBuffer.getBuffer();
	}

	public GlAllocator<RenderBuffer> getAllocator() {
		return this.regionBuffer;
	}

	public GlIndexBuffer getIndexBuffer() {
		return this.globalIndexBuffer.getBuffer();
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

		if (this.regionBuffer != null) {
			this.regionBuffer.delete();
			this.regionBuffer = null;
		}

		if (this.solidFirst != UnsafeUtil.NULL) {
			NativeBuffer.nmemFree(this.solidFirst);
			NativeBuffer.nmemFree(this.solidCount);
			this.solidFirst = this.solidCount = UnsafeUtil.NULL;
		}

		if (this.translucentFirst != UnsafeUtil.NULL) {
			NativeBuffer.nmemFree(this.translucentFirst);
			NativeBuffer.nmemFree(this.translucentCount);
			NativeBuffer.nmemFree(this.translucentIndices);
			this.translucentFirst = this.translucentCount = this.translucentIndices = UnsafeUtil.NULL;
		}

		if (this.solidIndirectPtr != UnsafeUtil.NULL) {
			NativeBuffer.nmemFree(this.solidIndirectPtr);
		}
		if (this.translucentIndirectPtr != UnsafeUtil.NULL) {
			NativeBuffer.nmemFree(this.translucentIndirectPtr);
		}

		int sectionX = this.regionX << (BLOCK_SHIFT_X - 4);
		int sectionY = this.regionY << (BLOCK_SHIFT_X - 4);
		int sectionZ = this.regionZ << (BLOCK_SHIFT_X - 4);

		if (this.sectionsTrees == null) {
			return;
		}

		for (int i = 0; i < REGION_SECTION_SIZE; i++) {
			if (this.sectionsTrees[i] != null) {
				int localSectionX = sectionX + getLocalSectionX(i);
				int localSectionY = sectionY + getLocalSectionY(i);
				int localSectionZ = sectionZ + getLocalSectionZ(i);

				this.globalIndexBuffer.remove(MathExt.asLong(localSectionX, localSectionY, localSectionZ));
			}
		}
	}

	public int getMeshedVisibleFaces(int regionIndex) {
		return this.visibleFaces[regionIndex];
	}

	private void sumbitAllocation(GlBuffer regionBuffer, ByteBuffer buffer, long allocData, int size) {
		StagingBuffer stagingBuffer = StagingBuffer.getInstance();

		if (!stagingBuffer.addUpload(buffer, regionBuffer, size, unpackOffset(allocData))) {
			// the staging buffer couldn't satisfy the condition.
			this.uploadAllocationDirectly(regionBuffer, buffer, allocData, size);
		}
	}

	private void uploadAllocationDirectly(GlBuffer regionBuffer, ByteBuffer buffer, long allocData, int size) {
		regionBuffer.bufferSubData(buffer, buffer.position(), unpackOffset(allocData), size);
	}

	public void addSolidMesh(SectionRender render, VertexWriter manager, SectionResult buildResult) {
		this.drawContext.invalidatePass(SOLID_PASS);

		this.setMeshOrder(render.regionIndex, buildResult.getMeshOrder());
		this.visibleFaces[render.regionIndex] = (byte) buildResult.getVisibleFaces();

		long[] packedDrawData = buildResult.getDrawData();

		GlBuffer spareBuffer = this.regionManager.getSpareBuffer();

		if (this.solidDrawData == null) {
			this.solidDrawData = new int[REGION_SECTION_SIZE * (MeshDirection.COUNT + 1)];
		}
		if (this.regionBuffer == null) {
			int size = manager.getVertices() * TerrainFormat.STRIDE;
			this.regionBuffer = new GlAllocator<>(new RenderBuffer(DefaultVertexFormats.TERRAIN_FORMAT, GL15.GL_STATIC_DRAW), spareBuffer, size, true);
		}

		this.prepareSolidPtr();

		long allocData = this.regionBuffer.allocate(render.regionIndex | (SOLID_PASS << 31), manager.getVertices() * TerrainFormat.STRIDE);
		int sectionFirst = unpackOffset(allocData) / TerrainFormat.STRIDE;

		this.sumbitAllocation(this.regionBuffer.getBuffer(), manager.getNioPtr(), allocData, manager.getVertices() * TerrainFormat.STRIDE);

		int drawDataIndex = render.regionIndex * TOTAL_DRAWS;
		this.solidDrawData[drawDataIndex] = sectionFirst;

		// Instead of saving the draw data in Direction enum order, do it in the sorted order.
		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			int sortedDirection = MeshDirection.getByIndex(buildResult.getMeshOrder(), dir);

			int index = (drawDataIndex + 1) + dir;
			int count = unpackSize(packedDrawData[sortedDirection]);

			this.solidDrawData[index] = count;
		}
	}

	private void prepareSolidPtr() {
		if (this.solidReady) {
			return;
		}

		if (!RegionManager.SUPPORT_INDIRECT) {
			this.solidFirst = NativeBuffer.nmemAlloc(SOLID_DRAWS * REGION_SECTION_SIZE * INT_BYTES);
			this.solidCount = NativeBuffer.nmemAlloc(SOLID_DRAWS * REGION_SECTION_SIZE * INT_BYTES);
		} else {
			this.solidIndirectPtr = NativeBuffer.nmemAlloc(SOLID_DRAWS * REGION_SECTION_SIZE * INDIRECT_ARRAYS_STRUCT_SIZE);

			for (int i = 0; i < SOLID_DRAWS * REGION_SECTION_SIZE; i++) {
				UnsafeUtil.memPutInt(this.solidIndirectPtr + i * INDIRECT_ARRAYS_STRUCT_SIZE + 4, 1);
				UnsafeUtil.memPutInt(this.solidIndirectPtr + i * INDIRECT_ARRAYS_STRUCT_SIZE + 12, 0);
			}
		}

		this.solidReady = true;
	}

	public void addTranslucentMesh(SectionRender section, CameraData camera, VertexWriter manager, SectionResult buildResult) {
		this.drawContext.invalidatePass(TRANSLUCENT_PASS);
		BSPReader tree = buildResult.getTranslucencyTree();

		if (tree != null && this.sectionsTrees == null) {
			this.sectionsTrees = new BSPReader[REGION_SECTION_SIZE];
		}
		if (this.sectionsTrees != null) {
			if (tree == null && this.sectionsTrees[section.regionIndex] != null) {
				this.globalIndexBuffer.remove(section.globalPosition);
			}
			this.sectionsTrees[section.regionIndex] = tree;
		}

		GlBuffer spareBuffer = this.regionManager.getSpareBuffer();

		if (this.translucentDrawData == null) {
			this.translucentDrawData = new long[REGION_SECTION_SIZE];
			this.sectionIndicesData = new int[REGION_SECTION_SIZE];
		}
		if (this.regionBuffer == null) {
			int size = manager.getVertices() * TerrainFormat.STRIDE;
			this.regionBuffer = new GlAllocator<>(new RenderBuffer(DefaultVertexFormats.TERRAIN_FORMAT, GL15.GL_STATIC_DRAW), spareBuffer, size, true);
		}

		this.prepareTranslucentPtr();

		this.setupSectionTranslucency(tree, camera, this.regionManager.getIndexBufferWriter(), section);

		long allocData = this.regionBuffer.allocate(section.regionIndex | (TRANSLUCENT_PASS << 31), manager.getVertices() * TerrainFormat.STRIDE);
		this.sumbitAllocation(this.regionBuffer.getBuffer(), manager.getNioPtr(), allocData, manager.getVertices() * TerrainFormat.STRIDE);

		int first = unpackOffset(allocData) / TerrainFormat.STRIDE;
		int count = unpackSize(allocData) / TerrainFormat.STRIDE;

		this.translucentDrawData[section.regionIndex] = packAllocation(count, first);
	}

	private void prepareTranslucentPtr() {
		if (this.translucentReady) {
			return;
		}

		if (!RegionManager.SUPPORT_INDIRECT) {
			this.translucentFirst = NativeBuffer.nmemAlloc(REGION_SECTION_SIZE * INT_BYTES);
			this.translucentCount = NativeBuffer.nmemAlloc(REGION_SECTION_SIZE * INT_BYTES);
			this.translucentIndices = NativeBuffer.nmemAlloc(REGION_SECTION_SIZE * INT_BYTES);
		} else {
			this.translucentIndirectPtr = NativeBuffer.nmemAlloc(REGION_SECTION_SIZE * INDIRECT_ELEMENTS_STRUCT_SIZE);

			for (int i = 0; i < REGION_SECTION_SIZE; i++) {
				UnsafeUtil.memPutInt(this.translucentIndirectPtr + i * INDIRECT_ELEMENTS_STRUCT_SIZE + 4, 1);
				UnsafeUtil.memPutInt(this.translucentIndirectPtr + i * INDIRECT_ELEMENTS_STRUCT_SIZE + 16, 0);
			}
		}

		this.translucentReady = true;
	}

	public void updateTranslucency(IndexBufferWriter writer, CameraData camera) {
		if (this.sectionsTrees == null) {
			return;
		}

		// The tree planes are saved in region local position, so the camera position should be relative
		// to the current region.
		int[] eye = BSPReader.getPlayerEye(
			(float) (camera.cameraXD() - (this.regionX << BLOCK_SHIFT_X)),
			(float) (camera.cameraYD() - (this.regionY << BLOCK_SHIFT_Y)),
			(float) (camera.cameraZD() - (this.regionZ << BLOCK_SHIFT_Z))
		);

		for (int i = 0; i < this.getRenderIndex(); i++) {
			int regionIndex = this.renderIndices[i];
			BSPReader tree = this.sectionsTrees[regionIndex];

			if (tree == null) {
				continue;
			}

			writer.restart();

			int sectionX = tree.sectionX;
			int sectionY = tree.sectionY;
			int sectionZ = tree.sectionZ;

			if (ALWAYS_SORT || this.shouldUpdateSection(camera, sectionX, sectionY, sectionZ) || this.shouldUpdateSection(this.lastCamera, sectionX, sectionY, sectionZ)) {
				int sortType = tree.traverseBackFront(eye, writer);

				if (sortType == BSPWriter.DEFAULT_SORT) {
					long allocData = this.globalIndexBuffer.allocate(MathExt.asLong(sectionX, sectionY, sectionZ), writer.getOffset());
					this.uploadAllocationDirectly(this.globalIndexBuffer.getBuffer(), writer.getPtrNio(), allocData, unpackSize(allocData));
					this.sectionIndicesData[regionIndex] = unpackOffset(allocData);
				} else if (sortType == BSPWriter.IDENTITY_SORT) {
					this.sectionIndicesData[regionIndex] = 0;
				}

				writer.restart();
			}
		}

		this.lastCamera = camera;
	}

	private void setupSectionTranslucency(BSPReader tree, CameraData camera, IndexBufferWriter writer, SectionRender section) {
		if (tree == null) {
			this.sectionIndicesData[section.regionIndex] = 0;
			return;
		}

		writer.restart();

		// The tree planes are saved in region local position, so the camera position should be relative
		// to the current region.
		int[] eye = BSPReader.getPlayerEye(
			(float) (camera.cameraXD() - (this.regionX << BLOCK_SHIFT_X)),
			(float) (camera.cameraYD() - (this.regionY << BLOCK_SHIFT_Y)),
			(float) (camera.cameraZD() - (this.regionZ << BLOCK_SHIFT_Z))
		);

		int sortType = tree.traverseBackFront(eye, writer);

		if (sortType == BSPWriter.UNCHANGED_SORT) {
			throw new RuntimeException("It shouldn't be possible to cache the sort if the tree hasn't been traversed yet!");
		}

		if (sortType == BSPWriter.DEFAULT_SORT) {
			long allocData = this.globalIndexBuffer.allocate(section.globalPosition, writer.getOffset());
			this.uploadAllocationDirectly(this.globalIndexBuffer.getBuffer(), writer.getPtrNio(), allocData, unpackSize(allocData));
			this.sectionIndicesData[section.regionIndex] = unpackOffset(allocData);
		} else if (sortType == BSPWriter.IDENTITY_SORT) {
			this.sectionIndicesData[section.regionIndex] = 0;
		}

		writer.restart();
	}

	private boolean shouldUpdateSection(CameraData camera, int sectionX, int sectionY, int sectionZ) {
		if (camera == null) {
			return true;
		}

		int cameraSectionX = camera.intX >> 4;
		int cameraSectionY = camera.intY >> 4;
		int cameraSectionZ = camera.intZ >> 4;

		return cameraSectionX == sectionX || cameraSectionY == sectionY || cameraSectionZ == sectionZ;
	}

	public void prepareAndDraw(WorldManager manager, TerrainProgram shader, CameraData camera, int pass) {
		if ((pass == SOLID_PASS && !this.solidReady) || (pass == TRANSLUCENT_PASS && !this.translucentReady)) {
			return;
		}

		// Try to re-use the last draw command setup.
		if (!manager.hasGraphUpdated() || (this.drawContext.isPassCacheable(pass) && this.drawContext.mismatchAndCopy(this, camera, pass))) {
			this.drawContext.multiDrawData(this, camera, shader, pass);
			return;
		}

		final short[] renderIndices = this.renderIndices;
		final byte[] drawDataMask = this.drawDataMask;

		int drawCount = 0;

		if (pass == SOLID_PASS) {
			for (int i = 0; i < this.queuedSections; i++) {
				int regionIndex = renderIndices[i];
				int drawMask = getSolidMask(drawDataMask[regionIndex]);
				drawCount = this.setupSolidBatch(camera, regionIndex, drawMask, drawCount);
			}
		} else {
			for (int i = this.queuedSections - 1; i >= 0; i--) {
				int regionIndex = renderIndices[i];
				int drawMask = getTranslucentMask(drawDataMask[regionIndex]);
				drawCount = this.setupTranslucentBatch(regionIndex, drawMask, drawCount);
			}
		}

		this.drawContext.setDrawCount(pass, drawCount);
		this.drawContext.validatePass(pass);
		this.drawContext.multiDrawData(this, camera, shader, pass);
	}

	private int setupSolidBatch(CameraData camera, int regionIndex, int solidMask, int drawCount) {
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

	private int setupTranslucentBatch(int regionIndex, int translucentBit, int drawCount) {
		if (translucentBit == 0) {
			return drawCount;
		}

		long drawData = this.translucentDrawData[regionIndex];
		int first = unpackOffset(drawData);
		int count = unpackSize(drawData);
		int index = this.sectionIndicesData[regionIndex];

		this.addCommandTranslucent(drawCount, first, count, index);
		return ++drawCount;
	}

	private void addCommandSolid(int drawCount, int first, int count) {
		if (RegionManager.SUPPORT_INDIRECT) {
			addIndirectArraysCommand(this.solidIndirectPtr, drawCount, first, count);
		} else {
			addDirectArraysCommand(this.solidFirst, this.solidCount, drawCount, first, count);
		}
	}

	private void addCommandTranslucent(int drawCount, int first, int count, int index) {
		if (RegionManager.SUPPORT_INDIRECT) {
			addIndirectElementsCommand(this.translucentIndirectPtr, drawCount, first, count, index);
		} else {
			addDirectElementsCommand(this.translucentFirst, this.translucentCount, this.translucentIndices, drawCount, first, count, index);
		}
	}

	@SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
	private static void addDirectArraysCommand(long firstPtr, long countPtr, int drawCount, int first, int count) {
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
	private static void addIndirectArraysCommand(long indirectPtr, int drawCount, int first, int count) {
		long ptr = indirectPtr + (drawCount << 4);

		UnsafeUtil.memPutInt(ptr + 0, count);
		UnsafeUtil.memPutInt(ptr + 8, first);
	}

	@SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
	private static void addDirectElementsCommand(long firstPtr, long countPtr, long indexPtr, int drawCount, int first, int count, int index) {
		UnsafeUtil.memPutInt(firstPtr + (drawCount << 2), first);
		UnsafeUtil.memPutInt(countPtr + (drawCount << 2), count);
		UnsafeUtil.memPutInt(indexPtr + (drawCount << 2), index);
	}

	//	typedef  struct {
	//		uint  count;
	//		uint  instanceCount;
	//		uint  firstIndex;
	//		int  baseVertex;
	//		uint  baseInstance;
	//	} DrawElementsIndirectCommand;
	private static void addIndirectElementsCommand(long indirectPtr, int drawCount, int first, int count, int index) {
		long ptr = indirectPtr + (drawCount * INDIRECT_ELEMENTS_STRUCT_SIZE);

		UnsafeUtil.memPutInt(ptr + 0, count);
		UnsafeUtil.memPutInt(ptr + 8, index >> 1); // index / IndexBufferWriter.INDEX_SIZE
		UnsafeUtil.memPutInt(ptr + 12, first);
	}

	static {
		if (IndexBufferWriter.INDEX_SIZE != 2) throw new RuntimeException("First index is not computed correctly");
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
