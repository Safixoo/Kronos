package dev.safixo.client.render.pipelines.terrain.meshing.model;

import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.PrimitivesFlags;
import dev.safixo.client.util.memory.UnsafeUtil;
import net.minecraft.block.Block;
import net.minecraft.world.ColorizerFoliage;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.BiomeEvent;

import java.lang.reflect.Field;

public class ModelColorizer {
	public static final int DYNAMIC_COLOR = 0;
	public static final int GRASS_COLOR = 1;
	public static final int FOLIAGE_COLOR = 2;
	public static final int WATER_COLOR = 3;
	public static final int DEFAULT_COLOR = 4;

	public static final int MAX_COLOR_TYPES = 5;

	private static final int SAMPLE_SIZE = MathExt.square(SectionCache.BIOME_RADIUS * 2 + 1);

	private final BiomeEvent.GetGrassColor grassEvent = new BiomeEvent.GetGrassColor(null, 0);
	private final BiomeEvent.GetWaterColor waterEvent = new BiomeEvent.GetWaterColor(null, 0);
	private final BiomeEvent.GetFoliageColor foliageEvent = new BiomeEvent.GetFoliageColor(null, 0);

	static final long ORIGINAL_COLOR_OFF;

	static {
		try {
			Field originalColor = BiomeEvent.BiomeColor.class.getDeclaredField("originalColor");
			ORIGINAL_COLOR_OFF = UnsafeUtil.getFieldOffset(originalColor);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void populateEvent(BiomeEvent event, BiomeGenBase biome, int color) {
		event.biome = biome;
		((BiomeEvent.BiomeColor) event).newColor = color;
		UnsafeUtil.UNSAFE.putInt(event, ORIGINAL_COLOR_OFF, color);
	}

	private static boolean isBiomeAreaUniform(SectionCache cache, int x, int y, int z) {
		int firstBiome = cache.getBiomeRaw(x, z);

		for (int relZ = -SectionCache.BIOME_RADIUS; relZ <= SectionCache.BIOME_RADIUS; relZ++) {
			for (int relX = -SectionCache.BIOME_RADIUS; relX <= SectionCache.BIOME_RADIUS; relX++) {
				int biome = cache.getBiomeRaw(x + relX, z + relZ);

				if (biome != firstBiome) {
					return false;
				}
			}
		}

		return true;
	}

	public int getColor(SectionCache cache, int x, int y, int z, Block block) {
		int colorizeType = PrimitivesFlags.COLOR_MODULATOR[block.blockID];

		if (colorizeType == DEFAULT_COLOR) {
			return 0xFFFFFF;
		}

		if (colorizeType == DYNAMIC_COLOR) {
			return block.colorMultiplier(cache, x, y, z);
		}

		// Check first if the whole section is uniform.
		if (cache.hasUniformBiomes()) {
			return cache.getColorByType(colorizeType);
		}

		// Check if the area of biome radius is uniform.
		if (isBiomeAreaUniform(cache, x, y, z)) {
			BiomeGenBase biome = cache.getBiomeGenForCoords(x, z);

			switch (colorizeType) {
				case GRASS_COLOR: return this.getBiomeGrassColor(biome);
				case FOLIAGE_COLOR:
					int meta = cache.getBlockMetadata(x, y, z);

					if ((meta & 0b11) == 0b01) {
						return ColorizerFoliage.getFoliageColorPine();
					}
					if ((meta & 0b11) == 0b10) {
						return ColorizerFoliage.getFoliageColorBirch();
					}

					return this.getBiomeFoliageColor(biome);
				case WATER_COLOR: return this.getWaterColorEvent(biome);
			}
		}

		switch (colorizeType) {
			case GRASS_COLOR: return this.getBlockGrassColor(cache, x, y, z);
			case FOLIAGE_COLOR: return this.getBlockLeavesColor(cache, x, y, z);
			case WATER_COLOR: return this.getBlockWaterColor(cache, x, y, z);
		}

		throw new RuntimeException("Couldn't tint the block!");
	}

	public int getBlockLeavesColor(SectionCache cache, int x, int y, int z) {
		int meta = cache.getBlockMetadata(x, y, z);

		if ((meta & 0b11) == 0b01) {
			return ColorizerFoliage.getFoliageColorPine();
		}
		if ((meta & 0b11) == 0b10) {
			return ColorizerFoliage.getFoliageColorBirch();
		}

		int b = 0;
		int g = 0;
		int r = 0;

		for (int relZ = -SectionCache.BIOME_RADIUS; relZ <= SectionCache.BIOME_RADIUS; relZ++) {
			for (int relX = -SectionCache.BIOME_RADIUS; relX <= SectionCache.BIOME_RADIUS; relX++) {
				BiomeGenBase biome = cache.getBiomeGenForCoords(x + relX, z + relZ);
				int color = this.getBiomeFoliageColor(biome);

				b += (color & 0xFF0000);
				g += (color & 0x00FF00);
				r += (color & 0x0000FF);
			}
		}

		// presumably jit-optimized into a couple of multiplications and shifts.
		r /= SAMPLE_SIZE;
		g /= SAMPLE_SIZE;
		b /= SAMPLE_SIZE;
		return (b & 0xFF0000) | (g & 0x00FF00) | (r & 0x0000FF);
	}

	public int getBlockGrassColor(SectionCache cache, int x, int y, int z) {
		int b = 0;
		int g = 0;
		int r = 0;

		for (int relZ = -SectionCache.BIOME_RADIUS; relZ <= SectionCache.BIOME_RADIUS; relZ++) {
			for (int relX = -SectionCache.BIOME_RADIUS; relX <= SectionCache.BIOME_RADIUS; relX++) {
				BiomeGenBase biome = cache.getBiomeGenForCoords(x + relX, z + relZ);
				int color = this.getBiomeGrassColor(biome);

				b += (color & 0xFF0000);
				g += (color & 0x00FF00);
				r += (color & 0x0000FF);
			}
		}

		r /= SAMPLE_SIZE;
		g /= SAMPLE_SIZE;
		b /= SAMPLE_SIZE;
		return (b & 0xFF0000) | (g & 0x00FF00) | (r & 0x0000FF);
	}

	public int getBlockWaterColor(SectionCache cache, int x, int y, int z) {
		int b = 0;
		int g = 0;
		int r = 0;

		for (int relZ = -SectionCache.BIOME_RADIUS; relZ <= SectionCache.BIOME_RADIUS; relZ++) {
			for (int relX = -SectionCache.BIOME_RADIUS; relX <= SectionCache.BIOME_RADIUS; relX++) {
				BiomeGenBase biome = cache.getBiomeGenForCoords(x + relX, z + relZ);
				int color = this.getWaterColorEvent(biome);

				b += (color & 0xFF0000);
				g += (color & 0x00FF00);
				r += (color & 0x0000FF);
			}
		}

		r /= SAMPLE_SIZE;
		g /= SAMPLE_SIZE;
		b /= SAMPLE_SIZE;
		return (b & 0xFF0000) | (g & 0x00FF00) | (r & 0x0000FF);
	}

	public int getBiomeGrassColor(BiomeGenBase biome) {
		return this.getGrassColorEvent(biome, PrimitivesFlags.GRASS_COLOR[biome.biomeID]);
	}

	public int getBiomeFoliageColor(BiomeGenBase biome) {
		return this.getFoliageColorEvent(biome, PrimitivesFlags.LEAVES_COLOR[biome.biomeID]);
	}

	public int getWaterColorEvent(BiomeGenBase biome) {
		if (biome != this.waterEvent.biome) {
			populateEvent(this.waterEvent, biome, biome.waterColorMultiplier);
			MinecraftForge.EVENT_BUS.post(this.waterEvent);
		}

		return this.waterEvent.newColor;
	}

	private int getGrassColorEvent(BiomeGenBase biome, int original) {
		if (biome != this.grassEvent.biome || original != this.grassEvent.originalColor) {
			populateEvent(this.grassEvent, biome, original);
			MinecraftForge.EVENT_BUS.post(this.grassEvent);
		}

		return this.grassEvent.newColor;
	}

	private int getFoliageColorEvent(BiomeGenBase biome, int original) {
		if (biome != this.foliageEvent.biome || original != this.foliageEvent.originalColor) {
			populateEvent(this.foliageEvent, biome, original);
			MinecraftForge.EVENT_BUS.post(this.foliageEvent);
		}

		return this.foliageEvent.newColor;
	}
}
