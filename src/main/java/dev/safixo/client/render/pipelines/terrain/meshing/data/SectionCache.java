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

	private static final byte[][] SECTION_BLOCKS = new byte[3 * 3 * 3][];
	private static final byte[][] SECTION_BLOCKS_MSB = new byte[3 * 3 * 3][];

	private static final byte[][] SECTION_DATA = new byte[3 * 3 * 3][];

	private static final byte[][] SKY_LIGHT = new byte[3 * 3 * 3][];
	private static final byte[][] BLOCK_LIGHT = new byte[3 * 3 * 3][];

	private static byte[] CENTER_BLOCKS;
	private static byte[] CENTER_BLOCK_MSB;

	private static byte[] CENTER_SKYLIGHT;
	private static byte[] CENTER_BLOCKLIGHT;

	public static final byte[] VISITED_CENTER_BLOCKS = new byte[4096];

	private static int MSB_SECTIONS = 0;

	private boolean centerSectEmpty;
	public int[] biomeColors = new int[ModelColorizer.MAX_COLOR_TYPES];

	static {
		Arrays.fill(SECTION_BLOCKS, DEFAULT_BYTE_ARRAY);
		Arrays.fill(SECTION_DATA, DEFAULT_BYTE_ARRAY);
		Arrays.fill(SKY_LIGHT, DEFAULT_FULL_BYTE_ARRAY);
		Arrays.fill(BLOCK_LIGHT, DEFAULT_BYTE_ARRAY);
	}

	public SectionCache(World world, int blockX, int blockY, int blockZ) {
		this.worldObj = world;

		this.blockX = blockX - 16;
		this.blockY = blockY - 16;
		this.blockZ = blockZ - 16;

		this.fillData(world, blockX, blockY, blockZ);
	}

	public void fillData(World world, int blockX, int blockY, int blockZ) {
		int centerX = blockX >> 4, centerY = blockY >> 4, centerZ = blockZ >> 4;

		int minSectionX = centerX - 1;
		int minSectionY = centerY - 1;
		int minSectionZ = centerZ - 1;

		int maxSectionX = centerX + 1;
		int maxSectionY = centerY + 1;
		int maxSectionZ = centerZ + 1;

		Chunk centerChunk = world.getChunkFromChunkCoords(centerX, centerZ);
		ExtendedBlockStorage centerSection = centerChunk.getBlockStorageArray()[centerY];

		this.centerSectEmpty = centerSection == null || centerSection.isEmpty();

		if (this.centerSectEmpty) {
			return;
		}

		for (int section = 0; section < 27; section++) {
			MSB_SECTIONS = 0b0;

			SECTION_BLOCKS_MSB[section] = DEFAULT_BYTE_ARRAY;
			SECTION_BLOCKS[section] = DEFAULT_BYTE_ARRAY;
			SECTION_DATA[section] = DEFAULT_BYTE_ARRAY;
			SKY_LIGHT[section] = DEFAULT_FULL_BYTE_ARRAY;
			BLOCK_LIGHT[section] = DEFAULT_BYTE_ARRAY;
		}

		Arrays.fill(CHUNKS, null);

		for (int x = minSectionX; x <= maxSectionX; x++) {
			for (int z = minSectionZ; z <= maxSectionZ; z++) {
				int relX = x - minSectionX;
				int relZ = z - minSectionZ;

				Chunk chunk = centerX == x && centerZ == z ? centerChunk : world.getChunkFromChunkCoords(x, z);
				CHUNKS[sectionIndex(relX, 0, relZ)] = chunk;

				for (int y = minSectionY; y <= maxSectionY; y++) {
					if (y < 0 || y > 15) {
						continue;
					}

					ExtendedBlockStorage section = chunk.getBlockStorageArray()[y];
					int relY = y - minSectionY;
					int sectionIndex = sectionIndex(relX, relY, relZ);

					if (section != null) {
						if (!section.isEmpty()) {
							if (section.getBlockLSBArray() != null) {
								SECTION_BLOCKS[sectionIndex] = section.getBlockLSBArray();
							}
							if (section.getBlockMSBArray() != null) {
								SECTION_BLOCKS_MSB[sectionIndex] = section.getBlockMSBArray().data;
								MSB_SECTIONS |= 1 << sectionIndex;
							}
							if (section.getMetadataArray() != null) {
								SECTION_DATA[sectionIndex] = section.getMetadataArray().data;
							}
						}

						if (section.getSkylightArray() == null || this.worldObj.provider.hasNoSky) {
							// Case where dimension doesn't support lighting (nether for example).
							SKY_LIGHT[sectionIndex] = DEFAULT_BYTE_ARRAY;
						} else if (section.getSkylightArray().data != null) {
							// Base case, there is lighting and everybody is happy :).
							SKY_LIGHT[sectionIndex] = section.getSkylightArray().data;
						}
						if (section.getBlocklightArray() == null || section.getBlocklightArray().data != null) {
							BLOCK_LIGHT[sectionIndex] = section.getBlocklightArray().data;
						}
					}
				}
			}

			WorldChunkManager manager = this.worldObj.getWorldChunkManager();

			int centerBlockX = centerX << 4;
			int centerBlockZ = centerZ << 4;

			BiomeGenBase lastBiome = null;
			boolean uniformed = true;

			for (int biomeX = centerBlockX - BIOME_RADIUS; biomeX < centerBlockX + 16 + BIOME_RADIUS; biomeX++) {
				for (int biomeZ = centerBlockZ - BIOME_RADIUS; biomeZ < centerBlockZ + 16 + BIOME_RADIUS; biomeZ++) {
					int actChunkX = (biomeX >> 4) - minSectionX;
					int actChunkZ = (biomeZ >> 4) - minSectionZ;

					int relBiomeX = biomeX - (centerBlockX - BIOME_RADIUS);
					int relBiomeZ = biomeZ - (centerBlockZ - BIOME_RADIUS);

					int chunkIndex = sectionIndex(actChunkX, 0, actChunkZ);

					Chunk chunk = CHUNKS[chunkIndex];
					BiomeGenBase biomeGenBase = BiomeGenBase.plains;

					if (chunk != null) {
						biomeGenBase = chunk.getBiomeGenForWorldCoords(biomeX & 15, biomeZ & 15, manager);
					}

					if (lastBiome == null) {
						lastBiome = biomeGenBase;
					}

					if (lastBiome != biomeGenBase) {
						uniformed = false;
					}

					lastBiome = biomeGenBase;
					BIOMES[relBiomeX + relBiomeZ * BIOME_CHUNK_WIDTH] = biomeGenBase;
				}
			}

			this.uniformBiome = uniformed;

			if (uniformed && lastBiome != null) {
				int grassColor = lastBiome.getBiomeGrassColor();
				int foliageColor = lastBiome.getBiomeFoliageColor();
				int waterColor = lastBiome.getWaterColorMultiplier();

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

		CENTER_BLOCK_MSB = SECTION_BLOCKS_MSB[sectionIndex(1, 1, 1)];
		CENTER_BLOCKS = SECTION_BLOCKS[sectionIndex(1, 1, 1)];
	}

	public static int sectionIndex(int x, int y, int z) {
		return x + (z * 3) + (y * 9);
	}

	public static int getNibble(byte[] nibbleArray, int blockIndex) {
		int nibbleIndex = blockIndex >> 1;
		int nibblePart = (blockIndex << 2) & 0b100;
		return (nibbleArray[nibbleIndex] >>> nibblePart) & 0xF;
	}

	public static int makeBlockIndex(int x, int y, int z) {
		return y << 8 | z << 4 | x;
	}

	@Override
	public int getBlockId(int x, int y, int z) {
		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

		int sectionIndex = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
		int blockIndex = makeBlockIndex(blockX & 15, blockY & 15, blockZ & 15);

		int blockIdLsb = MathExt.byteToUnsigned(SECTION_BLOCKS[sectionIndex][blockIndex]);
		int blockIdMsb = getMsbNibble(sectionIndex, blockIndex);

		return blockIdLsb | blockIdMsb << 8;
	}

	public int getBlockId(int sectionIndex, int blockIndex) {
		int blockIdLsb = MathExt.byteToUnsigned(SECTION_BLOCKS[sectionIndex][blockIndex]);
		int blockIdMsb = getMsbNibble(sectionIndex, blockIndex);

		return blockIdLsb | blockIdMsb << 8;
	}

	private static int getMsbNibble(int sectionIndex, int blockIndex) {
		return (MSB_SECTIONS & sectionIndex) != 0 ? getNibble(SECTION_BLOCKS_MSB[sectionIndex], blockIndex) : 0;
	}

	private static int getMsbNibbleCenter(int blockIndex) {
		return (MSB_SECTIONS & sectionIndex(1, 1, 1)) != 0 ? getNibble(CENTER_BLOCK_MSB, blockIndex) : 0;
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
		return this.getLightmap(x, y, z, defBlockLight);
	}

	public int getLightmap(int x, int y, int z, int defBlockLight) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);
		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

		int sectionIndex = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
		return extractLightNibbles(SKY_LIGHT[sectionIndex], BLOCK_LIGHT[sectionIndex], blockIndex, defBlockLight);
	}

	public int getLightmap(int sectionIndex, int blockIndex) {
		return extractLightNibbles(SKY_LIGHT[sectionIndex], BLOCK_LIGHT[sectionIndex], blockIndex, 0);
	}

	public int getLightmap(int x, int y, int z) {
		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);
		int sectionIndex = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);

		return extractLightNibbles(SKY_LIGHT[sectionIndex], BLOCK_LIGHT[sectionIndex], blockIndex);
	}

	public int getLightmapCenter(int blockIndex) {
		return extractLightNibbles(CENTER_SKYLIGHT, CENTER_BLOCKLIGHT, blockIndex);
	}

	private static int extractLightNibbles(byte[] skyLightArray, byte[] blockLightArray, int blockIndex, int minBlockLight) {
		int skyLight = getNibble(skyLightArray, blockIndex);
		int blockLight = getNibble(blockLightArray, blockIndex);
		return MathExt.getLightmapCoord(skyLight, Math.max(minBlockLight, blockLight));
	}

	private static int extractLightNibbles(byte[] skyLightArray, byte[] blockLightArray, int blockIndex) {
		int skyLight = getNibble(skyLightArray, blockIndex);
		int blockLight = getNibble(blockLightArray, blockIndex);
		return MathExt.getLightmapCoord(skyLight, blockLight);
	}

	public int getBlockIdCenter(int x, int y, int z) {
		return this.getBlockIdCenter(makeBlockIndex(x & 15, y & 15, z & 15));
	}

	public int getBlockIdCenter(int blockIndex) {
		int blockIdLsb = MathExt.byteToUnsigned(CENTER_BLOCKS[blockIndex]);
		int blockIdMsb = getMsbNibbleCenter(blockIndex);

		return blockIdLsb | blockIdMsb << 8;
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

		int sectionIndex = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);
		int blockIndex = makeBlockIndex(blockX & 15, blockY & 15, blockZ & 15);

		return getNibble(SECTION_DATA[sectionIndex], blockIndex);
	}

	@Override
	public Material getBlockMaterial(int x, int y, int z) {
		int blockId = this.getBlockId(x, y, z);

		return PrimitivesFlags.MATERIAL[blockId];
	}

	public int isVoxelFull(int x, int y, int z) {
		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);
		int sectionIndex = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);

		return PrimitivesFlags.SOLID_CULL_MASK[this.getBlockId(sectionIndex, blockIndex)];
	}

	public int isVoxelFullRel(int x, int y, int z) {
		int sectionIndex = sectionIndex(x >> 4, y >> 4, z >> 4);
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		if (sectionIndex == sectionIndex(1, 1, 1)) {
			return this.isVoxelFullRel(blockIndex);
		}

		return PrimitivesFlags.SOLID_CULL_MASK[this.getBlockId(sectionIndex, blockIndex)];
	}

	@Override
	public boolean isBlockOpaqueCube(int x, int y, int z) {
		return this.isVoxelFull(x, y, z) != 0;
	}

	public int isVoxelFullRel(int blockIndex) {
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
