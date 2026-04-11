package dev.safixo.client.render.pipelines.terrain.region.allocation;

import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.MeshDirection;
import dev.safixo.client.util.memory.UnsafeUtil;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.List;

public class NewCopyJoiner {
	private final VertexWriter copyDest = new VertexWriter(512);
	private final List<CopyMetadata> copies = new ReferenceArrayList<>();

	public void copySegmentsInLine(VertexWriter[] writers) {
		VertexWriter copyDest = this.copyDest;
		int size = 0;

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			VertexWriter writer = writers[dir];

			if (writer == null) {
				continue;
			}

			size += writer.getOffset();
		}

		copyDest.ensureCapacity(size);
		long ptr = copyDest.getTotalOffset();

		for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
			VertexWriter writer = writers[dir];

			if (writer == null) {
				continue;
			}

			int directionSize = writer.getOffset();
			UnsafeUtil.UNSAFE.copyMemory(writer.getVertexData(), ptr, directionSize);
			ptr += directionSize;
		}
	}

	public static class CopyMetadata {
		// Allocator destination.
		private final NewRegionAllocator allocator;

		// Offset from the reading pointer.
		private final int offset;

		// Size of the all the allocatiosn summed.
		private final int size;

		// Prefix-sum array of all the visible directions.
		public final int[] directionOffsets = new int[MeshDirection.COUNT];

		// All visible directions bit-set.
		private final int directionMask;

		public CopyMetadata(NewRegionAllocator allocator, int offset, VertexWriter[] writers, int size) {
			this.allocator = allocator;
			this.offset = offset;

			int directionSet = 0b0;

			for (int dir = 0; dir < MeshDirection.COUNT; dir++) {
				if (writers[dir] == null) {
					continue;
				}

				directionSet |= 1 << dir;
				this.directionOffsets[dir] = writers[dir].getOffset();
			}
			for (int dir = 1; dir < MeshDirection.COUNT; dir++) {
				this.directionOffsets[dir] += this.directionOffsets[dir - 1];
			}

			this.directionMask = directionSet;
			this.size = this.directionOffsets[MeshDirection.COUNT - 1];
		}
	}
}
