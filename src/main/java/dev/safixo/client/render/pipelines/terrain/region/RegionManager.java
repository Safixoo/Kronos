package dev.safixo.client.render.pipelines.terrain.region;

import dev.safixo.client.render.gfx.buffer.GlBuffer;
import dev.safixo.client.render.gfx.buffer.GlIndexBuffer;
import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.pipelines.terrain.SectionSet;
import dev.safixo.client.render.pipelines.terrain.sorting.IndexBufferWriter;
import dev.safixo.client.util.collection.FastLongHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.*;
import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.render.pipelines.terrain.shader.TerrainProgram;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.MathExt;

import java.util.List;

public class RegionManager {
	private static final int BUFFER_CAPACITY = 24 << 20;
	public static boolean SUPPORT_INDIRECT;

	private GlBuffer spareBuffer;
	private GlAllocator<GlIndexBuffer> globalIndexBuffer;

	public final FastLongHashMap<RegionRender> regionMap = new FastLongHashMap<>();

	private RegionRender[] indexedRegions;
	private int addRegionX, addRegionZ;
	private int factorY, factorZ;

	private IndexBufferWriter indexBufferWriter = new IndexBufferWriter();

	public RegionManager() {
		String vendor = GL11.glGetString(GL11.GL_VENDOR);
		SUPPORT_INDIRECT = GLContext.getCapabilities().GL_ARB_multi_draw_indirect && !vendor.contains("Intel");
	}

	public RegionRender getRegion(int sectionX, int sectionY, int sectionZ) {
		int regionX = sectionX >> (RegionConstants.BLOCK_SHIFT_X - 4);
		int regionY = sectionY >> (RegionConstants.BLOCK_SHIFT_Y - 4);
		int regionZ = sectionZ >> (RegionConstants.BLOCK_SHIFT_Z - 4);

		long position = MathExt.asLong(regionX, regionY, regionZ);
		RegionRender region = this.regionMap.get(position);

		if (region == null) {
			region = new RegionRender(this, sectionX, sectionY, sectionZ);
			this.regionMap.put(position, region);
		}

		return region;
	}

	public int getVertexMemoryUsed() {
		int sum = 0;

		for (RegionRender region : this.regionMap) {
			sum += region.getAllocator().getAllocationSum();
		}

		return sum;
	}

	public int getVertexMemoryTotal() {
		int sum = 0;

		for (RegionRender region : this.regionMap) {
			sum += region.getAllocator().getCapacity();
		}

		return sum;
	}

	public int getIndexMemoryTotal() {
		return this.globalIndexBuffer == null ? 0 : this.globalIndexBuffer.getCapacity();
	}

	public int getIndexMemoryUsed() {
		return this.globalIndexBuffer == null ? 0 : this.globalIndexBuffer.getAllocationSum();
	}

	public GlBuffer getSpareBuffer() {
		if (this.spareBuffer == null) {
			this.spareBuffer = new GlVertexBuffer(BUFFER_CAPACITY, GL15.GL_STATIC_COPY);
		}

		return this.spareBuffer;
	}

	// Creates a mapping between position and regions, useful for BFS only.
	public void saveIndexedRegions(SectionSet sectionSet, CameraData camera) {
		int regionCameraX = (camera.intX >> RegionConstants.BLOCK_SHIFT_X);
		int regionCameraZ = (camera.intZ >> RegionConstants.BLOCK_SHIFT_Z);

		int factorXZ = Integer.MIN_VALUE;
		int factorY = 256 >> RegionConstants.BLOCK_SHIFT_Y;

		for (RegionRender region : this.regionMap) {
			int regionX = Math.abs(region.regionX - regionCameraX);
			int regionZ = Math.abs(region.regionZ - regionCameraZ);

			factorXZ = Math.max(factorXZ, Math.max(regionX, regionZ));
		}
		factorXZ++;

		RegionRender[] indexedRegions = new RegionRender[MathExt.square(factorXZ * 2 + 1) * factorY];

		regionCameraX = -regionCameraX + factorXZ;
		regionCameraZ = -regionCameraZ + factorXZ;

		for (RegionRender region : this.regionMap) {
			int regionX = region.regionX + regionCameraX;
			int regionY = region.regionY;
			int regionZ = region.regionZ + regionCameraZ;

			// Region out-of-bounds.
			if (regionX < 0 || regionX > factorXZ * 2 || regionZ < 0 || regionZ > factorXZ * 2) {
				continue;
			}

			indexedRegions[regionX + (regionY + regionZ * factorY) * factorXZ] = region;
		}

		this.factorY = factorXZ;
		this.factorZ = factorXZ * factorY;

		this.addRegionX = regionCameraX;
		this.addRegionZ = regionCameraZ;

		this.indexedRegions = indexedRegions;
	}

