package dev.safixo.client.util.memory;

import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.util.NibbleUtil;
import dev.safixo.client.util.data.PrimitivesFlags;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class ChunkSectionStorage extends ExtendedBlockStorage {
	private boolean metaUniform = true;

	public ChunkSectionStorage(int y, boolean hasSky) {
		super(y, hasSky);
	}

	public boolean hasMetadata() {
		return !this.metaUniform;
	}

	@Override
	public int getExtBlockMetadata(int x, int y, int z) {
		if (this.metaUniform) {
			return 0;
		}

		return this.getBlockMetadata(SectionCache.makeBlockIndex(x, y, z));
	}

	int getBlockMetadata(int index) {
		return NibbleUtil.getNibble(this.blockMetadataArray.data, index);
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
		return this.getBlockId(SectionCache.makeBlockIndex(x, y, z));
	}

	int getBlockId(int index) {
		int lsbId = this.blockLSBArray[index] & 0xFF;
		int msbId = this.blockMSBArray != null ? NibbleUtil.getNibble(this.blockMSBArray.data, index) << 8 : 0;

		return lsbId | msbId;
	}

	@Override
	public void setExtSkylightValue(int x, int y, int z, int value) {
		NibbleUtil.setNibble(this.skylightArray.data, SectionCache.makeBlockIndex(x, y, z), value);
	}

	@Override
	public int getExtSkylightValue(int x, int y, int z) {
		return NibbleUtil.getNibble(this.skylightArray.data, SectionCache.makeBlockIndex(x, y, z));
	}

	@Override
	public void setExtBlocklightValue(int x, int y, int z, int value) {
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
		if (value != 0) {
			this.metaUniform = false;
		}

		NibbleUtil.setNibble(this.blockMetadataArray.data, SectionCache.makeBlockIndex(x, y, z), value);
	}

	@Override
	public void removeInvalidBlocks() {
		int blockRefCount = 0;
		int tickRefCount = 0;
		boolean metaUniform = true;

		byte[] metaArray = this.blockMetadataArray.data;

		for (int i = 0; i < 16 * 16 * 16; i++) {
			int blockId = this.getBlockId(i);

			if (blockId == 0) {
				continue;
			}

			if (i < 2048 && metaArray[i] != 0) {
				metaUniform = false;
			}

			/* Wouldn't many things have to gone wrong to have a invalid block
			** with a 'valid' block id?
			if (Block.blocksList[blockId] == null) {
				this.blockLSBArray[y << 8 | z << 4 | x] = 0;
				if (this.blockMSBArray != null) {
					this.blockMSBArray.set(x, y, z, 0);
				}
			}
			 */

			blockRefCount++;

			if (PrimitivesFlags.getTickRandom(blockId)) {
				tickRefCount++;
			}
		}

		this.metaUniform = metaUniform;
		this.blockRefCount = blockRefCount;
		this.tickRefCount = tickRefCount;
	}
}
