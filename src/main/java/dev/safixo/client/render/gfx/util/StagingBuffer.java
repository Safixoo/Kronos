package dev.safixo.client.render.gfx.util;

import dev.safixo.client.render.ImprovedTessellator;
import dev.safixo.client.render.gfx.buffer.GlBuffer;
import dev.safixo.client.render.gfx.buffer.GlVertexBuffer;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.memory.UnsafeUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.lwjgl.MemoryUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class StagingBuffer {
	private static final boolean USE_MAPPING = false;

	private static final int BUFFER_CAPACITY = 16 << 20;
	private static final int FRAMES_PER_SYNC = 4;
	private static StagingBuffer INSTANCE;

	private GlVertexBuffer stagingBuffer;

	private final RingBuffer ringBuffer;

	private final List<PendingAllocation> pendingAllocations = new ArrayList<>();
	private final List<PendingUpload> pendingUploads = new ArrayList<>();

	// For persistent mapping impl.
	private final RingBuffer.PoolAllocation[] allocationsPerFrame = new RingBuffer.PoolAllocation[FRAMES_PER_SYNC];
	private VertexWriter uploadBuffer = new VertexWriter(512);

	private int activeFrame;

	StagingBuffer() {
		this.ringBuffer = new RingBuffer(BUFFER_CAPACITY);

		if (USE_MAPPING) {
			this.stagingBuffer = new GlVertexBuffer(BUFFER_CAPACITY, GL15.GL_STREAM_COPY);
		} else {
			this.stagingBuffer = ImprovedTessellator.getTessellator().getVertexBuffer();
		}
	}

	public static StagingBuffer getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new StagingBuffer();
		}

		return INSTANCE;
	}

	public static void deleteInstance() {
		if (INSTANCE != null) {
			INSTANCE.delete();
			INSTANCE = null;
		}
	}

	public void flushAllCopies() {
		if (this.pendingAllocations.isEmpty()) {
			return;
		}

		if (USE_MAPPING) {
			this.mappedImpl();
		} else {
			this.bufferDataImpl();
		}

		this.pendingAllocations.clear();
	}

	void copyIntoBuffer() {
		this.insertionSort(this.pendingAllocations);

		int currentTarget = -1;
		int currentWriteOffset = 0;
		int currentSize = 0;

		ByteBuffer currentBuffer = null;
		long currentAddress = 0;

		// ugly way of collapsing contiguous uploads to avoid commands and binds.
		for (PendingAllocation alloc : this.pendingAllocations) {
			int target = alloc.bufferTarget.getHandle();
			int writeOffset = alloc.writeOffset;
			int size = alloc.allocationInfo.size;

			long address = MemoryUtil.getAddress(alloc.pending);

			boolean contiguous = target == currentTarget && writeOffset == currentWriteOffset + currentSize && address == currentAddress + currentSize;
			if (!contiguous) {
				if (currentSize != 0) {
					this.addUpload(currentBuffer, currentTarget, currentWriteOffset, currentSize);
					currentBuffer = null;
				}
				currentTarget = target;
				currentWriteOffset = writeOffset;
				currentSize = size;

				currentAddress = address;

				if (currentBuffer == null) {
					currentBuffer = alloc.pending;
				}
			} else {
				currentSize += size;
			}
		}

		if (currentSize != 0) {
			this.addUpload(currentBuffer, currentTarget, currentWriteOffset, currentSize);
		}

		this.stagingBuffer.bufferData(this.uploadBuffer.getNioPtr(), 0, this.uploadBuffer.offset);
		this.uploadBuffer.offset = 0;
	}

	void addUpload(ByteBuffer buffer, int target, int writeOffset, int size) {
		this.uploadBuffer.ensureCapacity(size);
		UnsafeUtil.memCopy(MemoryUtil.getAddress(buffer), this.uploadBuffer.getPtr() + this.uploadBuffer.offset, size);

		this.pendingUploads.add(new PendingUpload(target, this.uploadBuffer.offset, writeOffset, size));
		this.uploadBuffer.offset += size;
	}

	void bufferDataImpl() {
		this.copyIntoBuffer();

		GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, this.stagingBuffer.getHandle());

		for (PendingUpload upload : this.pendingUploads) {
			GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, upload.target);
			GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, upload.readOffset, upload.writeOffset, upload.size);
		}
		this.pendingUploads.clear();
		this.pendingAllocations.clear();