	public RegionRender getRegionFromIndexed(int sectionX, int sectionY, int sectionZ) {
		int regionX = (sectionX >> (RegionConstants.BLOCK_SHIFT_X - 4)) + this.addRegionX;
		int regionY = (sectionY >> (RegionConstants.BLOCK_SHIFT_Y - 4));
		int regionZ = (sectionZ >> (RegionConstants.BLOCK_SHIFT_Z - 4)) + this.addRegionZ;

		return this.indexedRegions[regionX + (regionY * this.factorY) + (regionZ * this.factorZ)];
	}

	public void clear() {
		for (RegionRender region : this.regionMap) {
			region.clear();
		}
		this.regionMap.clear();
		if (this.indexBufferWriter != null) {
			this.indexBufferWriter.delete();
			this.indexBufferWriter = null;
		}

		if (this.globalIndexBuffer != null) {
			this.globalIndexBuffer.delete();
			this.globalIndexBuffer = null;
		}

		if (this.spareBuffer != null) {
			this.spareBuffer.delete();
			this.spareBuffer = null;
		}
	}

	private RegionRender[] getRegionsSorted(CameraData camera) {
		if (this.regionMap.isEmpty()) {
			return null;
		}

		RegionRender[] regions = this.regionMap.toArray(RegionRender.class);
		int[] distances = new int[regions.length];
		int maxRegionDistance = Integer.MIN_VALUE;

		for (int i = 0; i < regions.length; i++) {
			maxRegionDistance = Math.max(maxRegionDistance, distances[i] = manhattanDistance(regions[i], camera));
		}
		maxRegionDistance += 1;

		int[] indices = new int[regions.length];
		int[] hist = new int[maxRegionDistance];

		for (int i = 0; i < regions.length; i++) {
			hist[distances[i]]++;
		}

		// turns histogram into a prefix-sum array.
		for (int i = 1; i < maxRegionDistance; i++) {
			hist[i] += hist[i - 1];
		}

		for (int i = 0; i < regions.length; i++) {
			indices[--hist[distances[i]]] = i;
		}

		RegionRender[] regionSorted = new RegionRender[regions.length];

		for (int i = 0; i < indices.length; i++) {
			regionSorted[i] = regions[indices[i]];
		}

		return regionSorted;
	}

	private static int manhattanDistance(RegionRender region, CameraData camera) {
		int pX = camera.intX >> RegionConstants.BLOCK_SHIFT_X;
		int pY = camera.intY >> RegionConstants.BLOCK_SHIFT_Y;
		int pZ = camera.intZ >> RegionConstants.BLOCK_SHIFT_Z;

		int rX = region.regionX;
		int rY = region.regionY;
		int rZ = region.regionZ;

		return Math.abs(pX - rX) + Math.abs(pY - rY) + Math.abs(pZ - rZ);
	}

	public void update(Profiler profiler, SectionSet sectionSet, CameraData camera, int renderDistance, boolean worldUpdated) {
		profiler.startSection("update_regions");

		if (worldUpdated) {
			this.clear();
		} else {
			this.sanitizeRegions(sectionSet, camera);
		}

		profiler.endSection();
	}

	public void updateTranslucency(CameraData camera) {
		if (this.indexBufferWriter == null) {
			this.indexBufferWriter = new IndexBufferWriter();
		}

		for (RegionRender region : this.regionMap) {
			if (region.getRenderIndex() == 0) {
				continue;
			}

			region.updateTranslucency(this.indexBufferWriter, camera);
		}
	}

