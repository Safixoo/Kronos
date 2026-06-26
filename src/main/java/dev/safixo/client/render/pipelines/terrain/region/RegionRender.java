package dev.safixo.client.render.pipelines.terrain.region;

import dev.safixo.client.render.gfx.util.RenderBuffer;
import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.render.pipelines.terrain.shader.TerrainProgram;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;
import org.lwjgl.opengl.*;
import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.util.MeshDirection;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.Direction;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.render.vertex.writers.TerrainFormat;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

public class RegionRender {
	private static final int INT_BYTES = 4;

	// Count of different render-passes possibly dispatched.
	// - SOLID (0)
	// - TRANSLUCENT (1)
	public static final int RENDER_PASSES = 2;
	public static final int SOLID_PASS = 0, TRANSLUCENT_PASS = 1;

	// Region total volume area in SectionRenders.
	public static final int REGION_SECTION_SIZE = 512; // 8 * 8 * 8

	public static final int TRANSLUCENT_DRAWS = 1;
	public static final int SOLID_DRAWS = MeshDirection.COUNT;
	public static final int TOTAL_DRAWS = SOLID_DRAWS + TRANSLUCENT_DRAWS;

	// Region coordinates in region space.
	public int regionX, regionY, regionZ;

	public static final int BLOCK_SHIFT_X = 7;
	public static final int BLOCK_SHIFT_Y = 7;
	public static final int BLOCK_SHIFT_Z = 7;

	public static final int BLOCK_BITS_X = (1 << BLOCK_SHIFT_X) - 1;
	public static final int BLOCK_BITS_Y = (1 << BLOCK_SHIFT_Y) - 1;
	public static final int BLOCK_BITS_Z = (1 << BLOCK_SHIFT_Z) - 1;

	public static final int RADIUS_X = 1 << (BLOCK_SHIFT_X - 1);
	public static final int RADIUS_Y = 1 << (BLOCK_SHIFT_Y - 1);
	public static final int RADIUS_Z = 1 << (BLOCK_SHIFT_Z - 1);

	public static final int DIAMETER_X = 1 << (BLOCK_SHIFT_X);
	public static final int DIAMETER_Y = 1 << (BLOCK_SHIFT_Y);
	public static final int DIAMETER_Z = 1 << (BLOCK_SHIFT_Z);

	// The vertex-buffers and its arenas.
	private RegionAllocation translucentBuffer;
	private RegionAllocation solidBuffer;

	private final RegionManager regionManager;

	// Draw-data buffers for uploading, and the draw index.
	private long solidFirst = UnsafeUtil.NULL, solidCount = UnsafeUtil.NULL;
	private long translucentFirst = UnsafeUtil.NULL, translucentCount = UnsafeUtil.NULL;

	// struct RegionDrawData[256] {
	//		// Solid PASS.
	//		uint_64_t solidDrawData[MeshDirection.COUNT];
	//		// Translucent PASS.
	//		uint_64_t translucentDrawData;
	// };
	// Each region has in total 8 possible different draw-calls.
	private final long[] regionDrawData = new long[REGION_SECTION_SIZE * TOTAL_DRAWS];

	// Each bit reference the visibility of the solid planes (2..8 bit) and
	// visibility of translucent pass (1 bit).
	public final byte[] drawDataMask = new byte[REGION_SECTION_SIZE];

	// Each time a section is queued for rendering, its region index is saved
	// in the drawIndex position of renderIndices, the top is signaled by drawInd.
	public final short[] renderIndices = new short[REGION_SECTION_SIZE];

	// If nothing has changed since the last draw, including the visible bit-set,
	// section count and render-indices try to re-use last draw command setup.
	private final int[] lastDrawCount = new int[RENDER_PASSES];
	private final short[][] lastRenderIndices = new short[RENDER_PASSES][REGION_SECTION_SIZE];

	// This is important as the direction enum, is ordered in a way that fundamentally
	// makes impossible batching draw without meshes being meshed in very specific
	// conditions/ways, also makes batching generally much more effective.
	private final int[] meshDirectionsOrdered = new int[REGION_SECTION_SIZE];

