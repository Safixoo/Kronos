package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.render.util.MathExt;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3Pool;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.ForgeDirection;
import dev.safixo.client.render.util.data.BlocksFlags;

import java.util.Arrays;

public class SectionCache implements IBlockAccess {
	private static final byte[] DEFAULT_BYTE_ARRAY = new byte[16 * 16 * 16];
	private static final byte[] DEFAULT_FULL_BYTE_ARRAY = new byte[16 * 16 * 16];

	static {
		Arrays.fill(DEFAULT_FULL_BYTE_ARRAY, (byte) 0xFF);
	}

	private final World worldObj;
	public final int blockX, blockY, blockZ;

	public static final byte[][] SECTION_BLOCKS = new byte[3 * 3 * 3][];
	public static final byte[][] SECTION_DATA = new byte[3 * 3 * 3][];
	public static final byte[][] SKY_LIGHT = new byte[3 * 3 * 3][];
	public static final byte[][] BLOCK_LIGHT = new byte[3 * 3 * 3][];

	private static byte[] CENTER_BLOCKS;
	private static byte[] CENTER_DATA;

	private boolean centerSectEmpty;

	public SectionCache(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		this.worldObj = world;

		this.blockX = minX & ~0b1111;
		this.blockY = minY & ~0b1111;
		this.blockZ = minZ & ~0b1111;

		this.fillData(world, minX, minY, minZ, maxX, maxY, maxZ);
	}

	public void fillData(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		int maxChunkX = (maxX >> 4);
		int maxChunkZ = (maxZ >> 4);
		int maxChunkY = (maxY >> 4);

		int sectionX = this.blockX >> 4, sectionY = this.blockY >> 4, sectionZ = this.blockZ >> 4;

		for (int x = sectionX; x <= maxChunkX; x++) {
			for (int z = sectionZ; z <= maxChunkZ; z++) {
				int relX = x - sectionX;
				int relZ = z - sectionZ;

				Chunk chunk = world.getChunkFromChunkCoords(x, z);

				for (int y = sectionY; y <= maxChunkY; y++) {
					int relY = y - sectionY;

					int yInd = (minY >> 4) + relY;

					if (yInd < 0 || yInd > 15) {
						continue;
					}

					ExtendedBlockStorage section = chunk.getBlockStorageArray()[(minY >> 4) + relY];
					int sectionIndex = sectionIndex(relX, relY, relZ);

					if (sectionIndex(1, 1, 1) == sectionIndex) {
						this.centerSectEmpty = section == null || section.isEmpty();

						if (this.centerSectEmpty) {
							return;
						}
					}

					if (section != null && !section.isEmpty()) {
						if (section.getBlockLSBArray() != null) {
							SECTION_BLOCKS[sectionIndex] = section.getBlockLSBArray();
						} else {
							SECTION_BLOCKS[sectionIndex] = DEFAULT_BYTE_ARRAY;
						}

						if (section.getMetadataArray() != null) {
							SECTION_DATA[sectionIndex] = section.getMetadataArray().data;
						} else {
							SECTION_DATA[sectionIndex] = DEFAULT_BYTE_ARRAY;
						}

						SKY_LIGHT[sectionIndex] = section.getSkylightArray().data != null ? section.getSkylightArray().data : DEFAULT_FULL_BYTE_ARRAY;
						BLOCK_LIGHT[sectionIndex] = section.getBlocklightArray().data != null ? section.getBlocklightArray().data : DEFAULT_BYTE_ARRAY;
					} else {
						SECTION_BLOCKS[sectionIndex] = DEFAULT_BYTE_ARRAY;
						SECTION_DATA[sectionIndex] = DEFAULT_BYTE_ARRAY;
						SKY_LIGHT[sectionIndex] = DEFAULT_FULL_BYTE_ARRAY;
						BLOCK_LIGHT[sectionIndex] = DEFAULT_BYTE_ARRAY;
					}
				}
			}
		}

		CENTER_BLOCKS = SECTION_BLOCKS[sectionIndex(1, 1, 1)];
		CENTER_DATA = SECTION_DATA[sectionIndex(1, 1, 1)];
	}

	public static int sectionIndex(int x, int y, int z) {
		return x + (z * 3) + (y * 9);
	}

	public static int getNibble(byte[] nibbleArray, int blockIndex) {
		int nibbleIndex = blockIndex >> 1;
		int nibblePart = blockIndex & 1;
		return nibbleArray[nibbleIndex] >>> (nibblePart << 2) & 15;
	}

	public static int makeBlockIndex(int x, int y, int z) {
		return y << 8 | z << 4 | x;
	}

	public static int blockX(int blockIndex) {
		return (blockIndex >>> 0) & 0xF;
	}

	public static int blockY(int blockIndex) {
		return (blockIndex >>> 8) & 0xF;
	}

	public static int blockZ(int blockIndex) {
		return (blockIndex >>> 4) & 0xF;
	}

