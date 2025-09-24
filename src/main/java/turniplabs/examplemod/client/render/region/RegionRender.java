package turniplabs.examplemod.client.render.region;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.render.SectionManager;
import turniplabs.examplemod.client.render.SectionRender;
import turniplabs.examplemod.client.render.ShaderSectionTerrain;
import turniplabs.examplemod.client.render.data.CameraData;
import turniplabs.examplemod.client.util.Direction;
import turniplabs.examplemod.client.render.vertex.VertexWriterManager;
import turniplabs.examplemod.client.render.vertex.writer.TerrainFormat;

public class RegionRender {
	// Region total volume area in SectionRenders.
	public static final int REGION_SECTION_SIZE = 256; // 8 * 4 * 8

	public static final int TRANSLUCENT_DRAWS = 1;
	public static final int SOLID_DRAWS = Direction.COUNT + 1;
	public static final int TOTAL_DRAWS = SOLID_DRAWS + TRANSLUCENT_DRAWS;

	// Region coordinates in region space.
	public int regionX, regionY, regionZ;

	public static final int BLOCK_SHIFT_X = 7;
	public static final int BLOCK_SHIFT_Y = 6;
	public static final int BLOCK_SHIFT_Z = 7;

	public static final int BLOCK_BITS_X = (1 << BLOCK_SHIFT_X) - 1;
	public static final int BLOCK_BITS_Y = (1 << BLOCK_SHIFT_Y) - 1;
	public static final int BLOCK_BITS_Z = (1 << BLOCK_SHIFT_Z) - 1;

	public static final int RADIUS_X = 1 << (BLOCK_SHIFT_X - 1);
	public static final int RADIUS_Y = 1 << (BLOCK_SHIFT_Y - 1);
	public static final int RADIUS_Z = 1 << (BLOCK_SHIFT_Z - 1);

	// The vertex-buffers and its arenas.
	private RegionAllocation translucentBuffer;
	private RegionAllocation solidBuffer;

	// Draw-data buffers for uploading, and the draw index.
	private long solidFirst, solidCount;
	private long translucentFirst, translucentCount;

	// struct RegionDrawData[256]
	// {
	//		uint_64_t solidDrawData[Direction.COUNT + 1];
	//		uint_64_t translucentDrawData;
	// }
	// Each region has in total 8 possible different draw-calls.
	private final long[] regionDrawData = new long[REGION_SECTION_SIZE * TOTAL_DRAWS];

	// Each bit reference the visibility of the solid planes (2..8 bit) and
	// visibility of translucent pass (1 bit).
	public final byte[] drawDataMask = new byte[REGION_SECTION_SIZE];

	// Each time a section is queued for rendering, its region index is saved
	// in the drawIndex position of renderIndices, the top is signaled by drawInd.
	public final byte[] renderIndices = new byte[REGION_SECTION_SIZE];

	// Number of sections queued for draw in the current frame.
	public int sectionsToRender;

	public static final RegionRender NULL = new RegionRender(0, Integer.MIN_VALUE, 0);

	public RegionRender(int sectionX, int sectionY, int sectionZ) {
		this.regionX = sectionX >> (RegionRender.BLOCK_SHIFT_X - 4);
		this.regionY = sectionY >> (RegionRender.BLOCK_SHIFT_Y - 4);
		this.regionZ = sectionZ >> (RegionRender.BLOCK_SHIFT_Z - 4);
	}

	private void prepareSolidPtr() {
		long ptrSolidData = MemoryUtil.nmemAlloc((REGION_SECTION_SIZE * SOLID_DRAWS * Integer.BYTES) * 2);

		this.solidFirst = ptrSolidData;
		this.solidCount = ptrSolidData + (REGION_SECTION_SIZE * SOLID_DRAWS * Integer.BYTES);
	}

