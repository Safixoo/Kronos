package dev.safixo.client.render.meshing;

import net.minecraft.client.render.LightmapHelper;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.entity.TileEntity;
import net.minecraft.core.block.material.Material;
import net.minecraft.core.enums.LightLayer;
import net.minecraft.core.world.World;
import net.minecraft.core.world.WorldSource;
import net.minecraft.core.world.biome.Biome;
import net.minecraft.core.world.chunk.Chunk;
import net.minecraft.core.world.chunk.ChunkSection;
import net.minecraft.core.world.season.SeasonManager;
import org.jetbrains.annotations.Nullable;
import dev.safixo.client.render.util.data.BlocksFlags;

public class SectionCache implements WorldSource {
	private static final short[] DEFAULT_SHORT_ARRAY = new short[16 * 16 * 16];
	private static final byte[] DEFAULT_BYTE_ARRAY = new byte[16 * 16 * 16];

	private final World worldObj;
	public final int blockX, blockY, blockZ;

	public static final short[][] SECTION_BLOCKS = new short[3 * 3 * 3][];
	public static final byte[][] SECTION_DATA = new byte[3 * 3 * 3][];
	public static final byte[][] SKY_LIGHT = new byte[3 * 3 * 3][];
	public static final byte[][] BLOCK_LIGHT = new byte[3 * 3 * 3][];

	private static short[] CENTER_BLOCKS;
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
		int maxChunkX = Math.floorDiv(maxX, 16);
		int maxChunkZ = Math.floorDiv(maxZ, 16);
		int maxChunkY = Math.floorDiv(maxY, 16);

		int sectionX = this.blockX >> 4, sectionY = this.blockY >> 4, sectionZ = this.blockZ >> 4;

		for (int x = sectionX; x <= maxChunkX; x++) {
			for (int z = sectionZ; z <= maxChunkZ; z++) {
				int relX = x - sectionX;
				int relZ = z - sectionZ;

				Chunk chunk = world.getChunkFromChunkCoords(x, z);

				for (int y = sectionY; y <= maxChunkY; y++) {
					int relY = y - sectionY;

					ChunkSection section = chunk.getSection((minY >> 4) + relY);
					int sectionIndex = sectionIndex(relX, relY, relZ);

					if (sectionIndex(1, 1, 1) == sectionIndex) {
						this.centerSectEmpty = section.blocks == null;

						if (section.blocks == null) {
							return;
						}
					}

					if (section != null) {
						if (section.blocks != null) {
							SECTION_BLOCKS[sectionIndex] = section.blocks;
						} else {
							SECTION_BLOCKS[sectionIndex] = DEFAULT_SHORT_ARRAY;
						}

						if (section.data != null) {
							SECTION_DATA[sectionIndex] = section.data.data;
						} else {
							SECTION_DATA[sectionIndex] = DEFAULT_BYTE_ARRAY;
						}

						SKY_LIGHT[sectionIndex] = section.skylightMap != null ? section.skylightMap.data : DEFAULT_BYTE_ARRAY;
						BLOCK_LIGHT[sectionIndex] = section.blocklightMap != null ? section.blocklightMap.data : DEFAULT_BYTE_ARRAY;
					} else {
						SECTION_BLOCKS[sectionIndex] = DEFAULT_SHORT_ARRAY;
						SKY_LIGHT[sectionIndex] = DEFAULT_BYTE_ARRAY;
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

	public boolean isSectionEmpty() {
		return this.centerSectEmpty;
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

		return SECTION_BLOCKS[sectInd][blockInd];
	}

	public int getBlockIdCenter(int x, int y, int z) {
		return CENTER_BLOCKS[makeBlockIndex(x & 15, y & 15, z & 15)];
	}

	public int getBlockIdCenter(int blockIndex) {
		return CENTER_BLOCKS[blockIndex];
	}

	@Override
	public @Nullable Block<?> getBlock(int x, int y, int z) {
		return Blocks.getBlock(this.getBlockId(x, y, z));
	}

	@Override
	public TileEntity getTileEntity(int i, int j, int k) {
		return null;
	}

	@Override
	public float getBrightness(int i, int j, int k, int l) {
		return 0;
	}

	@Override
	public int getLightmapCoord(int x, int y, int z, int blockLightValue) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

		int sectionIndex = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);

		if (BlocksFlags.SOLID[SECTION_BLOCKS[sectionIndex][blockIndex]]) {
			return 0;
		}

		int skyLight = getNibble(SKY_LIGHT[sectionIndex], blockIndex);
		int blockLight = getNibble(BLOCK_LIGHT[sectionIndex], blockIndex);

		return LightmapHelper.getLightmapCoord(skyLight, blockLight);
	}

	@Override
	public int getLightmapCoord(int skylight, int blockLight) {
		return this.worldObj.getLightmapCoord(skylight, blockLight);
	}

	@Override
	public float getLightBrightness(int x, int y, int z) {
		return this.worldObj.worldType.getBrightnessRamp()[this.getLightValue(x, y, z)];
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

		return SECTION_DATA[sectInd][blockInd];
	}

	public int getBlockMetadataCenter(int x, int y, int z) {
		return CENTER_DATA[makeBlockIndex(x & 15, y & 15, z & 15)];
	}

	@Override
	public Material getBlockMaterial(int x, int y, int z) {
		return BlocksFlags.MATERIAL[this.getBlockId(x, y, z)];
	}

	public int isBlockOpaqueCubeInt(int x, int y, int z) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

		int sectionIndex = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);

		return BlocksFlags.SOLID_LIGHT_MASK[SECTION_BLOCKS[sectionIndex][blockIndex]];
	}

	public int isBlockOpaqueCubeRel(int x, int y, int z) {
		int sectionIndex = sectionIndex(x >> 4, y >> 4, z >> 4);
		int blockInd = makeBlockIndex(x & 15, y & 15, z & 15);

		return BlocksFlags.SOLID_LIGHT_MASK[SECTION_BLOCKS[sectionIndex][blockInd]];
	}

	@Override
	public boolean isBlockOpaqueCube(int x, int y, int z) {
		return this.isBlockOpaqueCubeInt(x, y, z) != 0;
	}

	public int isBlockOpaqueCubeCenter(int blockIndex) {
		return BlocksFlags.SOLID_LIGHT_MASK[CENTER_BLOCKS[blockIndex]];
	}

	@Override
	public boolean isBlockNormalCube(int x, int y, int z) {
		int blockId = this.getBlockId(x, y, z);

		if (blockId == 0) {
			return false;
		}

		// Shouldn't be null as it can't be a air block, but who knows.
		Block<?> block = Blocks.getBlock(blockId);

		return block.getMaterial().blocksMotion() && block.isCubeShaped();
	}

	@Override
	public double getBlockTemperature(int x, int z) {
		return this.worldObj.getBlockTemperature(x, z);
	}

	@Override
	public double getBlockHumidity(int x, int z) {
		return this.worldObj.getBlockHumidity(x, z);
	}

	@Override
	public SeasonManager getSeasonManager() {
		return this.worldObj.getSeasonManager();
	}

	@Override
	public Biome getBlockBiome(int x, int y, int z) {
		return this.worldObj.getBlockBiome(x, y, z);
	}

	@Override
	public int getSavedLightValue(LightLayer layer, int x, int y, int z) {
		return this.worldObj.getSavedLightValue(layer, x, y, z);
	}

	@Override
	public boolean isRetro() {
		return this.worldObj.isRetro();
	}
}