	@Override
	public int getBlockId(int x, int y, int z) {
		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

		int sectInd = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
		int blockInd = makeBlockIndex(blockX & 15, blockY & 15, blockZ & 15);

		return byteToUnsigned(SECTION_BLOCKS[sectInd][blockInd]);
	}

	@Override
	public TileEntity getBlockTileEntity(int par1, int par2, int par3) {
		return null;
	}

	@Override
	public int getLightBrightnessForSkyBlocks(int x, int y, int z, int defBlockLight) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

		int sectionIndex = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
		int blockId = byteToUnsigned(SECTION_BLOCKS[sectionIndex][blockIndex]);

		if (blockId != 0 && BlocksFlags.SOLID[blockId]) {
			return 0;
		}

		int skyLight = getNibble(SKY_LIGHT[sectionIndex], blockIndex);
		int blockLight = getNibble(BLOCK_LIGHT[sectionIndex], blockIndex);

		return MathExt.getLightmapCoord(skyLight & 0xF, blockLight & 0xF);
	}

	public int getBlockIdCenter(int x, int y, int z) {
		return byteToUnsigned(CENTER_BLOCKS[makeBlockIndex(x & 15, y & 15, z & 15)]);
	}

	public int getBlockIdCenter(int blockIndex) {
		return CENTER_BLOCKS[blockIndex];
	}

	@Override
	public float getBrightness(int i, int j, int k, int l) {
		return 0;
	}

	@Override
	public float getLightBrightness(int x, int y, int z) {
		return this.worldObj.provider.lightBrightnessTable[this.getLightValue(x, y, z)];
	}

	// AFAIK, not used for rendering.
	public int getLightValue(int x, int y, int z) {
		return 0;
	}

	@Override
	public int getBlockMetadata(int x, int y, int z) {
		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

		int sectInd = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
		int blockInd = makeBlockIndex(blockX & 15, blockY & 15, blockZ & 15);

		return getNibble(SECTION_DATA[sectInd], blockInd);
	}

	public int getBlockMetadataCenter(int x, int y, int z) {
		return getNibble(CENTER_DATA, makeBlockIndex(x & 15, y & 15, z & 15));
	}

	@Override
	public Material getBlockMaterial(int x, int y, int z) {
		int blockId = this.getBlockId(x, y, z);

		return BlocksFlags.MATERIAL[blockId];
	}

	public int isBlockOpaqueCubeInt(int x, int y, int z) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

		int sectionIndex = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);

		return BlocksFlags.SOLID_LIGHT_MASK[byteToUnsigned(SECTION_BLOCKS[sectionIndex][blockIndex])];
	}

	public int isBlockOpaqueCubeRel(int x, int y, int z) {
		int sectionIndex = sectionIndex(x >> 4, y >> 4, z >> 4);
		int blockInd = makeBlockIndex(x & 15, y & 15, z & 15);

		return BlocksFlags.SOLID_LIGHT_MASK[byteToUnsigned(SECTION_BLOCKS[sectionIndex][blockInd])];
	}

	@Override
	public boolean isBlockOpaqueCube(int x, int y, int z) {
		return this.isBlockOpaqueCubeInt(x, y, z) != 0;
	}

	public int isBlockOpaqueCubeCenter(int blockIndex) {
		return BlocksFlags.SOLID_LIGHT_MASK[byteToUnsigned(CENTER_BLOCKS[blockIndex])];
	}

	public static int byteToUnsigned(byte id) {
		return id & 0xFF;
	}

	@Override
	public boolean isBlockNormalCube(int x, int y, int z) {
		return BlocksFlags.NORMAL_BLOCK[this.getBlockId(x, y, z)];
	}

	@Override
	public boolean isAirBlock(int x, int y, int z) {
		return this.getBlockId(x, y, z) == 0;
	}

	@Override
	public BiomeGenBase getBiomeGenForCoords(int par1, int par2) {
		return this.worldObj.getBiomeGenForCoords(par1, par2);
	}

	@Override
	public int getHeight() {
		return 256;
	}

	@Override
	public boolean extendedLevelsInChunkCache() {
		return this.centerSectEmpty;
	}

	@Override
	public boolean doesBlockHaveSolidTopSurface(int x, int y, int z) {
		return this.worldObj.doesBlockHaveSolidTopSurface(x, y, z);
	}

	@Override
	public Vec3Pool getWorldVec3Pool() {
		return this.worldObj.getWorldVec3Pool();
	}

	@Override
	public int isBlockProvidingPowerTo(int par1, int par2, int par3, int par4) {
		int i1 = this.getBlockId(par1, par2, par3);
		return i1 == 0 ? 0 : Block.blocksList[i1].isProvidingStrongPower(this, par1, par2, par3, par4);
	}

	@Override
	public boolean isBlockSolidOnSide(int x, int y, int z, ForgeDirection side, boolean defaultVal) {
		int blockId = this.getBlockId(x, y, z);

		if (blockId == 0) {
			return false;
		}

		Block block = Block.blocksList[blockId];

		if (block == null) {
			return false;
		}

		return block.isBlockSolidOnSide(this.worldObj, x, y, z, side);
	}
}
