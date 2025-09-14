package turniplabs.examplemod.client.render.data;

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
import turniplabs.examplemod.client.util.BlocksFlags;

public class SectionCache implements WorldSource {
	private static final short[] DEFAULT_SHORT_ARRAY = new short[16 * 16 * 16];
	private static final byte[] DEFAULT_BYTE_ARRAY = new byte[16 * 16 * 16];

	private final Chunk[] chunks = new Chunk[3 * 3];
	private final World worldObj;

	private final int sectionX, sectionY, sectionZ;

	private final short[][] sectionBlocks = new short[3 * 3 * 3][];
	private final byte[][] sectionData = new byte[3 * 3 * 3][];

	private final byte[][] lightSky = new byte[3 * 3 * 3][];
	private final byte[][] lightBlock = new byte[3 * 3 * 3][];

	private short[] centerSectBlocks;
	private boolean centerSectEmpty;

	public SectionCache(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		this.worldObj = world;

		this.sectionX = Math.floorDiv(minX, 16);
		this.sectionY = Math.floorDiv(minY, 16);
		this.sectionZ = Math.floorDiv(minZ, 16);

		this.fillData(world, minX, minY, minZ, maxX, maxY, maxZ);
	}

	public void fillData(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		int maxChunkX = Math.floorDiv(maxX, 16);
		int maxChunkZ = Math.floorDiv(maxZ, 16);
		int maxChunkY = Math.floorDiv(maxY, 16);

		for (int x = this.sectionX; x <= maxChunkX; x++) {
			for (int z = this.sectionZ; z <= maxChunkZ; z++) {
				for (int y = this.sectionY; y <= maxChunkY; y++) {
					int relX = x - this.sectionX;
					int relY = y - this.sectionY;
					int relZ = z - this.sectionZ;

					Chunk chunk;

					if (this.chunks[sectionIndex(relX, 0, relZ)] == null) {
						chunk = this.chunks[sectionIndex(relX, 0, relZ)] = world.getChunkFromChunkCoords(x, z);
					} else {
						chunk = this.chunks[sectionIndex(relX, 0, relZ)];
					}

					ChunkSection section = chunk.getSection((minY >> 4) + relY);
					int sectionIndex = sectionIndex(relX, relY, relZ);

					if (section != null) {
						if (section.blocks != null) {
							this.sectionBlocks[sectionIndex] = section.blocks;
						} else {
							this.sectionBlocks[sectionIndex] = DEFAULT_SHORT_ARRAY;
						}

						if (section.data != null) {
							this.sectionData[sectionIndex] = section.data.data;
						} else {
							this.sectionData[sectionIndex] = DEFAULT_BYTE_ARRAY;
						}

						this.lightSky[sectionIndex] = section.skylightMap != null ? section.skylightMap.data : DEFAULT_BYTE_ARRAY;
						this.lightBlock[sectionIndex] = section.blocklightMap != null ? section.blocklightMap.data : DEFAULT_BYTE_ARRAY;;
					} else {
						this.sectionBlocks[sectionIndex] = DEFAULT_SHORT_ARRAY;
						this.lightSky[sectionIndex] = DEFAULT_BYTE_ARRAY;
						this.lightBlock[sectionIndex] = DEFAULT_BYTE_ARRAY;
					}
					this.centerSectBlocks = this.sectionBlocks[sectionIndex(1, 1, 1)];
				}
			}
		}

		int i = 0;

		while (i < 4096 && this.centerSectBlocks[i] == 0) {
			i++;
		}

		this.centerSectEmpty = i == 4096;
	}

	public static int sectionIndex(int x, int y, int z) {
		return x + (z * 3) + (y * 9);
	}

	public boolean isSectionEmpty() {
		return this.centerSectEmpty;
	}

	public int getNibble(byte[] nibbleArray, int blockIndex) {
		int nibbleIndex = blockIndex >> 1;
		int nibblePart = blockIndex & 1;
		return nibbleArray[nibbleIndex] >>> (nibblePart << 2) & 15;
	}

	private static int makeBlockIndex(int x, int y, int z) {
		return y << 8 | z << 4 | x;
	}

	@Override
	public int getBlockId(int x, int y, int z) {
		if (y < 0 || y >= 256) {
			return 0;
		}

		int sectionX = (x >> 4) - this.sectionX;
		int sectionY = (y >> 4) - this.sectionY;
		int sectionZ = (z >> 4) - this.sectionZ;

		int sectionIndex = sectionIndex(sectionX, sectionY, sectionZ);

		if (sectionIndex >= 0 && sectionIndex < 27) {
			return this.sectionBlocks[sectionIndex(sectionX, sectionY, sectionZ)][makeBlockIndex(x & 15, y & 15, z & 15)];
		}

		return 0;
	}

	public int getBlockIdCenter(int x, int y, int z) {
		return this.centerSectBlocks[makeBlockIndex(x & 15, y & 15, z & 15)];
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
		x -= this.sectionX << 4;
		y -= this.sectionY << 4;
		z -= this.sectionZ << 4;

		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);
		int blockId = this.sectionBlocks[sectionIndex(x >> 4, y >> 4, z >> 4)][blockIndex];

		if (BlocksFlags.SOLID[blockId]) {
			return 0;
		}

//		boolean litInterior = BlocksFlags.LIT_INTERIOR[blockId];
//
//		int lighting = !litInterior ?  : 0;
//
//		if (litInterior) {
//			lighting |= this.getLightmapSpecificCoord(x, y + 1, z);
//			lighting |= this.getLightmapSpecificCoord(x + 1, y, z);
//			lighting |= this.getLightmapSpecificCoord(x - 1, y, z);
//			lighting |= this.getLightmapSpecificCoord(x, y, z + 1);
//			lighting |= this.getLightmapSpecificCoord(x, y, z - 1);
//		}

		return this.getLightmapSpecificCoord(x, y, z);
	}

	private int getLightmapSpecificCoord(int x, int y, int z) {
		int sectionX = x >> 4;
		int sectionY = y >> 4;
		int sectionZ = z >> 4;

		int sectionIndex = sectionIndex(sectionX, sectionY, sectionZ);
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		int skyLight = getNibble(this.lightSky[sectionIndex], blockIndex);
		int blockLight = getNibble(this.lightBlock[sectionIndex], blockIndex);

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
		int offX = (x >> 4) - this.sectionX;
		int offY = (y >> 4) - this.sectionY;
		int offZ = (z >> 4) - this.sectionZ;

		int sectionIndex = sectionIndex(offX, offY, offZ);

		// Doesn't solve all issues with section indexing but avoid many ArrayOutOfBounds.
		if (sectionIndex < 0 || sectionIndex >= 27) {
			return 0;
		}

		return this.sectionData[sectionIndex][makeBlockIndex(x & 15, y & 15, z & 15)];
	}

	@Override
	public Material getBlockMaterial(int x, int y, int z) {
		return BlocksFlags.MATERIAL[this.getBlockId(x, y, z)];
	}

	@Override
	public boolean isBlockOpaqueCube(int x, int y, int z) {
		int sectionX = (x >> 4) - this.sectionX;
		int sectionY = (y >> 4) - this.sectionY;
		int sectionZ = (z >> 4) - this.sectionZ;

		int sectionIndex = sectionIndex(sectionX, sectionY, sectionZ);
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		return BlocksFlags.SOLID[this.sectionBlocks[sectionIndex][blockIndex]];
	}

	public boolean isBlockOpaqueCubeCenter(int x, int y, int z) {
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);
		return BlocksFlags.SOLID[this.centerSectBlocks[blockIndex]];
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