	private void prepareTranslucentPtr() {
		long ptrTranslucentData = MemoryUtil.nmemAlloc((REGION_SECTION_SIZE * Integer.BYTES) * 2);

		this.translucentFirst = ptrTranslucentData;
		this.translucentCount = ptrTranslucentData + (REGION_SECTION_SIZE * Integer.BYTES);
	}

	public void clear() {
		if (this.solidBuffer != null) {
			SectionManager.getCurrentInstance().removeUsedMemory(this.solidBuffer.offset);

			this.solidBuffer.clear();
			this.solidBuffer = null;
		}

		if (this.translucentBuffer != null) {
			SectionManager.getCurrentInstance().removeUsedMemory(this.translucentBuffer.offset);

			this.translucentBuffer.clear();
			this.translucentBuffer = null;
		}

		if (this.solidFirst != MemoryUtil.NULL) {
			MemoryUtil.nmemFree(this.solidFirst);

			this.solidFirst = MemoryUtil.NULL;
			this.solidCount = MemoryUtil.NULL;
		}

		if (this.translucentFirst != MemoryUtil.NULL) {
			MemoryUtil.nmemFree(this.translucentFirst);

			this.translucentFirst = MemoryUtil.NULL;
			this.translucentCount = MemoryUtil.NULL;
		}
	}

	public void addSolidMesh(SectionRender render, VertexWriterManager manager, int side) {
		if (this.solidBuffer == null) {
			this.solidBuffer = new RegionAllocation(manager.getVertices() * TerrainFormat.STRIDE);
		}

		if (this.solidFirst == MemoryUtil.NULL) {
			this.prepareSolidPtr();
		}

		int index = (render.regionIndex * TOTAL_DRAWS) + side;

		if (this.regionDrawData[index] != 0) {
			this.regionDrawData[index] = this.solidBuffer.allocate(render, manager.getVertexData(), manager.getVertices(), side);
			return;
		}

		this.regionDrawData[index] = this.solidBuffer.renewAllocation(render, manager.getVertexData(), manager.getVertices(), side);
	}

	public void addTranslucentMesh(SectionRender render, VertexWriterManager manager) {
		if (this.translucentBuffer == null) {
			this.translucentBuffer = new RegionAllocation(manager.getVertices() * TerrainFormat.STRIDE);
		}

		if (this.translucentFirst == MemoryUtil.NULL) {
			this.prepareTranslucentPtr();
		}

		int index = (render.regionIndex * TOTAL_DRAWS) + SOLID_DRAWS;

		if (this.regionDrawData[index] != 0) {
			this.regionDrawData[index] = this.translucentBuffer.allocate(render, manager.getVertexData(), manager.getVertices(), 0);
			return;
		}

		this.regionDrawData[index] = this.translucentBuffer.renewAllocation(render, manager.getVertexData(), manager.getVertices(), 0);
	}

	// Processing draw data now and not in the BFS, allows decoupling the system and doing the extra
	// work between draw which doesn't pressure the driver immediately, also as we work in a "small"
	// and contiguous data-set we don't get penalized too much for pulling SectionRenders from memory.
	public void prepareAndDraw(ShaderSectionTerrain shader, CameraData camera, int pass) {
		if ((pass == 0 && this.solidFirst == MemoryUtil.NULL) || (pass == 1 && this.translucentFirst == MemoryUtil.NULL)) {
			return;
		}

		final byte[] renderIndices = this.renderIndices;
		final byte[] drawDataMask = this.drawDataMask;

		int index;
		int end;
		int inc;

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
			int regionIndex = Byte.toUnsignedInt(renderIndices[index]);
			int drawMask = drawDataMask[regionIndex];

			drawCount = pass == 0
						  ? this.prepareSolidBatch(camera, regionIndex, drawMask >>> 1, drawCount)
						  : this.prepareTranslucentBatch(regionIndex, drawMask & 0b1, drawCount);

			index += inc;
		}

		if (drawCount == 0) {
			return;
		}

		RegionVertexBuffer vertexBuffer = pass == 0 ? this.solidBuffer.vertexBuffer : this.translucentBuffer.vertexBuffer;