	private final boolean[] shouldCachePass = new boolean[RENDER_PASSES];
	private final int[] lastVisibleCount = new int[RENDER_PASSES];
	private int lastVisibleSet = -1;

	// Number of sections queued for rendering in the current frame.
	public int sectionsToRender;

	public static final RegionRender NULL = new RegionRender(null, 0, Integer.MIN_VALUE, 0);

	private long solidIndirectPtr;
	private long translucentIndirectPtr;

	private static final int INDIRECT_STRUCT_SIZE = 16;

	public RegionRender(RegionManager regionManager, int sectionX, int sectionY, int sectionZ) {
		this.regionX = sectionX >> (RegionRender.BLOCK_SHIFT_X - 4);
		this.regionY = sectionY >> (RegionRender.BLOCK_SHIFT_Y - 4);
		this.regionZ = sectionZ >> (RegionRender.BLOCK_SHIFT_Z - 4);

		this.regionManager = regionManager;

		if (RegionManager.SUPPORT_INDIRECT) {
			this.solidIndirectPtr = NativeBuffer.nmemAlloc(SOLID_DRAWS * REGION_SECTION_SIZE * INDIRECT_STRUCT_SIZE);
			this.translucentIndirectPtr = NativeBuffer.nmemAlloc(TRANSLUCENT_DRAWS * REGION_SECTION_SIZE * INDIRECT_STRUCT_SIZE);
		}
	}

	private void prepareSolidPtr() {
		this.solidFirst = NativeBuffer.nmemAlloc(REGION_SECTION_SIZE * INT_BYTES * SOLID_DRAWS);
		this.solidCount = NativeBuffer.nmemAlloc(REGION_SECTION_SIZE * INT_BYTES * SOLID_DRAWS);
	}

	private void prepareTranslucentPtr() {
		this.translucentFirst = NativeBuffer.nmemAlloc(REGION_SECTION_SIZE * INT_BYTES);
		this.translucentCount = NativeBuffer.nmemAlloc(REGION_SECTION_SIZE * INT_BYTES);
	}

	public void clear() {
		this.shouldCachePass[SOLID_PASS] = false;
		this.shouldCachePass[TRANSLUCENT_PASS] = false;

		Arrays.fill(this.regionDrawData, 0L);

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

			this.solidFirst = UnsafeUtil.NULL;
			this.solidCount = UnsafeUtil.NULL;
		}

		if (this.translucentFirst != UnsafeUtil.NULL) {
			NativeBuffer.nmemFree(this.translucentFirst);
			NativeBuffer.nmemFree(this.translucentCount);

			this.translucentFirst = UnsafeUtil.NULL;
			this.translucentCount = UnsafeUtil.NULL;
		}

		if (this.solidIndirectPtr != UnsafeUtil.NULL) {
			NativeBuffer.nmemFree(this.solidIndirectPtr);
		}

