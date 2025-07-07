package turniplabs.examplemod.client.render.region;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.world.chunk.ChunkSection;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import turniplabs.examplemod.client.render.SectionRender;
import turniplabs.examplemod.client.render.gl.GlVertexBuffer;
import turniplabs.examplemod.client.vertex.format.DefaultVertexFormats;
import turniplabs.examplemod.client.vertex.writer.TerrainVertexWriter;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

// Section renderer container 4x4x4.
public class RegionRender {
	public int regionX, regionY, regionZ;

	// The size is not equal to the size of renderers in a region as some null allocations are saved
	// to be reused afterwards to avoid fragmentation.
	public final ObjectArrayList<Allocation> allocations = new ObjectArrayList<Allocation>();

	public @Nullable GlVertexBuffer vertexBuffer; // Doesn't allocate VBO handle until it is really needed.

	// public int maxSpaceFree; // used for detecting early if a allocation space can be reused
	public int freeNullAllocs = 0; // number of wasted spaces to be later re-used or fragmente if many wasted.
	public int maxCurrentOffset;

	// Convert into long array.
	public ObjectArrayList<Allocation> drawBatch = new ObjectArrayList<>();

	public IntArrayList count = new IntArrayList();
	public IntArrayList first = new IntArrayList();

	public RegionRender(int chunkX, int chunkY, int chunkZ) {
		this.regionX = chunkX >>> 2;
		this.regionY = chunkY >>> 2;
		this.regionZ = chunkZ >>> 2;
	}

	public void uploadAllocation(Allocation alloc) {
		boolean couldAllocate = false;

		if (this.freeNullAllocs > 0) {
			couldAllocate = tryAllocInFreeSpace(alloc);
		}

		if (!couldAllocate) {
			allocateAtTheEnd(alloc);
		}
	}

	public void prepareDrawBatch() {
		this.drawBatch.clear();

		for (int i = 0; i < this.allocations.size(); i++) {
			Allocation alloc = this.allocations.get(i);

			if (alloc.sourceRender != null) {
				this.count.add(alloc.size / TerrainVertexWriter.STRIDE);
				this.first.add(alloc.offset / TerrainVertexWriter.STRIDE);
			}
		}
	}

	public void multiDrawRegion() {
		int[] first = this.first.elements();
		int[] count = this.count.elements();

		GL15.glMultiDrawArrays(GL11.GL_QUADS, first, count);
	}

	// Lookup if there is a space to allocate,
	public boolean tryAllocInFreeSpace(Allocation newAlloc) {
		for (int i = 0; i < this.allocations.size(); i++) {
			Allocation alloc = this.allocations.get(i);

			if (alloc != null && alloc.sourceRender == null && alloc.size <= newAlloc.size) {
				this.getVertexBuffer().uploadAt(newAlloc.vertexData, alloc.offset);

				newAlloc.vertexData.clear();
				newAlloc.vertexData = null;
				newAlloc.offset = alloc.offset;

				this.allocations.set(i, newAlloc);
				this.freeNullAllocs--;

				if ((newAlloc.size + newAlloc.offset) > this.maxCurrentOffset) {
					this.maxCurrentOffset = newAlloc.size + newAlloc.offset;
				}

				return true;
			}
		}

		return false;
	}

	// Done when deleting renderer or when starting a mesh event
	// in a compiled section/render.
	public void freeAlloc(SectionRender render) {
		for (int i = 0; i < this.allocations.size(); i++) {
			Allocation alloc = this.allocations.get(i);

			if (alloc != null && alloc.sourceRender == render) {
				alloc.sourceRender = null;
				this.freeNullAllocs++;
			}
		}
	}

	// Not free allocations to be reused, allocate further in space.
	public void allocateAtTheEnd(Allocation alloc) {
		this.getVertexBuffer().uploadAt(alloc.vertexData, this.maxCurrentOffset);
		alloc.vertexData.clear();
		alloc.vertexData = null;

		this.allocations.add(alloc);
	}

	public @NotNull GlVertexBuffer getVertexBuffer() {
		if (this.vertexBuffer == null) {
			this.vertexBuffer = new GlVertexBuffer(DefaultVertexFormats.TERRAIN_FORMAT);
		}

		return this.vertexBuffer;
	}

	// TODO: Use a array to separate allocations for each face in the renderer for back-face culling.
	// Hold the allocation data for each renderer in a region.
	// Some aclarations:
	// - If the sourceRender is NULL it means that the allocation has to be cleared or reused.
	// - If the vertexData is NULL it means that the data to allocate is already allocated in the region.
	public class Allocation {
		public SectionRender sourceRender;
		public ByteBuffer vertexData;
		public int offset;
		public int size;
	}
}
