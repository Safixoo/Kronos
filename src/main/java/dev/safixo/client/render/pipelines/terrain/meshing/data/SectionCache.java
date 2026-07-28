package dev.safixo.client.render.pipelines.terrain.meshing.data;

import dev.safixo.client.render.pipelines.terrain.meshing.model.ModelColorizer;
import dev.safixo.client.util.Direction;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.collection.ObjectPooler;
import net.minecraft.block.*;
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
	public static final byte[] BYTE_ARRAY = new byte[16 * 16 * 16];
	public static final byte[] NIBBLE_ARRAY = new byte[16 * 16 * 16 / 2];

	public static final byte[] FULL_BYTE_ARRAY = new byte[16 * 16 * 16];
	public static final byte[] FULL_NIBBLE_ARRAY = new byte[16 * 16 * 16 / 2];

	public static final int BIOME_RADIUS = 3;
	private static final int BIOME_CHUNK_WIDTH = 16 + (BIOME_RADIUS * 2);

	static {
		Arrays.fill(FULL_BYTE_ARRAY, (byte) 0xFF);
		Arrays.fill(FULL_NIBBLE_ARRAY, (byte) 0xFF);
	}

	private final Chunk[] chunks = new Chunk[3 * 3];

	private final ModelColorizer colorizer;
	private final FakeInlinedBiome inlinedBiome;
	private final Vec3Pool pool;
	private boolean uniformBiomes;

	private World world;
	public int blockX, blockY, blockZ;

	private final byte[] biomes = new byte[BIOME_CHUNK_WIDTH * BIOME_CHUNK_WIDTH];

	private final byte[][] sectionBlocks = new byte[3 * 3 * 3][4096];
	private final byte[][] sectionBlocksMsb = new byte[3 * 3 * 3][2048];

	private final byte[][] sectionData = new byte[3 * 3 * 3][2048];

	private final byte[][] skyLight = new byte[3 * 3 * 3][2048];
	private final byte[][] blockLight = new byte[3 * 3 * 3][2048];

	private final int[] uniformColors = new int[ModelColorizer.MAX_COLOR_TYPES];

	private byte[] centerBlocks, centerBlocksMsb;
	private byte[] centerSkylight, centerBlocklight;

	private byte[] visitedSectionBlocks;

	private boolean centerSectEmpty;

	private final ObjectPooler<byte[]> byteArrays = new ObjectPooler<>(null, 27);
	private final ObjectPooler<byte[]> nibbleArrays = new ObjectPooler<>(null, 27 * 4);

	public SectionCache(ModelColorizer colorizer, FakeInlinedBiome inlinedBiome, Vec3Pool pool) {
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

		Arrays.fill(this.chunks, null);

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

					int minIndex = this.getMinBlockIndex(sectionIndex);
					int maxIndex = this.getMaxBlockIndex(sectionIndex);

					if (section != null) {
						if (!section.isEmpty()) {
							this.sectionBlocks[sectionIndex] = this.popByteArray(this.sectionBlocks[sectionIndex]);
							this.sectionData[sectionIndex] = this.popNibbleArray(this.sectionData[sectionIndex]);

							if (section.getBlockMSBArray() != null) {
								this.sectionBlocksMsb[sectionIndex] = this.popNibbleArray(this.sectionBlocksMsb[sectionIndex]);
								copyNibble(this.sectionBlocksMsb[sectionIndex], section.getBlockMSBArray().data, minIndex, maxIndex);
							}
							copy(this.sectionBlocks[sectionIndex], section.getBlockLSBArray(), minIndex, maxIndex);
							copyNibble(this.sectionData[sectionIndex], section.getMetadataArray().data, minIndex, maxIndex);
						} else {
							this.sectionBlocks[sectionIndex] = this.pushByteArray(this.sectionBlocks[sectionIndex]);
							this.sectionBlocksMsb[sectionIndex] = this.pushNibbleArray(this.sectionBlocksMsb[sectionIndex]);
							this.sectionData[sectionIndex] = this.pushNibbleArray(this.sectionData[sectionIndex]);
						}

						if (!this.world.provider.hasNoSky) {
							this.skyLight[sectionIndex] = this.popSkyArray(this.skyLight[sectionIndex]);
							copyNibble(this.skyLight[sectionIndex], section.getSkylightArray().data, minIndex, maxIndex);
						} else {
							this.skyLight[sectionIndex] = this.pushSkyArray(this.skyLight[sectionIndex], NIBBLE_ARRAY);
						}

						this.blockLight[sectionIndex] = this.popNibbleArray(this.blockLight[sectionIndex]);
						copyNibble(this.blockLight[sectionIndex], section.getBlocklightArray().data, minIndex, maxIndex);
					} else {
						this.skyLight[sectionIndex] = this.pushSkyArray(this.skyLight[sectionIndex], FULL_NIBBLE_ARRAY);

						this.sectionBlocks[sectionIndex] = this.pushByteArray(this.sectionBlocks[sectionIndex]);
						this.sectionBlocksMsb[sectionIndex] = this.pushNibbleArray(this.sectionBlocksMsb[sectionIndex]);
						this.sectionData[sectionIndex] = this.pushNibbleArray(this.sectionData[sectionIndex]);

						this.blockLight[sectionIndex] = this.pushNibbleArray(this.blockLight[sectionIndex]);
					}
				}
			}
		}

		this.copyBiomes(world);

		this.centerSkylight = this.skyLight[sectionIndex(1, 1, 1)];
		this.centerBlocklight = this.blockLight[sectionIndex(1, 1, 1)];

		this.centerBlocksMsb = this.sectionBlocksMsb[sectionIndex(1, 1, 1)];
		this.centerBlocks = this.sectionBlocks[sectionIndex(1, 1, 1)];
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
				int actChunkX = (biomeX >> 4) - minSectionX;
				int actChunkZ = (biomeZ >> 4) - minSectionZ;

				int relBiomeX = biomeX - (centerBlockX - BIOME_RADIUS);
				int relBiomeZ = biomeZ - (centerBlockZ - BIOME_RADIUS);

				int chunkIndex = sectionIndex(actChunkX, 0, actChunkZ);
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

		this.uniformBiomes = false;
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

	public int getColorByType(int colorTypeId) {
		return this.uniformColors[colorTypeId];
	}

	private static int biomeIndex(int x, int z) {
		return x | z << 4;
	}

	public ModelColorizer getColorizer() {
		return this.colorizer;
	}

	private byte[] popSkyArray(byte[] array) {
		if (array == NIBBLE_ARRAY || array == FULL_NIBBLE_ARRAY) {
			return this.nibbleArrays.poll();
		}
		return array;
	}

	private byte[] pushSkyArray(byte[] array, byte[] defaultArray) {
		if (array != FULL_NIBBLE_ARRAY && array != NIBBLE_ARRAY) {
			this.nibbleArrays.push(array);
		}
		return defaultArray;
	}

	private byte[] popNibbleArray(byte[] array) {
		if (array == NIBBLE_ARRAY) {
			return this.nibbleArrays.poll();
		}
		return array;
	}

	private byte[] popByteArray(byte[] array) {
		if (array == BYTE_ARRAY) {
			return this.byteArrays.poll();
		}
		return array;
	}

	private byte[] pushNibbleArray(byte[] array) {
		if (array != NIBBLE_ARRAY) {
			this.nibbleArrays.push(array);
		}
		return NIBBLE_ARRAY;
	}

	private byte[] pushByteArray(byte[] array) {
		if (array != BYTE_ARRAY) {
			this.byteArrays.push(array);
		}
		return BYTE_ARRAY;
	}

	private static final int RADIUS = 1;

	private void copy(byte[] to, byte[] from, int minIndex, int maxIndex) {
		int copyLength = maxIndex - minIndex + 1;

		if (copyLength == 1){
			to[minIndex] = from[minIndex];
		} else if (copyLength == 16*16*16 || copyLength == 16*16) {
			System.arraycopy(from, minIndex, to, minIndex, copyLength);
		} else {
			int minX = blockX(minIndex);
			int minY = blockY(minIndex);
			int minZ = blockZ(minIndex);

			int maxX = blockX(maxIndex);
			int maxY = blockY(maxIndex);
			int maxZ = blockZ(maxIndex);

			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					for (int x = minX; x <= maxX; x++) {
						int blockIndex = makeBlockIndex(x, y, z);
						to[blockIndex] = from[blockIndex];
					}
				}
			}
		}
	}

	private void copyNibble(byte[] to, byte[] from, int minIndex, int maxIndex) {
		int copyLength = maxIndex - minIndex + 1;

		if (copyLength == 1){
			to[minIndex >>> 1] = from[minIndex >>> 1];
		} else if (copyLength == 16*16*16 || copyLength == 16*16) {
			maxIndex++;
			maxIndex >>>= 1;
			minIndex >>>= 1;

			copyLength = maxIndex - minIndex;
			System.arraycopy(from, minIndex, to, minIndex, copyLength);
		} else {
			int minX = blockX(minIndex) & ~1;
			int minY = blockY(minIndex);
			int minZ = blockZ(minIndex);

			int maxX = blockX(maxIndex);
			int maxY = blockY(maxIndex);
			int maxZ = blockZ(maxIndex);

			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					for (int x = minX; x <= maxX; x += 2) {
						int blockIndex = makeBlockIndex(x, y, z) >>> 1;
						to[blockIndex] = from[blockIndex];
					}
				}
			}
		}
	}

	static int blockX(int index) {
		return index & 0xF;
	}

	static int blockY(int index) {
		return (index >>> 8) & 0xF;
	}

	static int blockZ(int index) {
		return (index >>> 4) & 0xF;
	}

	private int getMinBlockIndex(int sectionIndex) {
		int sectX = sectionX(sectionIndex);
		int sectY = sectionY(sectionIndex);
		int sectZ = sectionZ(sectionIndex);

		int blockX = this.blockX + sectX * 16;
		int blockY = this.blockY + sectY * 16;
		int blockZ = this.blockZ + sectZ * 16;

		int minX = Math.max(blockX, this.blockX + 16 - RADIUS);
		int minY = Math.max(blockY, this.blockY + 16 - RADIUS);
		int minZ = Math.max(blockZ, this.blockZ + 16 - RADIUS);
		return makeBlockIndex(minX & 15, minY & 15, minZ & 15);
	}

	private int getMaxBlockIndex(int sectionIndex) {
		int sectX = sectionX(sectionIndex);
		int sectY = sectionY(sectionIndex);
		int sectZ = sectionZ(sectionIndex);

		int blockX = this.blockX + sectX * 16;
		int blockY = this.blockY + sectY * 16;
		int blockZ = this.blockZ + sectZ * 16;

		int maxX = Math.min(blockX + 15, this.blockX + 16 + 15 + RADIUS);
		int maxY = Math.min(blockY + 15, this.blockY + 16 + 15 + RADIUS);
		int maxZ = Math.min(blockZ + 15, this.blockZ + 16 + 15 + RADIUS);
		return makeBlockIndex(maxX & 15, maxY & 15, maxZ & 15);
	}

	public static int sectionIndex(int x, int y, int z) {
		return x + (z * 3) + (y * 9);
	}

	private static int sectionX(int sectionIndex) {
		return sectionIndex % 3;
	}

	private static int sectionY(int sectionIndex) {
		return (sectionIndex / 9);
	}

	private static int sectionZ(int sectionIndex) {
		return (sectionIndex / 3) % 3;
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

		int blockIdLsb = MathExt.byteToUnsigned(this.sectionBlocks[sectionIndex][blockIndex]);
		int blockIdMsb = getMsbNibble(sectionIndex, blockIndex);

		return blockIdLsb | blockIdMsb << 8;
	}

	public int getBlockId(int sectionIndex, int blockIndex) {
		int blockIdLsb = MathExt.byteToUnsigned(this.sectionBlocks[sectionIndex][blockIndex]);
		int blockIdMsb = getMsbNibble(sectionIndex, blockIndex);

		return blockIdLsb | blockIdMsb << 8;
	}

	private int getMsbNibble(int sectionIndex, int blockIndex) {
		byte[] msbArray = this.sectionBlocksMsb[sectionIndex];
		return msbArray != null ? getNibble(this.sectionBlocksMsb[sectionIndex], blockIndex) : 0;
	}

	private int getMsbNibbleCenter(int blockIndex) {
		return this.centerBlocksMsb != null ? getNibble(this.centerBlocksMsb, blockIndex) : 0;
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
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);
		int blockX = x - this.blockX;
		int blockY = y - this.blockY;
		int blockZ = z - this.blockZ;

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
		int blockIdLsb = MathExt.byteToUnsigned(this.centerBlocks[blockIndex]);
		int blockIdMsb = getMsbNibbleCenter(blockIndex);

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

		return getNibble(this.sectionData[sectionIndex], blockIndex);
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

	@Override
	public BiomeGenBase getBiomeGenForCoords(int x, int z) {
		if (this.uniformBiomes) {
			return this.inlinedBiome;
		}

		int biomeX = x - (this.blockX + 16 - BIOME_RADIUS);
		int biomeZ = z - (this.blockZ + 16 - BIOME_RADIUS);

		return BiomeGenBase.biomeList[this.biomes[biomeX + biomeZ * BIOME_CHUNK_WIDTH] & 0xFF];
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