//		int currentTarget = -1;
//		int currentWriteOffset = 0;
//		int currentSize = 0;
//
//		ByteBuffer currentBuffer = null;
//		long currentAddress = 0;
//
//		// ugly way of collapsing contiguous uploads to avoid commands and binds.
//		for (PendingAllocation alloc : this.pendingAllocations) {
//			int target = alloc.bufferTarget.getHandle();
//			int writeOffset = alloc.writeOffset;
//			int size = alloc.allocationInfo.size;
//
//			long address = MemoryUtil.getAddress(alloc.pending);
//
//			boolean contiguous = target == currentTarget && writeOffset == currentWriteOffset + currentSize && address == currentAddress + currentSize;
//			if (!contiguous) {
//				if (currentSize != 0) {
//					this.upload(currentBuffer, currentTarget, currentWriteOffset, currentSize);
//					currentBuffer = null;
//				}
//				currentTarget = target;
//				currentWriteOffset = writeOffset;
//				currentSize = size;
//
//				currentAddress = address;
//
//				if (currentBuffer == null) {
//					currentBuffer = alloc.pending;
//				}
//			} else {
//				currentSize += size;
//			}
//		}
//
//		if (currentSize != 0) {
//			this.upload(currentBuffer, currentTarget, currentWriteOffset, currentSize);
//		}
	}

	int compare(PendingAllocation a, PendingAllocation b) {
		if (a.bufferTarget != b.bufferTarget) {
			return Integer.signum(a.bufferTarget.getHandle() - b.bufferTarget.getHandle());
		}

		return Integer.signum(a.writeOffset - b.writeOffset);
	}

	void insertionSort(List<PendingAllocation> allocations) {
		for (int i = 1; i < allocations.size(); i++) {
			PendingAllocation key = allocations.get(i);

			int j = i - 1;
			while (j >= 0 && compare(allocations.get(j), key) > 0) {
				allocations.set(j + 1, allocations.get(j));
				j--;
			}
			allocations.set(j + 1, key);
		}
	}

	private void upload(ByteBuffer buffer, int target, int writeOffset, int size) {
		int oldLimit = buffer.limit();
		((Buffer) buffer).limit(buffer.position() + size);

		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, target);
		GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, writeOffset, buffer);

		((Buffer) buffer).limit(oldLimit);
	}

	void mappedImpl() {
		int totalSize = 0;

		// could be changed to maxAlloc.top - minAlloc.bottom
		for (PendingAllocation pendingAllocation : this.pendingAllocations) {
			totalSize += pendingAllocation.allocationInfo.size;
		}

		int writingOffset = this.pendingAllocations.get(0).allocationInfo.offset;

		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.stagingBuffer.getHandle());
		ByteBuffer writePtrNio = GL30.glMapBufferRange(GL15.GL_ARRAY_BUFFER, writingOffset, totalSize,
			GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_INVALIDATE_BUFFER_BIT | GL30.GL_MAP_UNSYNCHRONIZED_BIT, null
		);

		long writingPtr = MemoryUtil.getAddress(writePtrNio);

		for (PendingAllocation alloc : this.pendingAllocations) {
			int size = alloc.allocationInfo.size;

			UnsafeUtil.memCopy(MemoryUtil.getAddress(alloc.pending), writingPtr, size);
			writingPtr += size;
		}

		GL15.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);

		for (PendingAllocation alloc : this.pendingAllocations) {
			int size = alloc.allocationInfo.size;
			int offset = alloc.allocationInfo.offset;

			GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, alloc.bufferTarget.getHandle());
			GL31.glCopyBufferSubData(GL15.GL_ARRAY_BUFFER, GL31.GL_COPY_WRITE_BUFFER, offset, alloc.writeOffset, size);
		}

		GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
	}

	public boolean addUpload(ByteBuffer buffer, GlBuffer target, int writeSize, int writeOffset) {
		RingBuffer.PoolAllocation alloc = this.ringBuffer.allocate(writeSize);

		if (alloc == null) {
			return false;
		}

		// we have wrapped the ring buffer, que desgracia.
		if (USE_MAPPING && !this.pendingAllocations.isEmpty() && alloc.offset < this.pendingAllocations.get(this.pendingAllocations.size() - 1).allocationInfo.offset) {
			return false;
		}

		this.pendingAllocations.add(new PendingAllocation(target, buffer, alloc, writeOffset));

		if (this.allocationsPerFrame[this.activeFrame] == null || this.allocationsPerFrame[this.activeFrame].id < alloc.id) {
			this.allocationsPerFrame[this.activeFrame] = alloc;
		}

		return true;
	}

	public static void advanceFrame() {
		if (INSTANCE != null) {
			INSTANCE.updateFrame();
		}
	}

	void updateFrame() {
		if (!USE_MAPPING) {
			return;
		}

		this.activeFrame = (this.activeFrame + 1) % FRAMES_PER_SYNC;

		RingBuffer.PoolAllocation alloc = this.allocationsPerFrame[this.activeFrame];

		if (alloc != null) {
			this.ringBuffer.returnAllocations(new RingBuffer.PoolAllocation[]{alloc});
		}
	}

	public void delete() {
		if (this.stagingBuffer != null && USE_MAPPING) {
			this.stagingBuffer.delete();
			this.stagingBuffer = null;
		}
		if (this.uploadBuffer != null) {
			this.uploadBuffer.delete();
			this.uploadBuffer = null;
		}
	}

	static class PendingAllocation {
		private final GlBuffer bufferTarget;
		private final ByteBuffer pending;
		private final RingBuffer.PoolAllocation allocationInfo;

		private final int writeOffset;

		public PendingAllocation(GlBuffer bufferTarget, ByteBuffer pending, RingBuffer.PoolAllocation allocationInfo, int writeOffset) {
			this.bufferTarget = bufferTarget;
			this.pending = pending;
			this.allocationInfo = allocationInfo;
			this.writeOffset = writeOffset;
		}
	}

	static class PendingUpload {
		private final int target;
		private final int readOffset, writeOffset, size;

		public PendingUpload(int target, int readOffset, int writeOffset, int size) {
			this.target = target;
			this.readOffset = readOffset;
			this.writeOffset = writeOffset;
			this.size = size;
		}
	}
}
