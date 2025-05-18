package turniplabs.examplemod.mixins;

import net.minecraft.client.render.LightmapHelper;
import net.minecraft.core.world.World;
import net.minecraft.core.world.chunk.Chunk;
import net.minecraft.core.world.chunk.ChunkCache;
import net.minecraft.core.world.chunk.ChunkSection;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import turniplabs.examplemod.client.util.BlocksFlags;

@Mixin(value = ChunkCache.class, remap = false)
public abstract class ChunkCacheMixin {
	@Shadow
	@Final
	private int chunkZ;
	@Shadow
	@Final
	private int chunkX;
	@Unique
	private int sectionY;
	@Shadow
	@Final
	private Chunk[][] chunkArray;

	@Unique
	private static final short[] DEFAULT_AIR_BLOCKS = new short[16 * 16 * 16];
	@Unique
	private static final byte[] DEFAULT_DATA_SECTION = new byte[16 * 16 * 16];

	@Unique
	private byte[] sectionData;
	@Unique
	private final short[][] sectionBlocks = new short[3 * 3 * 3][0];
	@Unique
	private final byte[][] skyLightmap = new byte[3 * 3 * 3][0];
	@Unique
	private final byte[][] blockLightmap = new byte[3 * 3 * 3][0];

	@Inject(method = "<init>", at = @At("TAIL"))
	private void onConstructor(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
		this.sectionY = minY >> 4;

		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				for (int y = 0; y < 3; y++) {
					Chunk chunk = this.chunkArray[x][z];
					ChunkSection section = chunk.getSection((minY >> 4) + y);

					int sectionIndex = sectionIndex(x, y, z);
					if (section != null) {
						if (section.blocks != null) {
							this.sectionBlocks[sectionIndex] = section.blocks;
						} else {
							this.sectionBlocks[sectionIndex] = DEFAULT_AIR_BLOCKS;
						}
						this.skyLightmap[sectionIndex] = section.skylightMap != null ? section.skylightMap.data : DEFAULT_DATA_SECTION;
						this.blockLightmap[sectionIndex] = section.blocklightMap != null ? section.blocklightMap.data : DEFAULT_DATA_SECTION;;
					} else {
						this.sectionBlocks[sectionIndex] = DEFAULT_AIR_BLOCKS;
						this.skyLightmap[sectionIndex] = DEFAULT_DATA_SECTION;
						this.blockLightmap[sectionIndex] = DEFAULT_DATA_SECTION;
					}

				}
			}
		}

		Chunk chunk = this.chunkArray[1][1];
		ChunkSection section = chunk.getSection((minY >> 4) + 1);

		if (section != null) {
			this.sectionData = section.data != null ? section.data.data : DEFAULT_DATA_SECTION;
		}
	}

	@Unique
	private static int sectionIndex(int x, int y, int z) {
		return x + (z * 3) + (y * 9);
	}

	/**
	 * @author Safixo
	 * @reason Faster process
	 */
	@Overwrite
	public int getBlockId(int x, int y, int z) {
		if (y < 0 || y >= 256) {
			return 0;
		}

		int sectionX = (x >> 4) - this.chunkX;
		int sectionY = (y >> 4) - this.sectionY;
		int sectionZ = (z >> 4) - this.chunkZ;

		int sectionIndex = sectionIndex(sectionX, sectionY, sectionZ);

		if (sectionIndex < 27) {
			return this.sectionBlocks[sectionIndex(sectionX, sectionY, sectionZ)][makeBlockIndex(x & 15, y & 15, z & 15)];
		}

		return 0;
	}

	/**
	 * @author Safixo
	 * @reason Much faster lighting-pulling.
	 */
	@Overwrite
	public int getLightmapCoord(int x, int y, int z, int blockLightValue) {
		x -= this.chunkX << 4;
		y -= this.sectionY << 4;
		z -= this.chunkZ << 4;

		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);
		int blockId = this.sectionBlocks[sectionIndex(x >> 4, y >> 4, z >> 4)][blockIndex];

		if (BlocksFlags.SOLID[blockId]) {
			return 0;
		}

		int lighting = this.getLightmapSpecificCoord(x, y, z);

		if (BlocksFlags.LIT_INTERIOR[blockId]) {
			lighting |= this.getLightmapSpecificCoord(x, y + 1, z);
			lighting |= this.getLightmapSpecificCoord(x, y - 1, z);
			lighting |= this.getLightmapSpecificCoord(x + 1, y, z);
			lighting |= this.getLightmapSpecificCoord(x - 1, y, z);
			lighting |= this.getLightmapSpecificCoord(x, y, z + 1);
			lighting |= this.getLightmapSpecificCoord(x, y, z - 1);
		}

		return lighting;
	}

	@Unique
	public int getNibble(byte[] nibbleArray, int blockIndex) {
		int nibbleIndex = blockIndex >> 1;
		int nibblePart = blockIndex & 1;
		return nibbleArray[nibbleIndex] >>> (nibblePart << 2) & 15;
	}

	@Unique
	private int getLightmapSpecificCoord(int x, int y, int z) {
		int sectionX = x >> 4;
		int sectionY = y >> 4;
		int sectionZ = z >> 4;

		int sectionIndex = sectionIndex(sectionX, sectionY, sectionZ);
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		int skyLight = getNibble(this.skyLightmap[sectionIndex], blockIndex);
		int blockLight = getNibble(this.blockLightmap[sectionIndex], blockIndex);

		return LightmapHelper.getLightmapCoord(skyLight, blockLight);
	}

	/**
	 * @author Safixo
	 * @reason Faster block metadata pulling.
	 */
	@Overwrite
	public int getBlockMetadata(int x, int y, int z) {
		return this.sectionData[makeBlockIndex(x & 15, y & 15, z & 15)];
	}

	@Unique
	private static int makeBlockIndex(int x, int y, int z) {
		return y << 8 | z << 4 | x;
	}

	/**
	 * @author Safixo
	 * @reason Faster property pulling.
	 */
	@Overwrite
	public boolean isBlockOpaqueCube(int x, int y, int z) {
		int sectionX = (x >> 4) - this.chunkX;
		int sectionY = (y >> 4) - this.sectionY;
		int sectionZ = (z >> 4) - this.chunkZ;

		int sectionIndex = sectionIndex(sectionX, sectionY, sectionZ);
		int blockIndex = makeBlockIndex(x & 15, y & 15, z & 15);

		return BlocksFlags.SOLID[this.sectionBlocks[sectionIndex][blockIndex]];
	}
}
