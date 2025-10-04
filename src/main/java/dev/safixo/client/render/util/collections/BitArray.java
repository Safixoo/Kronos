package dev.safixo.client.render.util.collections;

import java.util.Arrays;

// Class borrowed from Sodium
public class BitArray {
	private static final int ADDRESS_BITS_PER_WORD = 6;
	private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;
	private static final int BIT_INDEX_MASK = BITS_PER_WORD - 1;

	private final long[] words;

	public BitArray(int capacity) {
		this.words = new long[(align(capacity, BITS_PER_WORD) >> ADDRESS_BITS_PER_WORD)];
	}

	public static int align(int num, int alignment) {
		int additive = alignment - 1;
		int mask = ~additive;
		return (num + additive) & mask;
	}

	public boolean get(int index) {
		return (this.words[wordIndex(index)] & 1L << bitIndex(index)) != 0;
	}

	public boolean getFalse(int index) {
		return (this.words[wordIndex(index)] & 1L << bitIndex(index)) == 0;
	}

	public void set(int index) {
		this.words[wordIndex(index)] |= 1L << bitIndex(index);
	}

	public void clear() {
		Arrays.fill(this.words, 0);
	}

	private static int wordIndex(int index) {
		return index >> ADDRESS_BITS_PER_WORD;
	}

	private static int bitIndex(int index) {
		return index & BIT_INDEX_MASK;
	}
}
