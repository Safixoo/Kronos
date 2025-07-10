package turniplabs.examplemod.client.render.region;

import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.render.SectionManager;
import turniplabs.examplemod.client.render.SectionRender;
import turniplabs.examplemod.client.vertex.VertexWriterManager;
import turniplabs.examplemod.client.vertex.writer.TerrainVertexWriter;

public class RegionRender {
	public int regionX, regionY, regionZ;

	private RegionAllocation translucentBuffer;
	private RegionAllocation solidBuffer;

	public final long solidFirst = MemoryUtil.nmemAlloc(256 * 4);
	public final long solidCount = MemoryUtil.nmemAlloc(256 * 4);
	public int solidEmptyDraw = 0;

	public final long translucentFirst = MemoryUtil.nmemAlloc(256 * 4);
	public final long translucentCount = MemoryUtil.nmemAlloc(256 * 4);
	public int translucentEmptyDraw = 0;

	private final RegionManager regionManager;

	private long solidMask = 0;
	private long translucentMask = 0;
	public int currentFrame;

	public RegionRender(RegionManager regionManager, int sectionX, int sectionY, int sectionZ) {
		this.regionManager = regionManager;

		this.regionX = sectionX >> 3;
		this.regionY = sectionY >> 2;
		this.regionZ = sectionZ >> 3;
	}

	public int getChunkX() {
		return this.regionX << 3;
	}

	public int getChunkY() {
		return this.regionY << 2;
	}

	public int getChunkZ() {
		return this.regionZ << 3;
	}

	public void clear() {
		if (this.solidBuffer != null) {
			SectionManager.getCurrentInstance().removeUsedMemory(this.solidBuffer.offset);
			this.solidBuffer.vertexBuffer.clear();
		}

		if (this.translucentBuffer != null) {
			SectionManager.getCurrentInstance().removeUsedMemory(this.translucentBuffer.offset);
			this.translucentBuffer.vertexBuffer.clear();
		}

		MemoryUtil.nmemFree(this.solidFirst);
		MemoryUtil.nmemFree(this.solidCount);

		MemoryUtil.nmemFree(this.translucentFirst);
		MemoryUtil.nmemFree(this.translucentCount);
	}

	private int getRegionIndex(SectionRender render) {
		int relX = (render.blockX >> 4) - this.getChunkX();
		int relY = (render.blockY >> 4) - this.getChunkY();
		int relZ = (render.blockZ >> 4) - this.getChunkZ();

		return regionIndex(relX, relY, relZ);
	}

	public void addSolidMesh(SectionRender render, VertexWriterManager manager) {
		if (this.solidBuffer == null) {
			this.solidBuffer = new RegionAllocation(manager.getVertices() * TerrainVertexWriter.STRIDE, false);
		}

		long regionBitIndex = 1L << getRegionIndex(render);
		render.solidDraw = this.solidBuffer.renewAllocation(render, manager.getVertexData(), manager.getVertices());

		this.solidMask |= regionBitIndex;
	}

	public void addTranslucentMesh(SectionRender render, VertexWriterManager manager) {
		if (this.translucentBuffer == null) {
			int max = Math.max(manager.getVertices() * TerrainVertexWriter.STRIDE, 8192);

			this.translucentBuffer = new RegionAllocation(max, true);
		}

		long regionBitIndex = 1L << getRegionIndex(render);
		render.translucentDraw = this.translucentBuffer.renewAllocation(render, manager.getVertexData(), manager.getVertices());

		this.translucentMask |= regionBitIndex;
	}

	public void addSolidDraw(long drawData) {
		this.addToBatch(this.solidFirst, this.solidCount, drawData, this.solidEmptyDraw++);
	}

	public void addTranslucentDraw(long drawData) {
		this.addToBatch(this.translucentFirst, this.translucentCount, drawData, this.translucentEmptyDraw++);
	}

	public void bindSolid() {
		this.solidBuffer.vertexBuffer.bind();
	}

	public void bindTranslucent() {
		this.translucentBuffer.vertexBuffer.bind();
	}

	private void addToBatch(long first, long count, long drawData, int drawIndex) {
		MemoryUtil.memPutInt(drawIndex * 4L + first, RegionAllocation.unpackFirst(drawData));
		MemoryUtil.memPutInt(drawIndex * 4L + count, RegionAllocation.unpackCount(drawData));
	}

	public void draw(long first, long count, int drawCount) {
		GL15.nglMultiDrawArrays(GL15.GL_QUADS, first, count, drawCount);
	}

	public static int regionIndex(int x, int y, int z) {
		return x << 0 | y << 2 | z << 4;
	}
}
