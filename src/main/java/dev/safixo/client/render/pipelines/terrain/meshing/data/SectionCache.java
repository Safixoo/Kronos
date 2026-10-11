package dev.safixo.client.render.pipelines.terrain.meshing.data;

import dev.safixo.client.render.pipelines.terrain.meshing.model.ModelColorizer;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.NibbleUtil;
import dev.safixo.client.util.collection.ObjectPooler;
import dev.safixo.client.util.data.ClientChunkListener;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3Pool;
import net.minecraft.world.ColorizerFoliage;
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
	public static final byte[] BYTE_ARRAY = new byte[16 * 16 * 16];
	public static final byte[] FULL_BYTE_ARRAY = new byte[16 * 16 * 16];

	public static final int BIOME_RADIUS = 3;
	private static final int BIOME_CHUNK_WIDTH = 16 + (BIOME_RADIUS * 2);

	static {
		Arrays.fill(FULL_BYTE_ARRAY, (byte) 0xFF);
	}

	private final Chunk[] chunks = new Chunk[3 * 3];

	private final ModelColorizer colorizer;
	private final FakeInlinedBiome inlinedBiome;
	private final Vec3Pool pool;
	private boolean uniformBiomes;

	private World world;
	public int blockX, blockY, blockZ;

	private final byte[] biomes = new byte[BIOME_CHUNK_WIDTH * BIOME_CHUNK_WIDTH];

	private final byte[][] sectionBlocksLsb = new byte[3 * 3 * 3][];
	private final byte[][] sectionBlocksMsb = new byte[3 * 3 * 3][];

	private final byte[][] sectionData = new byte[3 * 3 * 3][];

	private final byte[][] skyLight = new byte[3 * 3 * 3][];
	private final byte[][] blockLight = new byte[3 * 3 * 3][];

	private final int[] uniformColors = new int[ModelColorizer.MAX_COLOR_TYPES];

	private byte[] centerBlocksLsb, centerBlocksMsb;
	private byte[] centerSkylight, centerBlocklight;
	private byte[] visitedSectionBlocks;

	private boolean centerSectEmpty;

	public SectionCache(ModelColorizer colorizer, FakeInlinedBiome inlinedBiome, Vec3Pool pool) {
		this.clearReferences();

		this.colorizer = colorizer;
		this.inlinedBiome = inlinedBiome;
		this.pool = pool;

		Arrays.fill(this.uniformColors, 0xFFFFFFFF);
	}

	public void setupCache(World world, int blockX, int blockY, int blockZ) {
		this.world = world;

		this.blockX = blockX - 16;
		this.blockY = blockY - 16;
		this.blockZ = blockZ - 16;

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

		Arrays.fill(this.skyLight, BYTE_ARRAY);
		Arrays.fill(this.blockLight, BYTE_ARRAY);

		for (int x = minSectionX; x <= maxSectionX; x++) {
			for (int z = minSectionZ; z <= maxSectionZ; z++) {
				int relX = x - minSectionX;
				int relZ = z - minSectionZ;

				Chunk chunk = centerX == x && centerZ == z ? centerChunk : world.getChunkFromChunkCoords(x, z);
				this.chunks[sectionIndex(relX, 0, relZ)] = chunk;

				for (int y = minSectionY; y <= maxSectionY; y++) {
					if (y < 0 || y > 15) {
						continue;
					}
					ExtendedBlockStorage section = chunk.getBlockStorageArray()[y];

					int relY = y - minSectionY;
					int sectionIndex = sectionIndex(relX, relY, relZ);

					if (section != null) {
						if (!section.isEmpty()) {
							this.sectionBlocksLsb[sectionIndex] = section.getBlockLSBArray();
							this.sectionData[sectionIndex] = section.getMetadataArray().data;

							if (section.getBlockMSBArray() != null) {
								this.sectionBlocksMsb[sectionIndex] = section.getBlockMSBArray().data;
							} else {
								this.sectionBlocksMsb[sectionIndex] = BYTE_ARRAY;
							}
						} else {
							this.sectionBlocksLsb[sectionIndex] = BYTE_ARRAY;
							this.sectionBlocksMsb[sectionIndex] = BYTE_ARRAY;
							this.sectionData[sectionIndex] = BYTE_ARRAY;
						}

						if (world.provider.hasNoSky) {
							this.skyLight[sectionIndex] = BYTE_ARRAY;
						} else {
							this.skyLight[sectionIndex] = section.getSkylightArray().data;
						}
						this.blockLight[sectionIndex] = section.getBlocklightArray().data;
					} else {
						// Probably don't totally correct but enough for most cases.
						if (world.provider.hasNoSky) {
							this.skyLight[sectionIndex] = BYTE_ARRAY;
						} else {
							this.skyLight[sectionIndex] = FULL_BYTE_ARRAY;
						}
						this.blockLight[sectionIndex] = BYTE_ARRAY;
					}
				}
			}
		}

		this.copyBiomes(world);

		this.centerSkylight = this.skyLight[sectionIndex(1, 1, 1)];
		this.centerBlocklight = this.blockLight[sectionIndex(1, 1, 1)];

		this.centerBlocksMsb = this.sectionBlocksMsb[sectionIndex(1, 1, 1)];
		this.centerBlocksLsb = this.sectionBlocksLsb[sectionIndex(1, 1, 1)];
	}

	public void clearReferences() {
		for (int i = 0; i < 27; i++) {
			this.skyLight[i] = BYTE_ARRAY;
			this.blockLight[i] = BYTE_ARRAY;
			this.sectionBlocksLsb[i] = BYTE_ARRAY;
			this.sectionBlocksMsb[i] = BYTE_ARRAY;
			this.sectionData[i] = BYTE_ARRAY;
		}

		this.centerBlocklight = BYTE_ARRAY;
		this.centerSkylight = BYTE_ARRAY;

		Arrays.fill(this.chunks, null);
		this.world = null;
	}

	private void copyBiomes(World world) {
		int centerBlockX = this.blockX + 16;
		int centerBlockZ = this.blockZ + 16;

		int minSectionX = this.blockX >> 4;
		int minSectionZ = this.blockZ >> 4;

		int lastBiome = -1;
		boolean uniformBiome = true;

		for (int biomeX = centerBlockX - BIOME_RADIUS; biomeX < centerBlockX + 16 + BIOME_RADIUS; biomeX++) {
			for (int biomeZ = centerBlockZ - BIOME_RADIUS; biomeZ < centerBlockZ + 16 + BIOME_RADIUS; biomeZ++) {
				int relChunkX = (biomeX >> 4) - minSectionX;
				int relChunkZ = (biomeZ >> 4) - minSectionZ;

				int relBiomeX = biomeX - (centerBlockX - BIOME_RADIUS);
				int relBiomeZ = biomeZ - (centerBlockZ - BIOME_RADIUS);

				int chunkIndex = sectionIndex(relChunkX, 0, relChunkZ);
				Chunk chunk = this.chunks[chunkIndex];

				byte[] biomes = chunk.getBiomeArray();
				int biome = biomes[biomeIndex(biomeX & 15, biomeZ & 15)] & 0xFF;

				if (biome == 0xFF) {
					this.copyBiomeFromLayer(world, chunk);
					biome = biomes[biomeIndex(biomeX & 15, biomeZ & 15)] & 0xFF;
				}

				if (lastBiome == -1) {
					lastBiome = biome;
				}

				if (lastBiome != biome) {
					uniformBiome = false;
				}

				this.biomes[relBiomeX + relBiomeZ * BIOME_CHUNK_WIDTH] = (byte) biome;
			}
		}

		this.uniformBiomes = uniformBiome;
	}

	public void checkUniformBiomes() {
		BiomeGenBase biome = BiomeGenBase.biomeList[this.biomes[0]];

		if (this.uniformBiomes) {
			int waterColor = this.uniformColors[ModelColorizer.WATER_COLOR] = biome.getWaterColorMultiplier();
			int grassColor = this.uniformColors[ModelColorizer.GRASS_COLOR] = biome.getBiomeGrassColor();
			int foliageColor = this.uniformColors[ModelColorizer.FOLIAGE_COLOR] = biome.getBiomeFoliageColor();

			this.inlinedBiome.setColors(waterColor, foliageColor, grassColor);
		}
	}

	// Populates all biomes at once, and avoids using BiomeCache or populating one biome per
	// chance.
	private void copyBiomeFromLayer(World world, Chunk chunk) {
		WorldChunkManager chunkManager = world.getWorldChunkManager();
		byte[] chunkBiomes = chunk.getBiomeArray();
		int[] layerBiomes = chunkManager.genBiomes.getInts(chunk.xPosition, chunk.zPosition, 16, 16);

		for (int i = 0; i < 16 * 16; i++) {
			chunkBiomes[i] = (byte) layerBiomes[i];
		}
	}

	public boolean hasUniformBiomes() {
		return this.uniformBiomes;
	}

	public int getColorByType(int x, int y, int z, int colorizeType) {
		if (colorizeType == ModelColorizer.FOLIAGE_COLOR) {
			int meta = this.getBlockMetadata(x, y, z);

			if ((meta & 0b11) == 0b01) {
				return ColorizerFoliage.getFoliageColorPine();
			}
			if ((meta & 0b11) == 0b10) {
				return ColorizerFoliage.getFoliageColorBirch();
			}
		}

		return this.uniformColors[colorizeType];
	}

	private static int biomeIndex(int x, int z) {
		return x | z << 4;
	}

	public ModelColorizer getColorizer() {
		return this.colorizer;
	}

	public static int sectionIndex(int x, int y, int z) {
		return x + (z * 3) + (y * 9);
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

		int blockIdLsb = MathExt.byteToUnsigned(this.sectionBlocksLsb[sectionIndex][blockIndex]);
		int blockIdMsb = NibbleUtil.getNibble(this.sectionBlocksMsb[sectionIndex], blockIndex);

		return blockIdLsb | blockIdMsb << 8;
	}

	public int getBlockId(int sectionIndex, int blockIndex) {
		int blockIdLsb = MathExt.byteToUnsigned(this.sectionBlocksLsb[sectionIndex][blockIndex]);
		int blockIdMsb = NibbleUtil.getNibble(this.sectionBlocksMsb[sectionIndex], blockIndex);

		return blockIdLsb | blockIdMsb << 8;
	}

	@Override
	public TileEntity getBlockTileEntity(int x, int y, int z) {
		int chunkX = x - this.blockX;
		int chunkZ = z - this.blockZ;

		return this.chunks[sectionIndex(chunkX >> 4, 0, chunkZ >> 4)].getChunkBlockTileEntity(x & 15, y, z & 15);
	}

	@Override
	public int getLightBrightnessForSkyBlocks(int x, int y, int z, int defBlockLight) {
		return this.getLightmap(x, y, z, defBlockLight);
	}

	public int getLightmap(int x, int y, int z, int defBlockLight) {
		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

		int blockIndex = makeBlockIndex(blockX & 15, blockY & 15, blockZ & 15);
		int sectionIndex = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);

		return extractLightNibbles(this.skyLight[sectionIndex], this.blockLight[sectionIndex], blockIndex, defBlockLight);
	}

	public int getLightmap(int sectionIndex, int blockIndex) {
		return extractLightNibbles(this.skyLight[sectionIndex], this.blockLight[sectionIndex], blockIndex, 0);
	}

	public int getLightmap(int x, int y, int z) {
		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);
		int sectionIndex = sectionIndex(blockX >> 4, blockY >> 4, blockZ >> 4);

		return extractLightNibbles(this.skyLight[sectionIndex], this.blockLight[sectionIndex], blockIndex);
	}

	public int getLightmapCenter(int blockIndex) {
		return extractLightNibbles(this.centerSkylight, this.centerBlocklight, blockIndex);
	}

	private static int extractLightNibbles(byte[] skyLightArray, byte[] blockLightArray, int blockIndex, int minBlockLight) {
		int skyLight = NibbleUtil.getNibble(skyLightArray, blockIndex);
		int blockLight = NibbleUtil.getNibble(blockLightArray, blockIndex);
		return MathExt.getLightmapCoord(skyLight, Math.max(minBlockLight, blockLight));
	}

	private static int extractLightNibbles(byte[] skyLightArray, byte[] blockLightArray, int blockIndex) {
		int skyLight = NibbleUtil.getNibble(skyLightArray, blockIndex);
		int blockLight = NibbleUtil.getNibble(blockLightArray, blockIndex);
		return MathExt.getLightmapCoord(skyLight, blockLight);
	}

	public int getBlockIdCenter(int blockIndex) {
		int blockIdLsb = MathExt.byteToUnsigned(this.centerBlocksLsb[blockIndex]);
		int blockIdMsb = NibbleUtil.getNibble(this.centerBlocksMsb, blockIndex);

		return blockIdLsb | blockIdMsb << 8;
	}

	// AFAIK, not used for rendering.
	public float getBrightness(int x, int y, int z, int min) {
		return 0;
	}

	@Override
	public float getLightBrightness(int x, int y, int z) {
		return this.world.provider.lightBrightnessTable[this.getLightValue(x, y, z)];
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

		return NibbleUtil.getNibble(this.sectionData[sectionIndex], blockIndex);
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
		return this.visitedSectionBlocks[blockIndex];
	}

	@Override
	public boolean isBlockNormalCube(int x, int y, int z) {
		return PrimitivesFlags.NORMAL_BLOCK[this.getBlockId(x, y, z)];
	}

	@Override
	public boolean isAirBlock(int x, int y, int z) {
		return this.getBlockId(x, y, z) == 0;
	}

	public int getBiomeRaw(int x, int z) {
		int biomeX = x - (this.blockX + 16 - BIOME_RADIUS);
		int biomeZ = z - (this.blockZ + 16 - BIOME_RADIUS);

		return this.biomes[biomeX + biomeZ * BIOME_CHUNK_WIDTH] & 0xFF;
	}

	@Override
	public BiomeGenBase getBiomeGenForCoords(int x, int z) {
		if (this.uniformBiomes) {
			return this.inlinedBiome;
		}

		return BiomeGenBase.biomeList[this.getBiomeRaw(x, z)];
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
		return this.isBlockSolidOnSide(x, y, z, Direction.UP, false);
	}

	// Block#isBlockSolidOnSide
	private boolean isBlockSolidOnSide(int x, int y, int z, int side, boolean defaultValue) {
		int blockId = this.getBlockId(x, y, z);

		if (blockId == 0) {
			return defaultValue;
		}

		int meta = this.getBlockMetadata(x, y, z);
		Block block = Block.blocksList[blockId];

		if (block instanceof BlockHalfSlab) {
			return (meta & 8) == 8 && side == Direction.UP || block.isOpaqueCube();
		} else if (block instanceof BlockFarmland) {
			return side != Direction.DOWN && side != Direction.UP;
		} else if (block instanceof BlockStairs) {
			boolean flipped = (meta & 4) != 0;
			return (meta & 3) + side == 5 || side == Direction.UP && flipped;
		} else if (block instanceof BlockHopper && side == Direction.UP) {
			return true;
		} else {
			return block instanceof BlockPoweredOre || PrimitivesFlags.NORMAL_BLOCK[blockId];
		}
	}

	@Override
	public Vec3Pool getWorldVec3Pool() {
		return this.pool;
	}

	@Override
	public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
		int id = this.getBlockId(x, y, z);
		return id == 0 ? 0 : Block.blocksList[id].isProvidingStrongPower(this, x, y, z, side);
	}

	@Override
	public boolean isBlockSolidOnSide(int x, int y, int z, ForgeDirection side, boolean defaultValue) {
		return this.isBlockSolidOnSide(x, y, z, side.ordinal(), defaultValue);
	}

	public void setVisitedArray(byte[] array) {
		this.visitedSectionBlocks = array;
	}
}
