package dev.safixo.client.render.pipelines.terrain.region;

import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.gfx.util.GlBufferUtil;
import dev.safixo.client.render.gfx.util.RenderBuffer;
import dev.safixo.client.render.pipelines.terrain.SectionSet;
import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.util.MathExt;
import org.lwjgl.opengl.*;
import dev.safixo.client.render.vertex.writers.TerrainFormat;

import java.nio.ByteBuffer;

public class RegionAllocation {
	private static final int SPARE_BUFFER_ALLOC = 1024 * 1024 * 32;
	private static final int STRIDE = TerrainFormat.STRIDE;

	private static final int TRANSLUCENT_MIN_ALLOC = 1024 * 512;
	private static final int SOLID_MIN_ALLOC = 4 * 1024 * 1024;

	public RenderBuffer vertexBuffer;

	public long offset;
	public long capacity;

	private Allocation firstEntry;
	private Allocation freeAllocations;

	public static GlVertexBuffer SPARE_BUFFER;

	public RegionAllocation(int size, int pass) {
		this(Math.max(size, pass == RegionRender.SOLID_PASS ? SOLID_MIN_ALLOC : TRANSLUCENT_MIN_ALLOC));
	}

	public RegionAllocation(int size) {
		if (SPARE_BUFFER == null) {
			SPARE_BUFFER = new GlVertexBuffer(SPARE_BUFFER_ALLOC, GL15.GL_STREAM_COPY);
		}

		WorldManager.getCurrentInstance().addMemory(size);
		this.vertexBuffer = new RenderBuffer(size, GL15.GL_STATIC_DRAW);
		this.capacity = size;
	}

	private long getAmplification(long size) {
		return (size << 1);
	}

	private void resize(long size) {
		if (SPARE_BUFFER == null) {
			return;
		}

		long newSize = getAmplification(size);

		// If there's a possibility of avoiding using a temp-buffer for the geometry
		// it's preferable.
		if (newSize > SPARE_BUFFER_ALLOC && size <= SPARE_BUFFER_ALLOC) {
			newSize = SPARE_BUFFER_ALLOC;
		}

		WorldManager worldManager = WorldManager.getCurrentInstance();
		SectionSet sectionSet = worldManager.getSectionSet();

		// If it has been overpassed the copying buffer limit, do mental gymnastics.
		if (newSize > SPARE_BUFFER_ALLOC) {
			// If the region is more than 32MB avoid allocating a temporal buffer as is preferred
			// to not duplicate that much memory.
			if (this.offset > 40 * 1024 * 1024) {
				this.vertexBuffer.allocateSpace((int) newSize, GL15.GL_STATIC_DRAW);

				Allocation alloc = this.firstEntry;

				while (alloc != null) {
					long position = alloc.position;

					int sectionX = MathExt.decodeX(position);
					int sectionY = MathExt.decodeY(position);
					int sectionZ = MathExt.decodeZ(position);

					sectionSet.markDirty(sectionX, sectionY, sectionZ);

					alloc = alloc.next;
				}

			} else {
				// Allocate a temporal buffer to hold region memory.
				GlVertexBuffer regionBuffer = this.vertexBuffer.getVertexBuffer();
				GlVertexBuffer spareBuffer = new GlVertexBuffer((int) this.offset, GL15.GL_DYNAMIC_COPY);

				GlBufferUtil.copyBufferToBuffer(regionBuffer, spareBuffer, 0, 0, (int) this.offset);

				this.vertexBuffer.allocateSpace((int) newSize, GL15.GL_STATIC_DRAW);

				GlBufferUtil.copyBufferToBuffer(spareBuffer, regionBuffer, 0, 0, (int) this.offset);

				spareBuffer.delete();
			}

			this.capacity = newSize;
			return;
		}

		GlVertexBuffer regionBuffer = this.vertexBuffer.getVertexBuffer();
		GlVertexBuffer spareBuffer = SPARE_BUFFER;

		GlBufferUtil.copyBufferToBuffer(regionBuffer, spareBuffer, 0, 0, (int) this.offset);

		this.vertexBuffer.allocateSpace((int) newSize, GL15.GL_STATIC_DRAW);

		GlBufferUtil.copyBufferToBuffer(spareBuffer, regionBuffer, 0, 0, (int) this.offset);

		this.capacity = newSize;
	}

	// When freeing all the allocations of the region, is also wanted to avoid any interference
	// with the sections and the invalid region/allocation.
	public void clear() {
		WorldManager worldManager = WorldManager.getCurrentInstance();
		SectionSet sectionSet = worldManager.getSectionSet();

		worldManager.removeMemory((int) this.capacity);

		this.vertexBuffer.delete();
		this.capacity = 0;
		this.offset = 0;

		Allocation alloc = this.firstEntry;

		while (alloc != null) {
			long position = alloc.position;

			int sectionX = MathExt.decodeX(position);
			int sectionY = MathExt.decodeY(position);
			int sectionZ = MathExt.decodeZ(position);

			sectionSet.markDirty(sectionX, sectionY, sectionZ);
			worldManager.removeUsedMemory(alloc.count * STRIDE);

			alloc = alloc.next;
		}

		this.firstEntry = null;
		this.freeAllocations = null;
	}

