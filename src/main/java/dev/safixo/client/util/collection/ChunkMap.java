package dev.safixo.client.util.collection;

import dev.safixo.client.util.MathExt;
import net.minecraft.world.chunk.Chunk;

public class ChunkMap {
	private static final int INITIAL_SIZE = 16;
	private static final int HASH_SEED = 1183822147;

	private Chunk[] chunks;
	private short[] adjacentMasks;

	private int size, mask, count;
	private Chunk lastChunk;

	public ChunkMap() {
		this(INITIAL_SIZE);
	}

	public ChunkMap(int size) {
		size = Math.max(INITIAL_SIZE, size);
		size = MathExt.nextPOT(size);

		this.adjacentMasks = new short[size];
		this.chunks = new Chunk[size];

		this.size = size;
		this.mask = size - 1;
	}

	public int getSize() {
		return this.count;
	}

	public boolean contains(int x, int z) {
		int mask = this.mask;
		Chunk[] chunks = this.chunks;

		int slot = hash(x, z) & mask;
		Chunk slotChunk = chunks[slot];

		while (slotChunk != null) {
			if (slotChunk.xPosition == x && slotChunk.zPosition == z) {
				return true;
			}

			slot = ++slot & mask;
			slotChunk = chunks[slot];
		}

		return false;
	}

	private void shiftKeys(int pos) {
		int last, slot;

		Chunk[] values = this.chunks;
		short[] adjacentMasks = this.adjacentMasks;
		int mask = this.mask;

		this.lastChunk = null;

		while (true) {
			pos = ((last = pos) + 1) & mask;
			Chunk currValue;

			while (true) {
				currValue = values[pos];

				if (currValue == null) {
					values[last] = null;
					adjacentMasks[last] = 0;
					return;
				}

				slot = hash(currValue.xPosition, currValue.zPosition) & mask;
				if (last <= pos ? (last >= slot || slot > pos) : (last >= slot && slot > pos)) {
					break;
				}
				pos = ++pos & mask;
			}
			values[last] = values[pos];
			adjacentMasks[last] = adjacentMasks[pos];
		}
	}

	public Chunk remove(int x, int z) {
		int mask = this.mask;
		int slot = hash(x, z) & mask;

		Chunk[] chunks = this.chunks;

		while (true) {
			Chunk slotChunk = chunks[slot];

			if (slotChunk == null) {
				return null;
			}
			if (slotChunk.xPosition == x && slotChunk.zPosition == z) {
				break;
			}

			slot = ++slot & mask;
		}

		Chunk removed = this.chunks[slot];

		if (this.lastChunk != null &&
			removed.xPosition == this.lastChunk.xPosition && removed.zPosition == this.lastChunk.zPosition) {
			this.lastChunk = null;
		}

		this.chunks[slot] = null;
		this.adjacentMasks[slot] = 0;
		this.count--;

		this.shiftKeys(slot);
		return removed;
	}

	public void put(int x, int z, Chunk chunk) {
		int size = this.size;
		int newCount = this.count + 1;

		if (getNewSize(newCount) >= size) {
			this.resize();
		}

		int mask = this.mask;
		Chunk[] chunks = this.chunks;

		int slot = hash(chunk.xPosition, chunk.zPosition) & mask;
		Chunk slotChunk = chunks[slot];

		while (slotChunk != null) {
			// if the key is already put, replace the slot.
			if (slotChunk.xPosition == x && slotChunk.zPosition == z) {
				chunks[slot] = chunk;
				return;
			}

			slot = ++slot & mask;
			slotChunk = chunks[slot];
		}

		this.count++;

		chunks[slot] = chunk;
	}

	private static int hash(int x, int z) {
		return ((HASH_SEED + x) * HASH_SEED + z) * HASH_SEED;
	}

	public int getIndex(int x, int z) {
		int mask = this.mask;
		Chunk[] chunks = this.chunks;

		int slot = hash(x, z) & mask;
		Chunk slotChunk = chunks[slot];

		while (slotChunk != null) {
			if (slotChunk.xPosition == x && slotChunk.zPosition == z) {
				return slot;
			}

			slot = ++slot & mask;
			slotChunk = chunks[slot];
		}

		return slot;
	}

	public Chunk getChunk(int x, int z) {
		if (this.lastChunk != null && this.lastChunk.xPosition == x && this.lastChunk.zPosition == z) {
			return this.lastChunk;
		}

		int mask = this.mask;
		Chunk[] chunks = this.chunks;

		int slot = hash(x, z) & mask;
		Chunk slotChunk = chunks[slot];

		while (slotChunk != null) {
			if (slotChunk.xPosition == x && slotChunk.zPosition == z) {
				return this.lastChunk = slotChunk;
			}

			slot = ++slot & mask;
			slotChunk = chunks[slot];
		}

		return null;
	}

	public Chunk getChunk(int index) {
		return this.chunks[index];
	}

	public int getAdjacentMask(int index) {
		return this.adjacentMasks[index];
	}

	public void addAdjacentDirection(int index, int bit) {
		this.adjacentMasks[index] |= (short) bit;
	}

	public void removeAdjacentDirection(int index, int bit) {
		this.adjacentMasks[index] &= (short) ~bit;
	}

	static int getNewSize(int size) {
		return (size * 2);
	}

	void resize() {
		int newSize = getNewSize(this.size);

		Chunk[] newValues = new Chunk[newSize];
		Chunk[] oldValues = this.chunks;

		short[] newAdjacentMasks = new short[newSize];
		short[] oldAdjacentMasks = this.adjacentMasks;

		this.chunks = newValues;
		this.adjacentMasks = newAdjacentMasks;
		this.lastChunk = null;

		this.mask = newSize - 1;
		this.size = newSize;

		int mask = this.mask;

		int index = -1;
		int count = this.count;


		while (count > 0) {
			if (oldValues[++index] == null) {
				continue;
			}

			count--;

			Chunk oldValue = oldValues[index];
			short oldAdjacentMask = oldAdjacentMasks[index];

			{
				int slot = hash(oldValue.xPosition, oldValue.zPosition) & mask;
				Chunk slotChunk = newValues[slot];

				while (slotChunk != null) {
					slot = ++slot & mask;
					slotChunk = newValues[slot];
				}

				newValues[slot] = oldValue;
				newAdjacentMasks[slot] = oldAdjacentMask;
			}
		}
	}
}
