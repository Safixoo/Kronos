package turniplabs.examplemod.client.render.region;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.render.SectionManager;
import turniplabs.examplemod.client.render.SectionRender;
import turniplabs.examplemod.client.vertex.VertexWriterManager;
import turniplabs.examplemod.client.vertex.writer.TerrainVertexWriter;

public class RegionRender {
	public int regionX, regionY, regionZ;

	private RegionAllocation translucentBuffer;
	private RegionAllocation solidBuffer;

	public static final long emptyIndices = MemoryUtil.nmemCalloc(0, 8192 * 12);

	public final long solidFirst;
	public final long solidCount;
	public int solidEmptyDraw = 0;

	public final long translucentFirst = MemoryUtil.nmemAlloc(256 * 4);
	public final long translucentCount = MemoryUtil.nmemAlloc(256 * 4);
	public int translucentEmptyDraw = 0;

	public int currentFrame;

	public RegionRender(RegionManager regionManager, int sectionX, int sectionY, int sectionZ) {
		this.regionX = sectionX >> 3;
		this.regionY = sectionY >> 2;
		this.regionZ = sectionZ >> 3;

		long ptrSolidData = MemoryUtil.nmemAlloc(256 * 4 * 14);

		this.solidFirst = ptrSolidData;
		this.solidCount = ptrSolidData + (256 * 4 * 7);
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

		MemoryUtil.nmemFree(this.translucentFirst);
		MemoryUtil.nmemFree(this.translucentCount);
	}

	public void addSolidMesh(SectionRender render, VertexWriterManager manager, int side) {
		if (this.solidBuffer == null) {
			this.solidBuffer = new RegionAllocation(manager.getVertices() * TerrainVertexWriter.STRIDE);
		}

		render.solidDrawFaces[side] = this.solidBuffer.renewAllocation(render, manager.getVertexData(), manager.getVertices(), side);
	}

	public void addTranslucentMesh(SectionRender render, VertexWriterManager manager) {
		if (this.translucentBuffer == null) {
			this.translucentBuffer = new RegionAllocation(manager.getVertices() * TerrainVertexWriter.STRIDE);
		}

		render.transDrawData = this.translucentBuffer.renewAllocation(render, manager.getVertexData(), manager.getVertices(), 0);
	}

	public void addSolidDraw(long drawData) {
		this.addToBatch(this.solidFirst, this.solidFirst + (256 * 4 * 7), drawData, this.solidEmptyDraw++);
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
		MemoryUtil.memPutInt((drawIndex << 2L) + first, RegionAllocation.unpackFirst(drawData));
		MemoryUtil.memPutInt((drawIndex << 2L) + count, RegionAllocation.unpackCount(drawData));
	}

	public void draw(long first, long count, int drawCount) {
		GL14C.nglMultiDrawArrays(GL15.GL_QUADS, first, count, drawCount);
	}

	public static int regionIndex(int x, int y, int z) {
		return x << 0 | y << 2 | z << 4;
	}
}