	public boolean isEmpty() {
		return this.firstEntry == null;
	}

	private long createAllocation(long position, ByteBuffer buffer, int size) {
		Allocation alloc = this.fitInFree(size);

		if (alloc == null) {
			alloc = this.addAllocation(position, size);
		}

		alloc.position = position;
		WorldManager.getCurrentInstance().addUsedMemory(alloc.count * STRIDE);

		this.sumbitToBuffer(alloc, buffer, size);
		return packDrawData(size, (int) alloc.first);
	}

	private Allocation addAllocation(long position, int size) {
		long maxOffset = this.offset / STRIDE;
		int sizeInBytes = size * STRIDE;

		WorldManager.getCurrentInstance().removeMemory((int) this.capacity);

		if (this.offset + sizeInBytes > this.capacity) {
			this.resize(this.offset + sizeInBytes);
		}

		WorldManager.getCurrentInstance().addMemory((int) this.capacity);

		Allocation newAlloc = new Allocation(position, (int) maxOffset, size);
		Allocation first = this.firstEntry;
		this.offset += sizeInBytes;

		if (first == null) {
			return this.firstEntry = newAlloc;
		}

		while (first.next != null) {
			first = first.next;
		}

		first.next = newAlloc;
		return newAlloc;
	}

	public long allocate(long position, ByteBuffer buffer, int vertices) {
		Allocation alloc = this.findPrevAlloc(position);
		long drawData;

		if (alloc != null && alloc.count >= vertices) {
			this.sumbitToBuffer(alloc, buffer, vertices);
			drawData = packDrawData(vertices, (int) alloc.first);
		} else {
			if (alloc != null) {
				this.remove(position);
			}
			drawData = this.createAllocation(position, buffer, vertices);
		}

		return drawData;
	}

	// Searches for a space in allocations already freed.
	private Allocation fitInFree(int spaceNeeded) {
		Allocation alloc = this.freeAllocations;

		// Base case: isn't any free space.
		if (alloc == null) {
			return null;
		}

		// Base case: first meets the criteria.
		if (alloc.count >= spaceNeeded) {
			this.freeAllocations = this.freeAllocations.next;
			alloc.next = null;

			return alloc;
		}

		while (alloc.next != null && alloc.next.count < spaceNeeded) {
			alloc = alloc.next;
		}

		// Didn't find any free allocation that meet the space needs.
		if (alloc.next == null) {
			return null;
		}

		Allocation returnAlloc = alloc.next;

		// If next isn't null, we find the alloc we wanted.
		alloc.next = alloc.next.next;
		returnAlloc.next = null;

		return returnAlloc;
	}

	// Searches for a previous allocation.
	public Allocation findPrevAlloc(long position) {
		Allocation alloc = this.firstEntry;

		while (alloc != null && alloc.position != position) {
			alloc = alloc.next;
		}

		return alloc;
	}

	public static long packDrawData(int count, int first) {
		return (first & 0xFFFFFFFFL) << 32L | (count & 0xFFFFFFFFL);
	}

	public static int unpackFirst(long drawData) {
		return (int) (drawData >>> 32L);
	}

	public static int unpackCount(long drawData) {
		return (int) (drawData & 0xFFFFFFFFL);
	}

	// Removes allocation from the main pool, and saves in the free pool.
	public void remove(long position) {
		Allocation alloc = this.firstEntry;

		// shouldn't happen
		if (alloc == null) {
			return;
		}

		// The allocation shouldn't be null as we are removing an existent
		// allocation.
		if (alloc.position == position) {
			this.firstEntry = this.firstEntry.next;
			this.addToFreeList(alloc);
		} else {
			while (alloc.next != null && alloc.next.position != position) {
				alloc = alloc.next;
			}

			// This shouldn't happen, but in the life there's a lot of things that shouldn't,
			// but they still happen, right?
			if (alloc.next == null) {
				return;
			}

			// alloc.next == render && alloc.next.size == side
			Allocation next = alloc.next;
			alloc.next = next.next;

			this.addToFreeList(next);
		}
	}

	private void addToFreeList(Allocation alloc) {
		Allocation free = this.freeAllocations;

		WorldManager.getCurrentInstance().removeUsedMemory(alloc.count * STRIDE);

		alloc.position = Long.MIN_VALUE;
		alloc.next = null;

		// Save removed alloc in free pool.
		if (free != null) {
			while (free.next != null) {
				free = free.next;
			}

			free.next = alloc;
		} else {
			this.freeAllocations = alloc;
		}
	}

	public void sumbitToBuffer(Allocation alloc, ByteBuffer buffer, int size) {
		this.vertexBuffer.upload(buffer, (alloc.first * STRIDE), size * STRIDE);
	}

	public static class Allocation {
		public Allocation next;
		public long position;

		public Allocation(long render, int first, int count) {
			this.position = render;
			this.first = first;
			this.count = count;
		}

		// Size and offset are written in vertex amount and not bytes to
		// avoid division to translate byte sizes to vertex counts.
		public int first;
		public int count;
	}
}
