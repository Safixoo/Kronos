package dev.safixo.client.render.pipelines.terrain.region.allocation;

import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.util.GlBufferUtil;
import dev.safixo.client.render.pipelines.terrain.SectionFlags;
import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.render.pipelines.terrain.region.RegionBuffer;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;
import dev.safixo.client.render.vertex.writers.TerrainFormat;
import org.lwjgl.opengl.GL15;

import java.util.Arrays;

public class NewRegionAllocator {
	private static final int COPY_BUFFER_SIZE = 32 << 20;
	public static GlVertexBuffer COPY_BUFFER;

	private NewAllocation start, head;
	private NewAllocation free;

	private long capacity;
	private long offset;

	private final RegionBuffer vertexBuffer;

	private static final int STRIDE = TerrainFormat.STRIDE;
	private static final int MIN_ALLOCATION = 4 << 20;

	public NewRegionAllocator(int size) {
		size = Math.max(MIN_ALLOCATION, size);

		this.vertexBuffer = new RegionBuffer(size, GL15.GL_STATIC_DRAW);
		this.capacity = size;

		if (COPY_BUFFER == null) {
			COPY_BUFFER = new GlVertexBuffer(COPY_BUFFER_SIZE, GL15.GL_STATIC_COPY);
		}
	}

	private NewAllocation uploadGeometry(SectionRender section, int pass, long ptr, int size) {
		NewAllocation alloc = this.getAllocation(section, pass, size);
		alloc.sizeBytes = size;
		alloc.section = section;

		this.vertexBuffer.upload(ptr, (int) alloc.offsetBytes, (int) alloc.sizeBytes);
		return alloc;
	}

	public long uploadGeometrySolid(SectionRender section, NewCopyJoiner.CopyMetadata copyMetadata, long ptr, int size) {
		NewAllocation alloc = this.uploadGeometry(section, RegionRender.SOLID_PASS, ptr, size);
		alloc.addFacingOffsets(copyMetadata.directionOffsets);

		return packDrawData(alloc.sizeBytes / STRIDE,  alloc.offsetBytes / STRIDE);
	}

	public long uploadGeometryTranslucent(SectionRender section, long ptr, int size) {
		NewAllocation alloc = this.uploadGeometry(section, RegionRender.TRANSLUCENT_PASS, ptr, size);

		return packDrawData(alloc.sizeBytes / STRIDE,  alloc.offsetBytes / STRIDE);
	}

	public static long packDrawData(long count, long first) {
		return (first & 0xFFFFFFFFL) << 32L | (count & 0xFFFFFFFFL);
	}

	private NewAllocation getAllocation(SectionRender section, int pass, int size) {
		long id = NewAllocation.getId(section, pass);
		NewAllocation alloc = this.getById(id);

		if (alloc != null) {
			if (alloc.sizeBytes >= size) {
				return alloc;
			} else {
				this.removeAllocation(alloc);
			}
		}

		alloc = getFreeAllocation(size);

		if (alloc != null) {
			return alloc;
		}

		alloc = newAllocation(id, size);
		return alloc;
	}

	private NewAllocation newAllocation(long id, int size) {
		if (size + this.offset >= this.capacity) {
			this.resize(size + this.offset + 1);
		}

		NewAllocation head = this.head;
		NewAllocation newAlloc = new NewAllocation();

		newAlloc.sizeBytes = size;
		newAlloc.offsetBytes = this.offset;
		newAlloc.id = id;

		this.offset += size;

		if (head == null) {
			this.head = this.start = newAlloc;
			return newAlloc;
		}

		this.head.nextNode = newAlloc;
		this.head = newAlloc;

		return newAlloc;
	}

	private NewAllocation getById(long id) {
		NewAllocation alloc = this.start;

		while (alloc != null && alloc.id != id) {
			alloc = alloc.nextNode;
		}

		return alloc;
	}

	private void removeAllocation(NewAllocation alloc) {
		NewAllocation first = this.start;

		if (first == alloc) {
			this.head = this.start = null;
			this.addFreeAllocation(alloc);
			return;
		}

		while (first.nextNode != null) {
			if (first.nextNode == alloc) {
				first.nextNode = alloc.nextNode;
				this.addFreeAllocation(alloc);
				break;
			}

			first = first.nextNode;
		}
	}

