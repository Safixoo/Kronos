package dev.safixo.client.util.collection;

import dev.safixo.client.util.MathExt;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Iterator;

// Linear probing, robin-hood hash-map, should have a very performant get.
public class FastLongHashMap<T> implements Iterable<T> {
	private static final long LONG_PHI = 0x9E3779B97F4A7C15L;

	// use a very unlikely value to mark unused keys.
	private static final long NULL = (1L << 62L) + (1L << 61L) + 5;

	private static final int INITIAL_SIZE = 16;

	private long[] keys;
	private T[] values;
	private int size, mask, count;

	private long lastKey = NULL;
	private T lastObject;

	public FastLongHashMap() {
		this(INITIAL_SIZE);
	}

	public FastLongHashMap(int size) {
		size = Math.max(INITIAL_SIZE, size);
		size = MathExt.nextPOT(size);

		this.keys = new long[size];
		this.values = (T[]) new Object[size];
		Arrays.fill(this.keys, NULL);

		this.size = size;
		this.mask = size - 1;
	}

	public int getSize() {
		return this.count;
	}

	public boolean contains(long key) {
		int mask = this.mask;
		long[] keys = this.keys;

		int slot = hash(key) & mask;
		long slotKey = keys[slot];

		while (slotKey != NULL && slotKey != key) {
			slot = ++slot & mask;
			slotKey = keys[slot];
		}

		return slotKey != NULL;
	}

	private void shiftKeys(int pos) {
		int last, slot;

		T[] values = this.values;
		long[] key = this.keys;
		int mask = this.mask;

		while (true) {
			pos = ((last = pos) + 1) & mask;
			long curr;

			while (true) {
				curr = key[pos];

				if (curr == NULL) {
					key[last] = NULL;
					values[last] = null;
					return;
				}

				slot = hash(curr) & mask;
				if (last <= pos ? (last >= slot || slot > pos) : (last >= slot && slot > pos)) {
					break;
				}
				pos = ++pos & mask;
			}
			key[last] = curr;
			values[last] = values[pos];
		}
	}

	public T remove(long key) {
		if (this.lastKey == key) {
			this.lastKey = NULL;
		}

		int mask = this.mask;
		long[] keys = this.keys;

		int slot = hash(key) & mask;

		while (true) {
			long slotKey = keys[slot];

			if (slotKey == NULL) {
				return null;
			}
			if (slotKey == key) {
				break;
			}

			slot = ++slot & mask;
		}

		T removed = this.values[slot];

		this.values[slot] = null;
		this.count--;

		if (this.size >= 64 && this.count < (this.size >> 2)) {
			this.resize(this.size >> 1);
		} else {
			this.shiftKeys(slot);
		}

		return removed;
	}

	public void put(long key, T value) {
		// This does not share the same behavior as most maps!.
		if (value == null) {
			this.remove(key);
			return;
		}

		int size = this.size;
		int newCount = this.count + 1;

		if (this.lastKey == key) {
			this.lastKey = NULL;
		}

		if (newCount >= (size * 3) >> 2) { // 0.75f load factor
			this.resize(size * 2);
		}

		if (key == NULL) {
			throw new RuntimeException("Key is the same as the internal value!");
		}

		int mask = this.mask;
		long[] keys = this.keys;
		Object[] values = this.values;

		int slot = hash(key) & mask;
		long slotKey = keys[slot];

		while (slotKey != NULL) {
			// if the key is already put, replace the slot.
			if (slotKey == key) {
				values[slot] = value;
				return;
			}

			slot = ++slot & mask;
			slotKey = keys[slot];
		}

		this.count++;

		values[slot] = value;
		keys[slot] = key;
	}

	static int hash(long x) {
		long h = x * LONG_PHI;
		h ^= h >>> 32;
		return (int) (h ^ (h >>> 16));
	}

	public T get(long key) {
		if (this.lastKey == key) {
			return this.lastObject;
		}

		int mask = this.mask;
		long[] keys = this.keys;

		int slot = hash(key) & mask;
		long slotKey = keys[slot];

		while (slotKey != NULL && slotKey != key) {
			slot = ++slot & mask;
			slotKey = keys[slot];
		}

		this.lastKey = key;
		return this.lastObject = this.values[slot];
	}

	void resize(int newSize) {
		long[] newKeys = new long[newSize];
		T[] newValues = (T[]) new Object[newSize];

		long[] oldKeys = this.keys;
		T[] oldValues = this.values;

		this.keys = newKeys;
		this.values = newValues;
		this.mask = newSize - 1;
		this.size = newSize;

		int mask = this.mask;

		int index = -1;
		int count = this.count;

		Arrays.fill(this.keys, NULL);

		while (count > 0) {
			if (oldKeys[++index] == NULL) {
				continue;
			}

			count--;

			long key = oldKeys[index];
			T value = oldValues[index];

			{
				int slot = hash(key) & mask;
				long slotKey = newKeys[slot];

				while (slotKey != NULL) {
					slot = ++slot & mask;
					slotKey = newKeys[slot];
				}

				newValues[slot] = value;
				newKeys[slot] = key;
			}
		}
	}

	public void clear() {
		Arrays.fill(this.keys, NULL);
		Arrays.fill(this.values, null);
		this.count = 0;
		this.lastKey = NULL;
		this.lastObject = null;
	}

	public T[] toArray(Class<T> arrayClass) {
		T[] values = (T[]) Array.newInstance(arrayClass, this.count);
		int index = 0;

		for (T value : this.values) {
			if (value != null) {
				values[index++] = value;
			}
		}

		return values;
	}

	public boolean isEmpty() {
		return this.count == 0;
	}

	@Override
	public @NotNull Iterator<T> iterator() {
		return new MapIterator<>(this);
	}

	public static class MapIterator<T> implements Iterator<T> {
		private final T[] values;

		private int count;
		private int index;

		public MapIterator(FastLongHashMap<T> map) {
			this.values = map.values;
			this.count = map.count;
		}

		@Override
		public boolean hasNext() {
			return this.count > 0;
		}

		@Override
		public T next() {
			while (this.values[this.index] == null) {
				this.index++;
			}

			this.count--;
			return this.values[this.index++];
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}
