package dev.safixo.client.render.pipelines.terrain.meshing.data;

import dev.safixo.client.render.pipelines.terrain.meshing.model.ModelColorizer;
import dev.safixo.client.util.MathExt;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3Pool;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.ForgeDirection;
import dev.safixo.client.util.data.PrimitivesFlags;

import java.util.Arrays;

public class SectionCache implements IBlockAccess {
	private static final FakeInlinedBiome FAKE_BIOME = new FakeInlinedBiome(Integer.MAX_VALUE);
	private static final Chunk[] CHUNKS = new Chunk[3 * 3];
	private static final ModelColorizer COLORIZER = new ModelColorizer();

	public static final byte[] DEFAULT_BYTE_ARRAY = new byte[16 * 16 * 16];
	public static final byte[] DEFAULT_FULL_BYTE_ARRAY = new byte[16 * 16 * 16];

	static {
		Arrays.fill(DEFAULT_FULL_BYTE_ARRAY, (byte) 0xFF);
	}

	private final World worldObj;
	public final int blockX, blockY, blockZ;
	private boolean uniformBiome;

	public static final int BIOME_RADIUS = 1;
	private static final int BIOME_CHUNK_WIDTH = 16 + (BIOME_RADIUS * 2);

	private static final BiomeGenBase[] BIOMES = new BiomeGenBase[BIOME_CHUNK_WIDTH * BIOME_CHUNK_WIDTH];
	private static final int[] BIOMES_COLOR = new int[BIOME_CHUNK_WIDTH * BIOME_CHUNK_WIDTH];

	public static final byte[][] SECTION_BLOCKS = new byte[3 * 3 * 3][];
	public static final byte[][] SECTION_DATA = new byte[3 * 3 * 3][];
	public static final byte[][] SKY_LIGHT = new byte[3 * 3 * 3][];
	public static final byte[][] BLOCK_LIGHT = new byte[3 * 3 * 3][];

	public static byte[] CENTER_BLOCKS;
	public static byte[] CENTER_METADATA;

	public static byte[] CENTER_SKYLIGHT;
	public static byte[] CENTER_BLOCKLIGHT;

	public static final byte[] VISITED_CENTER_BLOCKS = new byte[4096];

	private boolean centerSectEmpty;
	public int[] biomeColors = new int[ModelColorizer.MAX_COLOR_TYPES];

	static {
		Arrays.fill(SECTION_BLOCKS, DEFAULT_BYTE_ARRAY);
		Arrays.fill(SECTION_DATA, DEFAULT_BYTE_ARRAY);
		Arrays.fill(SKY_LIGHT, DEFAULT_FULL_BYTE_ARRAY);
		Arrays.fill(BLOCK_LIGHT, DEFAULT_BYTE_ARRAY);
	}

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
				CHUNKS[sectionIndex(relX, 0, relZ)] = chunk;

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

						if (section.getSkylightArray() == null) {
							// Case where dimension doesn't support lighting (nether for example).
							SKY_LIGHT[sectionIndex] = DEFAULT_BYTE_ARRAY;
						} else if (section.getSkylightArray().data == null) {
							// Case where the lighting is the default of the type (for the sky light-type is 15).
							SKY_LIGHT[sectionIndex] = DEFAULT_FULL_BYTE_ARRAY;
						} else {
							// Base case, there is lighting and everybody is happy :).
							SKY_LIGHT[sectionIndex] = section.getSkylightArray().data;
						}