	public void remove(SectionRender render, int pass) {
		long id = NewAllocation.getId(render, pass);
		NewAllocation alloc = this.getById(id);

		this.removeAllocation(alloc);
	}

	private void addFreeAllocation(NewAllocation alloc) {
		NewAllocation free = this.free;
		alloc.nextNode = null;
		alloc.id = -1;

		if (free == null) {
			this.free = alloc;
			return;
		}

		while (free.nextNode != null) {
			free = free.nextNode;
		}

		free.nextNode = alloc;
	}

	private NewAllocation getFreeAllocation(int size) {
		NewAllocation free = this.free;

		if (free == null) {
			return null;
		}

		NewAllocation next = free.nextNode;

		if (next == null) {
			if (free.sizeBytes >= size) {
				this.free = null;
				return free;
			}
			return null;
		}

		while (next != null) {
			if (next.sizeBytes >= size) {
				free.nextNode = next.nextNode;
			}

			free = next;
			next = next.nextNode;
		}

		return null;
	}

	private static long getAmplification(long size) {
		return size << 1;
	}

	private void resize(long size) {
		if (COPY_BUFFER == null) {
			return;
		}

		long newSize = getAmplification(size);

		// If there's a possibility of avoiding using a temp-buffer for the geometry it would be preferable.
		if (newSize > COPY_BUFFER_SIZE && size <= COPY_BUFFER_SIZE) {
			newSize = COPY_BUFFER_SIZE;
		}

		// If it has overpassed the copying buffer limit, do gymnastics.
		if (newSize > COPY_BUFFER_SIZE) {
			// If the region is more than 80MB avoid allocating a temporal buffer as is preferred
			// to not duplicate that much memory.
			if (this.offset > (80 << 20)) {
				this.vertexBuffer.allocateSpace((int) newSize, GL15.GL_STATIC_DRAW);
				NewAllocation alloc = this.start;

				while (alloc != null) {
					int flags = alloc.section.flags;
					flags = SectionFlags.setPassesNonEmpty(flags, 0b00);
					alloc.section.flags = flags;
					alloc.section.markDirty(true);
					alloc = alloc.nextNode;
				}
			} else {
				// Allocate a temporal buffer to hold region memory.
				RegionBuffer tempBuffer = new RegionBuffer((int) this.offset, GL15.GL_DYNAMIC_COPY);

				GlVertexBuffer regionBuffer = this.vertexBuffer.getVertexBuffer();
				GlVertexBuffer spareBuffer = tempBuffer.getVertexBuffer();

				GlBufferUtil.copyBufferToBuffer(regionBuffer, spareBuffer, 0, 0, (int) this.offset);
				this.vertexBuffer.allocateSpace((int) newSize, GL15.GL_STATIC_DRAW);
				GlBufferUtil.copyBufferToBuffer(spareBuffer, regionBuffer, 0, 0, (int) this.offset);

				tempBuffer.delete();
			}
			this.capacity = newSize;
		} else { // Use a intermediary copying buffer to avoid resizes.
			GlVertexBuffer regionBuffer = this.vertexBuffer.getVertexBuffer();
			GlVertexBuffer spareBuffer = COPY_BUFFER;

			GlBufferUtil.copyBufferToBuffer(regionBuffer, spareBuffer, 0, 0, (int) this.offset);
			this.vertexBuffer.allocateSpace((int) newSize, GL15.GL_STATIC_DRAW);
			GlBufferUtil.copyBufferToBuffer(spareBuffer, regionBuffer, 0, 0, (int) this.offset);

			this.capacity = newSize;
		}
	}

	public static class NewAllocation {
		public NewAllocation nextNode;

		private long id = -1;
		private long sizeBytes;
		private long offsetBytes;

		public SectionRender section;
		int[] subOffsets;

		private NewAllocation() {}

		public static long getId(SectionRender section, int pass) {
			return section.sectionPos << 1 | pass;
		}

		public void addFacingOffsets(int[] offsets) {
			this.subOffsets = Arrays.copyOf(offsets, offsets.length);
		}

		private boolean isSolid() {
			return (this.id & 0b111_111) != 0;
		}
	}
}