		if (this.translucentIndirectPtr != UnsafeUtil.NULL) {
			NativeBuffer.nmemFree(this.translucentIndirectPtr);
		}
	}

	public void addSolidMesh(SectionRender render, VertexWriter manager, long[] packedDrawData) {
		this.shouldCachePass[SOLID_PASS] = false;

		if (this.solidBuffer == null) {
			this.solidBuffer = new RegionAllocation(manager.getVertices() * TerrainFormat.STRIDE, RegionRender.SOLID_PASS);
		}

		if (this.solidFirst == UnsafeUtil.NULL) {
			this.prepareSolidPtr();
		}

		long drawData = this.solidBuffer.allocate(render, manager.getWriterPtr(), manager.getVertices());
		int sectionFirst = RegionAllocation.unpackFirst(drawData);

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			int index = (render.regionIndex * TOTAL_DRAWS) + dir;
			long relDrawData = packedDrawData[dir];

			if (relDrawData != 0L) {
				int first = RegionAllocation.unpackFirst(relDrawData);
				int count = RegionAllocation.unpackCount(relDrawData);
				this.regionDrawData[index] = RegionAllocation.packDrawData(count, first + sectionFirst);
			} else {
				this.regionDrawData[index] = 0L;
			}
		}
	}

	public void addTranslucentMesh(SectionRender render, VertexWriter manager) {
		this.shouldCachePass[TRANSLUCENT_PASS] = false;

		if (this.translucentBuffer == null) {
			this.translucentBuffer = new RegionAllocation(manager.getVertices() * TerrainFormat.STRIDE, RegionRender.TRANSLUCENT_PASS);
		}

		if (this.translucentFirst == UnsafeUtil.NULL) {
			this.prepareTranslucentPtr();
		}

		int index = (render.regionIndex * TOTAL_DRAWS) + SOLID_DRAWS;
		this.regionDrawData[index] = this.translucentBuffer.allocate(render, manager.getWriterPtr(), manager.getVertices());
	}

	public void deleteRenderAllocation(SectionRender render) {
		long[] drawData = this.regionDrawData;

		int translucentDrawData = (render.regionIndex * TOTAL_DRAWS) + SOLID_DRAWS;
		int solidDrawData = (render.regionIndex * TOTAL_DRAWS);

		if (drawData[translucentDrawData] != 0L) {
			this.translucentBuffer.remove(render);
			drawData[translucentDrawData] = 0L;
		}

		if (this.solidBuffer != null) {
			this.solidBuffer.remove(render);
			for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
				drawData[solidDrawData + dir] = 0L;
			}
		}
	}

	// Processing draw data now and not in the BFS, allows decoupling the system and doing the extra
	// work between draw which doesn't pressure the driver immediately, also as we work in a "small"
	// and contiguous data-set we don't get penalized too much for pulling SectionRenders from memory.
	public void prepareAndDraw(WorldManager manager, TerrainProgram shader, CameraData camera, int pass) {
		if ((pass == 0 && this.solidFirst == UnsafeUtil.NULL) || (pass == 1 && this.translucentFirst == UnsafeUtil.NULL)) {
			return;
		}

		// Try to re-use the last draw command setup.
		if (!manager.hasGraphUpdated() || (this.shouldCachePass[pass] && this.shouldUseCachedDraw(camera, pass))) {
			int drawCount = this.lastDrawCount[pass];

			if (drawCount != 0) {
				this.multiDrawData(camera, shader, pass, drawCount);
			}

			return;
		}

		final short[] renderIndices = this.renderIndices;
		final byte[] drawDataMask = this.drawDataMask;

		int index;
		int end;
		int inc;

		// Change iteration order based in current render-pass.
		if (pass == 1) {
			index = this.sectionsToRender - 1;
			end = -1;
			inc = -1;
		} else {
			index = 0;
			end = this.sectionsToRender;
			inc = 1;
		}

		int drawCount = 0;

		while (index != end) {
			int regionIndex = renderIndices[index];
			int drawMask = drawDataMask[regionIndex];

			drawCount = pass == 0
						  ? this.prepareSolidBatch(camera, regionIndex, drawMask >>> 1, drawCount)
						  : this.prepareTranslucentBatch(regionIndex, drawMask & 0b1, drawCount);

			index += inc;
		}

		this.lastDrawCount[pass] = drawCount;

		if (drawCount == 0) {
			return;
		}

		this.multiDrawData(camera, shader, pass, drawCount);
		this.shouldCachePass[pass] = true;
	}

	private void multiDrawData(CameraData camera, TerrainProgram shader, int pass, int drawCount) {
		RenderBuffer vertexBuffer = pass == 0 ? this.solidBuffer.vertexBuffer : this.translucentBuffer.vertexBuffer;

		vertexBuffer.bindState(true);

		int blockRegionX = this.regionX << RegionRender.BLOCK_SHIFT_X;
		int blockRegionY = this.regionY << RegionRender.BLOCK_SHIFT_Y;
		int blockRegionZ = this.regionZ << RegionRender.BLOCK_SHIFT_Z;

		// Setup camera and region offset.
		shader.setupRegionOffset(camera, blockRegionX, blockRegionY, blockRegionZ);

		if (RegionManager.SUPPORT_INDIRECT) {
			this.multiDrawIndirect(drawCount, pass);
		} else {
			this.multiDrawDirect(drawCount, pass);
		}
	}

	private void multiDrawDirect(int drawCount, int pass) {
		long first = pass != 0 ? this.translucentFirst : this.solidFirst;
		long count = pass != 0 ? this.translucentCount : this.solidCount;

		// As of now count and first use the pointer but with some offset, so simply offset
		// count itself to make the same effect.
		IntBuffer firstBuff = NativeBuffer.wrap(first).asIntBuffer();
		IntBuffer countBuff = NativeBuffer.wrap(count).asIntBuffer();

		((Buffer) firstBuff).limit(drawCount);
		((Buffer) countBuff).limit(drawCount);

		GL14.glMultiDrawArrays(GL11.GL_QUADS, firstBuff, countBuff);
	}

	private void multiDrawIndirect(int drawCount, int pass) {
		ByteBuffer indirectBuff = NativeBuffer.wrap(pass == 0 ? this.solidIndirectPtr : this.translucentIndirectPtr);

		if (indirectBuff == null) {
			return;
		}

		((Buffer) indirectBuff).limit(drawCount * INDIRECT_STRUCT_SIZE);

		GL43.glMultiDrawArraysIndirect(GL11.GL_QUADS, indirectBuff, drawCount, 0);
	}

	// TODO: In some cases even this code isn't even correct (ex: inside a region sometimes the result is invalid but
	//  because the camera didn't move in the exact way to invalidate indices, the result is re-used and culling
	//  artifacts are seen).
	private boolean shouldUseCachedDraw(CameraData camera, int pass) {
		int regionVis = getRegionVisibleFaces(camera.intX, camera.intY, camera.intZ, this.centerBlockX(), this.centerBlockY(), this.centerBlockZ());
		int oldRegionVis = this.lastVisibleSet;

		this.lastVisibleSet = regionVis;

		if (regionVis != oldRegionVis || this.lastVisibleCount[pass] != this.sectionsToRender) {
			this.lastVisibleCount[pass] = this.sectionsToRender;
			return false;
		}

		final short[] lastRenderIndices = this.lastRenderIndices[pass];
		final short[] renderIndices = this.renderIndices;
		final int maxIndex = this.sectionsToRender;

		int index = 0;

		// Mismatch of section indices.
		while (index < maxIndex && lastRenderIndices[index] == renderIndices[index]) {
			index++;
		}

		boolean canBeCached = index == maxIndex;

		// A mismatch was found, copy the indices from the mismatch index.
		while (index < maxIndex) {
			lastRenderIndices[index] = renderIndices[index++];
		}

		return canBeCached;
	}

	private int prepareSolidBatch(CameraData camera, int regionIndex, int solidMask, int drawCount) {
		if (solidMask == 0) {
			return drawCount;
		}

		int blockX = (sectionX(regionIndex) << 4) + (this.regionX << BLOCK_SHIFT_X);
		int blockY = (sectionY(regionIndex) << 4) + (this.regionY << BLOCK_SHIFT_Y);
		int blockZ = (sectionZ(regionIndex) << 4) + (this.regionZ << BLOCK_SHIFT_Z);

		int visibleFaces = getSectionVisibleFaces(camera.intX, camera.intY, camera.intZ, blockX, blockY, blockZ) & solidMask;

		if (visibleFaces == 0) {
			return drawCount;
		}

		int batchedFirst = -1;
		int batchedCount = -1;
		boolean meshRemaining = false;

		int orderedDirectionMask = this.meshDirectionsOrdered[regionIndex];

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			int meshCurrentDir = orderedDirectionMask & 0xF;
			orderedDirectionMask >>= 4;

			if ((visibleFaces & (1 << meshCurrentDir)) == 0) {
				continue;
			}

			long drawData = this.regionDrawData[regionIndex * TOTAL_DRAWS + meshCurrentDir];

			int first = RegionAllocation.unpackFirst(drawData);
			int count = RegionAllocation.unpackCount(drawData);

			// Always save the last draw data and if the draw data is contiguous in memory
			// continue batching the draw, is slower than the normal method but with the draw
			// caching technique combined with the batching here, is a nice improvement.
			if ((batchedFirst + batchedCount) != first) {
				if (meshRemaining) {
					this.addCommandSolid(drawCount++, batchedFirst, batchedCount);
				}

				meshRemaining = true;
				batchedFirst = first;
				batchedCount = count;
			} else /* ((first + count) == meshFirst) */ {
				batchedCount += count;
			}

		}

		if (meshRemaining) {
			this.addCommandSolid(drawCount++, batchedFirst, batchedCount);
		}

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

		long drawData = this.regionDrawData[regionIndex * TOTAL_DRAWS + SOLID_DRAWS];
		int first = RegionAllocation.unpackFirst(drawData);
		int count = RegionAllocation.unpackCount(drawData);

		this.addCommandTranslucent(drawCount, first, count);
		return ++drawCount;
	}

	public static int getSectionVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
		int planes = 1 << MeshDirection.GENERIC;

		planes |= greaterThan(originX, (chunkX - 3)) << Direction.EAST;
		planes |= greaterThan(originY, (chunkY - 3)) << Direction.UP;
		planes |= greaterThan(originZ, (chunkZ - 3)) << Direction.SOUTH;

		planes |= lessThan(originX, (chunkX + 19)) << Direction.WEST;
		planes |= lessThan(originY, (chunkY + 19)) << Direction.DOWN;
		planes |= lessThan(originZ, (chunkZ + 19)) << Direction.NORTH;

		return planes;
	}

	public static int getRegionVisibleFaces(int originX, int originY, int originZ, int centerRegionX, int centerRegionY, int centerRegionZ) {
		int planes = 1 << MeshDirection.GENERIC;

		planes |= greaterThan(originX, (centerRegionX - RADIUS_X - 19)) << Direction.EAST;
		planes |= greaterThan(originY, (centerRegionY - RADIUS_Y - 19)) << Direction.UP;
		planes |= greaterThan(originZ, (centerRegionZ - RADIUS_Z - 19)) << Direction.SOUTH;

		planes |= lessThan(originX, (centerRegionX + RADIUS_X + 19)) << Direction.WEST;
		planes |= lessThan(originY, (centerRegionY + RADIUS_Y + 19)) << Direction.DOWN;
		planes |= lessThan(originZ, (centerRegionZ + RADIUS_Z + 19)) << Direction.NORTH;

		return planes;
	}

	public void addMeshOrderMask(int regionIndex, int mask) {
		this.meshDirectionsOrdered[regionIndex] = mask;
	}

	public static int lessThan(int a, int b) {
		return (a - b) >>> 31;
	}

	public static int greaterThan(int a, int b) {
		return (b - a) >>> 31;
	}

	public static int regionIndex(int sectionX, int sectionY, int sectionZ) {
		int bitsX = sectionX & (BLOCK_BITS_X >> 4);
		int bitsY = sectionY & (BLOCK_BITS_Y >> 4);
		int bitsZ = sectionZ & (BLOCK_BITS_Z >> 4);

		return (bitsX << 0) | (bitsY << 3) | (bitsZ << 6);
	}

	// Generates an order of drawing of directions that makes draw-batching more favorable.
	public static int generateMeshDrawOrderMask(int meshBitDirections) {
		int preferredCount = 0;
		int restCount = 0;

		int preferredDirSet = 0;
		int restDirectionSet = 0;

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			// Mesh directions are always less than 0xF.
			if ((meshBitDirections & (1 << dir)) != 0) {
				preferredDirSet |= (dir & 0xF) << (preferredCount++ * 4);
			} else {
				restDirectionSet |= (dir & 0xF) << (restCount++ * 4);
			}
		}

		return preferredDirSet | (restDirectionSet << (preferredCount * 4));
	}

	public static int sectionX(int regionIndex) {
 		return (regionIndex & 0b000_000_111) >>> 0;
	}

	public static int sectionY(int regionIndex) {
		return (regionIndex & 0b000_111_000) >>> 3;
	}

	public static int sectionZ(int regionIndex) {
		return (regionIndex & 0b111_000_000) >>> 6;
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