						BLOCK_LIGHT[sectionIndex] = section.getBlocklightArray().data != null ? section.getBlocklightArray().data : DEFAULT_BYTE_ARRAY;
					} else {
						SECTION_BLOCKS[sectionIndex] = DEFAULT_BYTE_ARRAY;
						SECTION_DATA[sectionIndex] = DEFAULT_BYTE_ARRAY;
						SKY_LIGHT[sectionIndex] = DEFAULT_FULL_BYTE_ARRAY;
						BLOCK_LIGHT[sectionIndex] = DEFAULT_BYTE_ARRAY;
					}
				}
			}

			WorldChunkManager manager = this.worldObj.getWorldChunkManager();

			int centerBlockX = this.blockX + 16;
			int centerBlockZ = this.blockZ + 16;
			BiomeGenBase biome = null;
			boolean uniformed = true;

			for (int blockX = centerBlockX - BIOME_RADIUS; blockX < centerBlockX + 16 + BIOME_RADIUS; blockX++) {
				for (int blockZ = centerBlockZ - BIOME_RADIUS; blockZ < centerBlockZ + 16 + BIOME_RADIUS; blockZ++) {
					int actChunkX = (blockX >> 4) - sectionX;
					int actChunkZ = (blockZ >> 4) - sectionZ;

					int relBiomeX = blockX - (centerBlockX - BIOME_RADIUS);
					int relBiomeZ = blockZ - (centerBlockZ - BIOME_RADIUS);

					int chunkIndex = sectionIndex(actChunkX, 0, actChunkZ);

					Chunk chunk = CHUNKS[chunkIndex];
					BiomeGenBase biomeGenBase = BiomeGenBase.plains;

					if (chunk != null) {
						biomeGenBase = chunk.getBiomeGenForWorldCoords(blockX & 15, blockZ & 15, manager);
					}

					if (biome == null || biome != biomeGenBase) {
						uniformed = false;
					}

					biome = biomeGenBase;
					BIOMES[relBiomeX + relBiomeZ * BIOME_CHUNK_WIDTH] = biomeGenBase;
				}
			}

			this.uniformBiome = uniformed;

			if (uniformed) {
				int grassColor = COLORIZER.getBlockGrassColor(this, this.blockX + 16, this.blockY + 16, this.blockZ + 16);
				int foliageColor = COLORIZER.getBlockLeavesColor(this, this.blockX + 16, this.blockY + 16, this.blockZ + 16);
				int waterColor = COLORIZER.getBlockWaterColor(this, this.blockX + 16, this.blockY + 16, this.blockZ + 16);

				this.biomeColors[ModelColorizer.GRASS_COLOR] = grassColor;
				this.biomeColors[ModelColorizer.LEAVES_COLOR] = foliageColor;
				this.biomeColors[ModelColorizer.WATER_COLOR] = waterColor;
				this.biomeColors[ModelColorizer.DEFAULT_COLOR] = 0xFFFFFFFF;

				FAKE_BIOME.setColors(waterColor, foliageColor, grassColor);
				Arrays.fill(BIOMES, FAKE_BIOME);
			}
		}

		CENTER_SKYLIGHT = SKY_LIGHT[sectionIndex(1, 1, 1)];
		CENTER_BLOCKLIGHT = BLOCK_LIGHT[sectionIndex(1, 1, 1)];

		CENTER_BLOCKS = SECTION_BLOCKS[sectionIndex(1, 1, 1)];
		CENTER_METADATA = SECTION_DATA[sectionIndex(1, 1, 1)];
	}

	public static int sectionIndex(int x, int y, int z) {
		return x + (z * 3) + (y * 9);
	}

	public static int getNibble(byte[] nibbleArray, int blockIndex) {
		int nibbleIndex = blockIndex >> 1;
		int nibblePart = (blockIndex << 2) & 0b100;
		return nibbleArray[nibbleIndex] >>> nibblePart & 15;
	}

	public static int makeBlockIndex(int x, int y, int z) {
		return y << 8 | z << 4 | x;
	}

	@Override
	public int getBlockId(int x, int y, int z) {
		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

		int sectInd = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
		int blockInd = makeBlockIndex(blockX & 15, blockY & 15, blockZ & 15);

		return MathExt.byteToUnsigned(SECTION_BLOCKS[sectInd][blockInd]);
	}

	public boolean isBiomeUniform() {
		return this.uniformBiome;
	}

	@Override
	public TileEntity getBlockTileEntity(int x, int y, int z) {
		int chunkX = x - this.blockX;
		int chunkZ = z - this.blockZ;

		return CHUNKS[sectionIndex(chunkX >> 4, 0, chunkZ >> 4)].getChunkBlockTileEntity(x & 15, y, z & 15);
	}

	@Override
	public int getLightBrightnessForSkyBlocks(int x, int y, int z, int defBlockLight) {
		return this.getLight(x, y, z, defBlockLight);
	}

	public int getLight(int x, int y, int z, int defBlockLight) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

		int sectionIndex = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
		int skyLight = getNibble(SKY_LIGHT[sectionIndex], blockIndex);
		int blockLight = getNibble(BLOCK_LIGHT[sectionIndex], blockIndex);

		return MathExt.getLightmapCoord(skyLight, Math.max(defBlockLight, blockLight));
	}

	public int getLightCenter(int x, int y, int z, int defBlockLight) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);
		int skyLight = getNibble(CENTER_SKYLIGHT, blockIndex);
		int blockLight = getNibble(CENTER_BLOCKLIGHT, blockIndex);

		return MathExt.getLightmapCoord(skyLight, Math.max(defBlockLight, blockLight));
	}

	public int getLightCenter(int blockIndex, int defBlockLight) {
		int skyLight = getNibble(CENTER_SKYLIGHT, blockIndex);
		int blockLight = getNibble(CENTER_BLOCKLIGHT, blockIndex);

		return MathExt.getLightmapCoord(skyLight, Math.max(defBlockLight, blockLight));
	}

	public int getBlockIdCenter(int x, int y, int z) {
		return MathExt.byteToUnsigned(CENTER_BLOCKS[makeBlockIndex(x & 15, y & 15, z & 15)]);
	}

	public int getBlockIdCenter(int blockIndex) {
		return MathExt.byteToUnsigned(CENTER_BLOCKS[blockIndex]);
	}

	// AFAIK, not used for rendering.
	public float getBrightness(int x, int y, int z, int min) {
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
		return getNibble(CENTER_METADATA, makeBlockIndex(x & 15, y & 15, z & 15));
	}

	@Override
	public Material getBlockMaterial(int x, int y, int z) {
		int blockId = this.getBlockId(x, y, z);

		return PrimitivesFlags.MATERIAL[blockId];
	}

	public int isVoxelFull(int x, int y, int z) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

		int sectionIndex = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);

		return PrimitivesFlags.SOLID_CULL_MASK[MathExt.byteToUnsigned(SECTION_BLOCKS[sectionIndex][blockIndex])];
	}

	public int isVoxelFullRelative(int x, int y, int z) {
		int sectionIndex = sectionIndex(x >> 4, y >> 4, z >> 4);
		int blockInd = makeBlockIndex(x & 15, y & 15, z & 15);

		if (sectionIndex == sectionIndex(1, 1, 1)) {
			return this.isVoxelFullFromCenter(blockInd);
		}

		return PrimitivesFlags.SOLID_CULL_MASK[MathExt.byteToUnsigned(SECTION_BLOCKS[sectionIndex][blockInd])];
	}

	@Override
	public boolean isBlockOpaqueCube(int x, int y, int z) {
		return this.isVoxelFull(x, y, z) != 0;
	}

	public int isVoxelFullFromCenter(int blockIndex) {
		return SectionCache.VISITED_CENTER_BLOCKS[blockIndex];
	}

	@Override
	public boolean isBlockNormalCube(int x, int y, int z) {
		return PrimitivesFlags.NORMAL_BLOCK[this.getBlockId(x, y, z)];
	}

	@Override
	public boolean isAirBlock(int x, int y, int z) {
		return this.getBlockId(x, y, z) == 0;
	}

	@Override
	public BiomeGenBase getBiomeGenForCoords(int x, int z) {
		int biomeX = x - (this.blockX + 16 - BIOME_RADIUS);
		int biomeZ = z - (this.blockZ + 16 - BIOME_RADIUS);

		return BIOMES[biomeX + biomeZ * BIOME_CHUNK_WIDTH];
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
