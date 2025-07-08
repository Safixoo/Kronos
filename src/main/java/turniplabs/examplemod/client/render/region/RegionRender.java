package turniplabs.examplemod.client.render.region;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.lwjgl.Sys;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import turniplabs.examplemod.client.render.SectionRender;
import turniplabs.examplemod.client.vertex.VertexWriterManager;
import turniplabs.examplemod.client.vertex.writer.TerrainVertexWriter;

public class RegionRender {
	public int regionX, regionY, regionZ;

	private RegionAllocation translucentBuffer;
	private RegionAllocation solidBuffer;

	public boolean solidEmptyDraw = true;
	public final IntArrayList solidCount = new IntArrayList();
	public final IntArrayList solidFirst = new IntArrayList();

	public boolean translucentEmptyDraw = true;
	public final IntArrayList translucentCount = new IntArrayList();
	public final IntArrayList translucentFirst = new IntArrayList();

	private final RegionManager regionManager;

	private long solidMask = 0;
	private long translucentMask = 0;
	public int currentFrame;

	public RegionRender(RegionManager regionManager, int sectionX, int sectionY, int sectionZ) {
		this.regionManager = regionManager;

		this.regionX = sectionX >> 2;
		this.regionY = sectionY >> 2;
		this.regionZ = sectionZ >> 2;
	}

	public int getChunkX() {
		return this.regionX << 2;
	}

	public int getChunkY() {
		return this.regionY << 2;
	}

	public int getChunkZ() {
		return this.regionZ << 2;
	}

	private int getRegionIndex(SectionRender render) {
		int relX = (render.blockX >> 4) - this.getChunkX();
		int relY = (render.blockY >> 4) - this.getChunkY();
		int relZ = (render.blockZ >> 4) - this.getChunkZ();

		return regionIndex(relX, relY, relZ);
	}

	public void addSolidMesh(SectionRender render, VertexWriterManager manager) {
		if (this.solidBuffer == null) {
			this.solidBuffer = new RegionAllocation(manager.getVertices() * TerrainVertexWriter.STRIDE);
		}

		long regionBitIndex = 1L << getRegionIndex(render);

		if ((this.solidMask & regionBitIndex) == 0) {
			render.solidDraw = this.solidBuffer.allocate(render, manager.getVertexData(), manager.getVertices());
		} else {
			render.solidDraw = this.solidBuffer.renewAllocation(render, manager.getVertexData(), manager.getVertices());
		}

		this.solidMask |= regionBitIndex;
	}

	public void addTranslucentMesh(SectionRender render, VertexWriterManager manager) {
		if (this.translucentBuffer == null) {
			this.translucentBuffer = new RegionAllocation(manager.getVertices() * TerrainVertexWriter.STRIDE);
		}

		long regionBitIndex = 1L << getRegionIndex(render);

		if ((this.translucentMask & regionBitIndex) == 0) {
			render.translucentDraw = this.translucentBuffer.allocate(render, manager.getVertexData(), manager.getVertices());
		} else {
			render.translucentDraw = this.translucentBuffer.renewAllocation(render, manager.getVertexData(), manager.getVertices());
		}

		this.translucentMask |= regionBitIndex;
	}

	public void addSolidDraw(long drawData) {
		this.addToBatch(this.solidFirst, this.solidCount, drawData);
		this.solidEmptyDraw = false;
	}

	public void addTranslucentDraw(long drawData) {
		this.addToBatch(this.translucentFirst, this.translucentCount, drawData);
		this.translucentEmptyDraw = false;
	}

	public void bindSolid() {
		this.solidBuffer.vertexBuffer.bind();
	}

	public void bindTranslucent() {
		this.translucentBuffer.vertexBuffer.bind();
	}

	private void addToBatch(IntArrayList first, IntArrayList count, long drawData) {
		count.add(RegionAllocation.unpackCount(drawData));
		first.add(RegionAllocation.unpackFirst(drawData));
	}

	public void draw(IntArrayList first, IntArrayList count) {
		long firstPtr = MemoryUtil.nmemAlloc(64 * 4);
		long countPtr = MemoryUtil.nmemAlloc(64 * 4);

		int nonEmpty = 0;

		for (int i = 0; i < first.size(); i++) {
			if (count.getInt(i) == 0) {
				continue;
			}

			MemoryUtil.memPutInt((nonEmpty * 4L) + firstPtr, first.getInt(i));
			MemoryUtil.memPutInt((nonEmpty * 4L) + countPtr, count.getInt(i));
			nonEmpty++;
		}

		GL15.nglMultiDrawArrays(GL15.GL_QUADS, firstPtr, countPtr, nonEmpty);

		first.clear();
		count.clear();
	}

	public static int regionIndex(int x, int y, int z) {
		return x << 0 | y << 2 | z << 4;
	}
}
