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
	private static final boolean USE_UNIFORM_BIOME = false;

	private final FakeInlinedBiome FAKE_BIOME = new FakeInlinedBiome(Integer.MAX_VALUE);
	// private final ModelColorizer COLORIZER = new ModelColorizer();

	public static final byte[] DEFAULT_BYTE_ARRAY = new byte[16 * 16 * 16];
	public static final byte[] DEFAULT_NIBBLE_ARRAY = new byte[16 * 16 * 8];

	public static final byte[] DEFAULT_FULL_ARRAY = new byte[16 * 16 * 16];
	public static final byte[] DEFAULT_FULL_NIBBLE_ARRAY = new byte[16 * 16 * 8];

	public static final int BIOME_RADIUS = 1;
	private static final int BIOME_CHUNK_WIDTH = 16 + (BIOME_RADIUS * 2);

	static {
		Arrays.fill(DEFAULT_FULL_ARRAY, (byte) 0xFF);
		Arrays.fill(DEFAULT_FULL_NIBBLE_ARRAY, (byte) 0xFF);
	}

	private final Chunk[] chunks = new Chunk[3 * 3];

	private World worldObj;
	public int blockX, blockY, blockZ;
	private boolean uniformBiome;

	private final BiomeGenBase[] biomes = new BiomeGenBase[BIOME_CHUNK_WIDTH * BIOME_CHUNK_WIDTH];

	private final byte[][] sectionBlocks = new byte[3 * 3 * 3][4096];
	private final byte[][] sectionBlocksMsb = new byte[3 * 3 * 3][2048];

	private final byte[][] sectionData = new byte[3 * 3 * 3][2048];

	private final byte[][] skyLight = new byte[3 * 3 * 3][2048];
	private final byte[][] blockLight = new byte[3 * 3 * 3][2048];

	private byte[] centerBlocks;
	private byte[] centerBlocksMsb;

	private byte[] centerSkylight;
	private byte[] centerBlocklight;

	public byte[] visitedSectionBlocks;

	private boolean centerSectEmpty;
	public int[] biomeColors = new int[ModelColorizer.MAX_COLOR_TYPES];

	public SectionCache() {
	}

	public void fillData(World world, int blockX, int blockY, int blockZ) {
		this.worldObj = world;

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

		if (this.centerSectEmpty) {
			return;
		}

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
							if (section.getBlockLSBArray() != null) {
								copy(this.sectionBlocks[sectionIndex], section.getBlockLSBArray(), minIndex, maxIndex);
							}
							if (section.getBlockMSBArray() != null) {
								copy(this.sectionBlocksMsb[sectionIndex], section.getBlockMSBArray().data, minIndex, maxIndex);
							}
							if (section.getMetadataArray() != null) {
								copy(this.sectionData[sectionIndex], section.getMetadataArray().data, minIndex, maxIndex);
							}
						}

						if (section.getSkylightArray() == null || section.getSkylightArray().data == null || this.worldObj.provider.hasNoSky) {
							// Case where dimension doesn't support lighting (nether for example).
							copy(this.skyLight[sectionIndex], DEFAULT_NIBBLE_ARRAY, minIndex, maxIndex);
						} else if (section.getSkylightArray().data != null) {
							// Base case, there is lighting.
							copy(this.skyLight[sectionIndex], section.getSkylightArray().data, minIndex, maxIndex);
						}
						if (section.getBlocklightArray() == null || section.getBlocklightArray().data == null) {
							copy(this.blockLight[sectionIndex], DEFAULT_NIBBLE_ARRAY, minIndex, maxIndex);
						} else {
							copy(this.blockLight[sectionIndex], section.getBlocklightArray().data, minIndex, maxIndex);
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

					Chunk chunk = this.chunks[chunkIndex];
					BiomeGenBase biomeGenBase = BiomeGenBase.plains;

					if (chunk != null) {
						biomeGenBase = chunk.getBiomeGenForWorldCoords(biomeX & 15, biomeZ & 15, manager);
					}

//					if (lastBiome == null) {
//						lastBiome = biomeGenBase;
//					}
//
//					if (lastBiome != biomeGenBase) {
//						uniformed = false;
//					}
//
//					lastBiome = biomeGenBase;

					this.biomes[relBiomeX + relBiomeZ * BIOME_CHUNK_WIDTH] = biomeGenBase;
				}
			}

			if (uniformed && lastBiome != null && USE_UNIFORM_BIOME) {
				int grassColor = lastBiome.getBiomeGrassColor();
				int foliageColor = lastBiome.getBiomeFoliageColor();
				int waterColor = lastBiome.getWaterColorMultiplier();

				this.biomeColors[ModelColorizer.GRASS_COLOR] = grassColor;
				this.biomeColors[ModelColorizer.LEAVES_COLOR] = foliageColor;
				this.biomeColors[ModelColorizer.WATER_COLOR] = waterColor;
				this.biomeColors[ModelColorizer.DEFAULT_COLOR] = 0xFFFFFFFF;

				FAKE_BIOME.setColors(waterColor, foliageColor, grassColor);
				Arrays.fill(this.biomes, FAKE_BIOME);

				this.uniformBiome = uniformed;
			}
		}

		this.centerSkylight = this.skyLight[sectionIndex(1, 1, 1)];
		this.centerBlocklight = this.blockLight[sectionIndex(1, 1, 1)];

		this.centerBlocksMsb = this.sectionBlocksMsb[sectionIndex(1, 1, 1)];
		this.centerBlocks = this.sectionBlocks[sectionIndex(1, 1, 1)];
	}

	private static void copy(byte[] to, byte[] from) {
		if (from.length != to.length) {
			throw new RuntimeException("Incompatible array copy!");
		}

		System.arraycopy(from, 0, to, 0, from.length);
	}

	private static final int RADIUS = 1;

	private void copy(byte[] to, byte[] from, int minIndex, int maxIndex) {
		if (from.length == 2048) {
			minIndex >>= 1;
			maxIndex >>= 1;
		}

		System.arraycopy(from, minIndex, to, minIndex, maxIndex - minIndex + 1);
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

	public boolean isBiomeUniform() {
		return this.uniformBiome;
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
		int biomeX = x - (this.blockX + 16 - BIOME_RADIUS);
		int biomeZ = z - (this.blockZ + 16 - BIOME_RADIUS);

		return this.biomes[biomeX + biomeZ * BIOME_CHUNK_WIDTH];
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

	public void setVisitedArray(byte[] array) {
		this.visitedSectionBlocks = array;
	}
}