	public void iterateAllTileEntities(SectionSet sectionSet, List<TileEntity> globalList) {
		globalList.clear();

		for (RegionRender region : this.regionMap) {
			if (!region.hasTileEntities()) {
				continue;
			}

			region.getTileEntityManager().iterateTileEntities(sectionSet, globalList);
		}
	}

	public void sanitizeRegions(SectionSet sectionSet, CameraData camera) {
		LongArrayList forRemoval = new LongArrayList();

		for (RegionRender region : this.regionMap) {
			if (!isAnyRegionSectionActive(sectionSet, camera, region)) {
				long regionPos = MathExt.asLong(region.regionX, region.regionY, region.regionZ);

				forRemoval.add(regionPos);
				region.clear();
			}
		}

		for (long position : forRemoval) {
			this.regionMap.remove(position);
		}
	}

	public IndexBufferWriter getIndexBufferWriter() {
		if (this.indexBufferWriter == null) {
			this.indexBufferWriter = new IndexBufferWriter();
		}

		return this.indexBufferWriter;
	}

	public GlAllocator<GlIndexBuffer> getGlobalIndexBuffer() {
		if (this.globalIndexBuffer == null) {
			this.globalIndexBuffer = this.createGlobalIndexBuffer();
		}

		return this.globalIndexBuffer;
	}

	private GlAllocator<GlIndexBuffer> createGlobalIndexBuffer() {
		GlAllocator<GlIndexBuffer> globalIndexBuffer = new GlAllocator<>(
			new GlIndexBuffer(GL15.GL_STATIC_DRAW), this.getSpareBuffer(), IndexBufferWriter.STRIDE_PER_QUAD << 16, false);

		if (this.indexBufferWriter == null) {
			this.indexBufferWriter = new IndexBufferWriter();
		}

		this.indexBufferWriter.restart();
		this.indexBufferWriter.ensureQuadCapacity((1 << 16) / 4);

		for (int i = 0; i < (1 << 16) / 4; i++) {
			this.indexBufferWriter.writeQuad(i);
		}

		globalIndexBuffer.allocate(-1, (IndexBufferWriter.STRIDE_PER_QUAD << 16) / 4);
		globalIndexBuffer.getBuffer().bufferSubData(this.indexBufferWriter.getPtrNio(), 0, 0, (IndexBufferWriter.STRIDE_PER_QUAD << 16) / 4);

		return globalIndexBuffer;
	}

	private static boolean isAnyRegionSectionActive(SectionSet sectionSet, CameraData camera, RegionRender region) {
		int minX = (region.regionX << (RegionConstants.BLOCK_SHIFT_X - 4)) - (camera.intX >> 4);
		int minZ = (region.regionZ << (RegionConstants.BLOCK_SHIFT_Z - 4)) - (camera.intZ >> 4);

		int maxX = minX + (RegionConstants.DIAMETER_X >> 4);
		int maxZ = minZ + (RegionConstants.DIAMETER_Z >> 4);

		int sectionX = Math.min(maxX, Math.max(0, minX)) + (camera.intX >> 4);
		int sectionZ = Math.min(maxZ, Math.max(0, minZ)) + (camera.intZ >> 4);

		return sectionSet.isInBounds(sectionX, sectionZ);
	}

	public void drawAllRegions(WorldManager manager, TerrainProgram shader, CameraData camera, int pass) {
		RegionRender[] regionRenders = this.getRegionsSorted(camera);

		if (regionRenders == null) {
			return;
		}

		int index;
		int end;
		int inc;

		if (pass == RegionConstants.TRANSLUCENT_PASS) {
			index = regionRenders.length - 1;
			end = -1;
			inc = -1;
		} else {
			index = 0;
			end = regionRenders.length;
			inc = 1;
		}

		while (index != end) {
			RegionRender region = regionRenders[index];

			region.prepareAndDraw(manager, shader, camera, pass);

			if (pass == 0) {
				manager.drawnSolidRenderers += region.getRenderIndex();
			}

			index += inc;
		}

		GL30.glBindVertexArray(0);

		if (pass == RegionConstants.TRANSLUCENT_PASS) {
			this.globalIndexBuffer.getBuffer().unbind();
		}
	}
 }