		vertexBuffer.bind();

		long first = pass != 0 ? this.translucentFirst : this.solidFirst;
		long count = pass != 0 ? this.translucentCount : this.solidCount;

		int blockRegionX = this.regionX << RegionRender.BLOCK_SHIFT_X;
		int blockRegionY = this.regionY << RegionRender.BLOCK_SHIFT_Y;
		int blockRegionZ = this.regionZ << RegionRender.BLOCK_SHIFT_Z;

		shader.setupRegionOffset(camera, blockRegionX, blockRegionY, blockRegionZ);

		GL15.nglMultiDrawArrays(GL11.GL_QUADS, first, count, drawCount);
	}

	private int prepareSolidBatch(CameraData camera, int regionIndex, int solidMask, int drawCount) {
		if (solidMask == 0) {
			return drawCount;
		}

		int blockX = (sectionX(regionIndex) << 4) + (this.regionX << BLOCK_SHIFT_X);
		int blockY = (sectionY(regionIndex) << 4) + (this.regionY << BLOCK_SHIFT_Y);
		int blockZ = (sectionZ(regionIndex) << 4) + (this.regionZ << BLOCK_SHIFT_Z);

		int visibleFaces = getVisibleFaces(camera.intX, camera.intY, camera.intZ, blockX, blockY, blockZ) & solidMask;

		for (int side = 0; side <= Direction.COUNT; side++) {
			long drawData = this.regionDrawData[regionIndex * TOTAL_DRAWS + side];

			MemoryUtil.memPutInt((drawCount * 4L) + this.solidFirst, RegionAllocation.unpackFirst(drawData));
			MemoryUtil.memPutInt((drawCount * 4L) + this.solidCount, RegionAllocation.unpackCount(drawData));

			drawCount += (visibleFaces >>> side) & 1;
		}

		return drawCount;
	}

	private int prepareTranslucentBatch(int regionIndex, int translucentBit, int drawCount) {
		if (translucentBit == 0) {
			return drawCount;
		}

		long drawData = this.regionDrawData[regionIndex * TOTAL_DRAWS + SOLID_DRAWS];

		MemoryUtil.memPutInt((drawCount << 2L) + this.translucentFirst, RegionAllocation.unpackFirst(drawData));
		MemoryUtil.memPutInt((drawCount << 2L) + this.translucentCount, RegionAllocation.unpackCount(drawData));

		return ++drawCount;
	}

	public static int getVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
		int planes = (1 << Direction.COUNT);

		planes |= greaterThan(originX, (chunkX - 3)) << Direction.EAST;
		planes |= greaterThan(originY, (chunkY - 3)) << Direction.UP;
		planes |= greaterThan(originZ, (chunkZ - 3)) << Direction.SOUTH;

		planes |= lessThan(originX, (chunkX + 19)) << Direction.WEST;
		planes |= lessThan(originY, (chunkY + 19)) << Direction.DOWN;
		planes |= lessThan(originZ, (chunkZ + 19)) << Direction.NORTH;

		return planes;
	}

	public static int lessThan(int a, int b) {
		return (a - b) >>> 31;
	}

	public static int greaterThan(int a, int b) {
		return (b - a) >>> 31;
	}

	public static int regionIndex(int sectionX, int sectionY, int sectionZ) {
		int bitsX = sectionX - ((sectionX >> 3) << 3);
		int bitsY = sectionY - ((sectionY >> 2) << 2);
		int bitsZ = sectionZ - ((sectionZ >> 3) << 3);

		return (bitsX << 0) | (bitsY << 3) | (bitsZ << 5);
	}

	public static int sectionX(int regionIndex) {
		return (regionIndex & 0b000_00_111) >>> 0;
	}

	public static int sectionY(int regionIndex) {
		return (regionIndex & 0b000_11_000) >>> 3;
	}

	public static int sectionZ(int regionIndex) {
		return (regionIndex & 0b111_00_000) >>> 5;
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
}
