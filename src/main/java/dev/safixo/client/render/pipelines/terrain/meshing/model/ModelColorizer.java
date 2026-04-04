package dev.safixo.client.render.pipelines.terrain.meshing.model;

import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.PrimitivesFlags;
import net.minecraft.block.Block;
import net.minecraft.world.ColorizerFoliage;
import net.minecraft.world.ColorizerGrass;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.BiomeGenSwamp;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.BiomeEvent;

public class ModelColorizer {
	public static final int DYNAMIC_COLOR = 0;
	public static final int GRASS_COLOR = 1;
	public static final int LEAVES_COLOR = 2;
	public static final int WATER_COLOR = 3;
	public static final int DEFAULT_COLOR = 4;

	public static final int MAX_COLOR_TYPES = 5;

	private static final int SAMPLE_SIZE = MathExt.square(SectionCache.BIOME_RADIUS * 2 + 1);

	private BiomeEvent.GetGrassColor grassEvent;
	private BiomeEvent.GetWaterColor waterEvent;
	private BiomeEvent.GetFoliageColor foliageEvent;

	public int getColor(SectionCache cache, int x, int y, int z, Block block) {
		int colorizeType = PrimitivesFlags.COLOR_MODULATOR[block.blockID];

		if (cache.isBiomeUniform()) {
			return cache.biomeColors[colorizeType];
		}

		switch (colorizeType) {
			case DEFAULT_COLOR: return 0xFFFFFF;
			case GRASS_COLOR: return this.getBlockGrassColor(cache, x, y, z);
			case LEAVES_COLOR: return this.getBlockLeavesColor(cache, x, y, z);
			case WATER_COLOR: return this.getBlockWaterColor(cache, x, y, z);
		}

		return block.colorMultiplier(cache, x, y, z);
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

	private int getBiomeGrassColor(BiomeGenBase biome) {
		return this.getGrassColorEvent(biome, PrimitivesFlags.GRASS_COLOR[biome.biomeID]);
	}

	private int getBiomeFoliageColor(BiomeGenBase biome) {
		return this.getFoliageColorEvent(biome, PrimitivesFlags.LEAVES_COLOR[biome.biomeID]);
	}

	private int getWaterColorEvent(BiomeGenBase biome) {
		BiomeEvent.GetWaterColor event;

		if (this.waterEvent == null || this.waterEvent.biome != biome) {
			event = this.waterEvent = new BiomeEvent.GetWaterColor(biome, biome.waterColorMultiplier);
		} else {
			event = this.waterEvent;
		}

		MinecraftForge.EVENT_BUS.post(event);
		return event.newColor;
	}

	private int getGrassColorEvent(BiomeGenBase biome, int original) {
		BiomeEvent.GetGrassColor event;

		if (this.grassEvent == null || this.grassEvent.biome != biome || this.grassEvent.originalColor != original) {
			event = this.grassEvent = new BiomeEvent.GetGrassColor(biome, original);
		} else {
			event = this.grassEvent;
		}

		MinecraftForge.EVENT_BUS.post(event);
		return event.newColor;
	}

	private int getFoliageColorEvent(BiomeGenBase biome, int original) {
		BiomeEvent.GetFoliageColor event;

		if (this.foliageEvent == null || this.foliageEvent.biome != biome) {
			event = this.foliageEvent = new BiomeEvent.GetFoliageColor(biome, original);
		} else {
			event = this.foliageEvent;
		}

		MinecraftForge.EVENT_BUS.post(event);
		return event.newColor;
	}
}
