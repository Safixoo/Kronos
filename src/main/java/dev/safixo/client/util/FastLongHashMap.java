package dev.safixo.client.util;

import java.util.Arrays;

// Linear probing, robin-hood hash-map, should have a very performant get.
public class FastLongHashMap {
	private static final long LONG_PHI = 0x9E3779B97F4A7C15L;

	// use a very unlikely value to mark unused keys.
	private static final long TOMBSTONE = ~(1L << 62);
	private static final long NULL = (1L << 62L) + (1L << 61L) + 5;

	private static final int INITIAL_SIZE = 16;

	private long[] keys;
	private Object[] values;
	private int size, mask, count, tombstones;

	private long lastKey = NULL;
	private Object lastObject;

	public FastLongHashMap() {
		this(INITIAL_SIZE);
	}

	public FastLongHashMap(int size) {
		size = Math.max(INITIAL_SIZE, size);
		size = MathExt.nextPOT(size);

		this.keys = new long[size];
		this.values = new Object[size];
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

	public Object remove(long key) {
		int mask = this.mask;
		long[] keys = this.keys;

		if (this.lastKey == key) {
			this.lastKey = NULL;
		}

		int slot = hash(key) & mask;
		long slotKey = keys[slot];

		while (slotKey != NULL && slotKey != key) {
			slot = ++slot & mask;
			slotKey = keys[slot];
		}

		Object removedObj = this.values[slot];

		if (slotKey == key) {
			this.values[slot] = null;
			this.keys[slot] = TOMBSTONE;

			this.count--;
			this.tombstones++;

			// avoids getting stuck because of too many tombstones.
			if (getNewSize(this.tombstones + this.count) >= this.size) {
				this.resize();
			}
		}

		return removedObj;
	}

	public void put(long key, Object value) {
		int size = this.size;
		int newCount = this.count + 1;

		if (this.lastKey == key) {
			this.lastKey = NULL;
		}

		if (getNewSize(newCount) >= size) {
			this.resize();
		}

		if (key == NULL || key == TOMBSTONE) {
			throw new RuntimeException("Key is the same as the internal value!");
		}

		int mask = this.mask;
		long[] keys = this.keys;
		Object[] values = this.values;

		int slot = hash(key) & mask;

		long slotKey = keys[slot];
		int potentialTombstone = 0;

		while (slotKey != NULL) {
			// if the key is already put, replace the slot.
			if (slotKey == key) {
				values[slot] = value;
				return;
			}

			// reuse tombstones, not immediately to avoid duplicating keys.
			if (slotKey == TOMBSTONE) {
				potentialTombstone = slot;
			}

			slot = ++slot & mask;
			slotKey = keys[slot];
		}

		if (potentialTombstone != 0) {
			slot = potentialTombstone;
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

	public Object get(long key) {
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

	static int getNewSize(int size) {
		return (size * 3) >> 1;
	}

	void resize() {
		int newSize = MathExt.nextPOT(getNewSize(this.size));

		long[] newKeys = new long[newSize];
		Object[] newValues = new Object[newSize];

		long[] oldKeys = this.keys;
		Object[] oldValues = this.values;

		this.keys = newKeys;
		this.values = newValues;
		this.mask = newSize - 1;
		this.size = newSize;

		int mask = this.mask;

		int index = -1;
		int count = this.count;

		Arrays.fill(this.keys, NULL);

		while (count > 0) {
			if (oldValues[++index] == null) {
				continue;
			}

			count--;

			long key = oldKeys[index];
			Object value = oldValues[index];

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
}
