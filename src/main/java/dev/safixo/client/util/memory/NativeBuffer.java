package dev.safixo.client.util.memory;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import org.lwjgl.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class NativeBuffer {
	private static final Long2ReferenceMap<ByteBuffer> BUFFER_TO_PTR = new Long2ReferenceOpenHashMap<>();

	public static FloatBuffer memAllocFloat(int size) {
		return memAlloc(size * 4).asFloatBuffer();
	}

	public static long nmemAlloc(long size) {
		int realSize = (int) size;

		ByteBuffer buffer = memAlloc(realSize);
		long ptr = MemoryUtil.getAddress(buffer);

		BUFFER_TO_PTR.put(ptr, buffer);

		return ptr;
	}

	public static void nmemFree(long ptr) {
		BUFFER_TO_PTR.remove(ptr);
	}

	public static ByteBuffer wrap(long ptr) {
		return BUFFER_TO_PTR.get(ptr);
	}

	public static ByteBuffer memAlloc(int size) {
		return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
	}
}
