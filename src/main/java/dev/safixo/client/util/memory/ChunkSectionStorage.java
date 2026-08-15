package dev.safixo.client.util.memory;

import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.util.NibbleUtil;
import dev.safixo.client.util.data.PrimitivesFlags;
import net.minecraft.block.Block;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class ChunkSectionStorage extends ExtendedBlockStorage {
	public ChunkSectionStorage(int y, boolean hasSky) {
		super(y, hasSky);

		this.skylightArray = null;
		this.blocklightArray = null;
		this.blockMetadataArray = null;
	}

	public boolean hasMetadata() {
		return this.blockMetadataArray != null;
	}

	@Override
	public int getExtBlockMetadata(int x, int y, int z) {
		if (this.blockMetadataArray == null) {
			return 0;
		}

		return NibbleUtil.getNibble(this.blockMetadataArray.data, SectionCache.makeBlockIndex(x, y, z));
	}

	@Override
	public void setExtBlockID(int x, int y, int z, int newId) {
		int index = SectionCache.makeBlockIndex(x, y, z);

		int oldId = this.blockLSBArray[index] & 0xFF;
		if (this.blockMSBArray != null) {
			oldId |= NibbleUtil.getNibble(this.blockMSBArray.data, index) << 8;
		}

		if (oldId * newId == 0) {
			this.blockRefCount += Integer.signum(newId - oldId);
		}

		boolean oldTick = PrimitivesFlags.getTickRandom(oldId);
		boolean newTick = PrimitivesFlags.getTickRandom(newId);

		if (oldTick != newTick) {
			this.tickRefCount += newTick ? 1 : -1;
		}

		this.blockLSBArray[index] = (byte) (newId & 0xFF);

		if (newId > 0xFF) {
			if (this.blockMSBArray == null) {
				this.blockMSBArray = new NibbleArray(this.blockLSBArray.length, 4);
			}
			NibbleUtil.setNibble(this.blockMSBArray.data, index, newId >> 8);
		} else if (this.blockMSBArray != null) {
			NibbleUtil.setNibble(this.blockMSBArray.data, index, 0);
		}
	}

	@Override
	public int getExtBlockID(int x, int y, int z) {
		int index = SectionCache.makeBlockIndex(x, y, z);

		int lsbId = this.blockLSBArray[index] & 0xFF;
		int msbId = this.blockMSBArray != null ? NibbleUtil.getNibble(this.blockMSBArray.data, index) << 8 : 0;

		return lsbId | msbId;
	}

	@Override
	public void setExtSkylightValue(int x, int y, int z, int value) {
		if (this.skylightArray == null) {
			if (value == 0) {
				return;
			}
			this.skylightArray = new NibbleArray(4096, 4);
		}

		NibbleUtil.setNibble(this.skylightArray.data, SectionCache.makeBlockIndex(x, y, z), value);
	}

	@Override
	public int getExtSkylightValue(int x, int y, int z) {
		if (this.skylightArray == null) {
			return 0;
		}

		return NibbleUtil.getNibble(this.skylightArray.data, SectionCache.makeBlockIndex(x, y, z));
	}

	@Override
	public void setExtBlocklightValue(int x, int y, int z, int value) {
		if (this.blocklightArray == null) {
			if (value == 0) {
				return;
			}
			this.blocklightArray = new NibbleArray(4096, 4);
		}

		NibbleUtil.setNibble(this.blocklightArray.data, SectionCache.makeBlockIndex(x, y, z), value);
	}

	@Override
	public int getExtBlocklightValue(int x, int y, int z) {
		if (this.blocklightArray == null) {
			return 0;
		}

		return NibbleUtil.getNibble(this.blocklightArray.data, SectionCache.makeBlockIndex(x, y, z));
	}

	@Override
	public void setExtBlockMetadata(int x, int y, int z, int value) {
		if (this.blockMetadataArray == null) {
			if (value == 0) {
				return;
			}
			this.blockMetadataArray = new NibbleArray(4096, 4);
		}

		NibbleUtil.setNibble(this.blockMetadataArray.data, SectionCache.makeBlockIndex(x, y, z), value);
	}

	@Override
	public NibbleArray getMetadataArray() {
		if (this.blockMetadataArray == null) {
			this.blockMetadataArray = new NibbleArray(4096, 4);
		}

		return this.blockMetadataArray;
	}

	@Override
	public NibbleArray getBlocklightArray() {
		if (this.blocklightArray == null) {
			this.blocklightArray = new NibbleArray(4096, 4);
		}

		return this.blocklightArray;
	}

	@Override
	public NibbleArray getSkylightArray() {
		if (this.skylightArray == null) {
			this.skylightArray = new NibbleArray(4096, 4);
		}

		return this.skylightArray;
	}
}
