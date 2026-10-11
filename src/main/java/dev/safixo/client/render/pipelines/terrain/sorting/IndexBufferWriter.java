package dev.safixo.client.render.pipelines.terrain.sorting;

import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.memory.NativeBuffer;
import dev.safixo.client.util.memory.UnsafeUtil;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class IndexBufferWriter {
	public static final int INDEX_SIZE = 2;
	public static final int STRIDE_PER_QUAD = INDEX_SIZE * 4;
	private static final int MIN_QUADS = 64;

	private long ptr;
	private ByteBuffer ptrNio;
	private int capacity;

	private int offset;

	public IndexBufferWriter() {
		this.capacity = MIN_QUADS * STRIDE_PER_QUAD;
		this.ptr = NativeBuffer.nmemAlloc(STRIDE_PER_QUAD * MIN_QUADS);
		this.ptrNio = NativeBuffer.wrap(this.ptr);
	}

	public void restart() {
		this.offset = 0;
	}

	public int getQuadCount() {
		return this.offset / STRIDE_PER_QUAD;
	}

	public int getOffset() {
		return this.offset;
	}

	public ByteBuffer getPtrNio() {
		((Buffer)this.ptrNio).position(0);
		((Buffer)this.ptrNio).limit(this.getOffset());

		return this.ptrNio;
	}

	// Assumes that all quad indices are GL_UNSIGNED_SHORT
	public void writeQuad(int quadId) {
		UnsafeUtil.memPutLong(this.ptr + this.offset, quadId * 0x4000400040004L + 0x3000200010000L);
		this.offset += STRIDE_PER_QUAD;
	}

	private void resize(int minSize) {
		int newCapacity = Math.max(MathExt.nextPOT(minSize), this.capacity * 2);
		long newPtr = NativeBuffer.nmemAlloc(newCapacity);

		UnsafeUtil.memCopy(this.ptr, newPtr, this.offset);
		NativeBuffer.nmemFree(this.ptr);

		this.ptr = newPtr;
		this.ptrNio = NativeBuffer.wrap(newPtr);
		this.capacity = newCapacity;
	}

	public void delete() {
		NativeBuffer.nmemFree(this.ptr);
		this.ptrNio = null;
	}

	public void ensureQuadCapacity(int quadCount) {
		if (this.offset + quadCount * STRIDE_PER_QUAD >= this.capacity) {
			this.resize(this.offset + quadCount * STRIDE_PER_QUAD);
		}
	}
}
