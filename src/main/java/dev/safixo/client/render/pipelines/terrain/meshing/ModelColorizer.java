package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.util.MathExt;
import dev.safixo.client.util.data.PrimitivesFlags;
import net.minecraft.block.Block;
import net.minecraft.world.ColorizerFoliage;
import net.minecraft.world.ColorizerGrass;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.BiomeEvent;

public class ModelColorizer {
	public static final int GRASS_COLOR = 1;
	public static final int LEAVES_COLOR = 2;
	public static final int WATER_COLOR = 3;
	public static final int DEFAULT_COLOR = 4;

	private BiomeEvent.GetGrassColor grassEvent;
	private BiomeEvent.GetWaterColor waterEvent;
	private BiomeEvent.GetFoliageColor foliageEvent;

	public int getColor(SectionCache cache, int x, int y, int z, Block block) {
		int colorizeType = PrimitivesFlags.COLOR_MODULATOR[block.blockID];

		switch (colorizeType) {
			case DEFAULT_COLOR: return 0xFF_FF_FF_FF;
			case GRASS_COLOR: return this.getBlockGrassColor(cache, x, y, z);
			case LEAVES_COLOR: return this.getBlockLeavesColor(cache, x, y, z);
			case WATER_COLOR: return this.getBlockWaterColor(cache, x, y, z);
			default: return block.colorMultiplier(cache, x, y, z); // slow path.
		}
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

		for (int relZ = -1; relZ <= 1; relZ++) {
			for (int relX = -1; relX <= 1; relX++) {
				BiomeGenBase biome = cache.getBiomeGenForCoords(x + relX, z + relZ);
				int color = this.getBiomeFoliageColor(biome);

				b += (color & 0xFF0000) >>> 16;
				g += (color & 0x00FF00) >>> 8;
				r += (color & 0x0000FF) >>> 0;
			}
		}

		// presumably jit-optimized into a couple of multiplications and shifts.
		r /= 9;
		g /= 9;
		b /= 9;
		return (b & 0xFF) << 16 | (g & 0xFF) << 8 | (r & 0xFF);
	}

	public int getBlockGrassColor(SectionCache cache, int x, int y, int z) {
		int b = 0;
		int g = 0;
		int r = 0;

		for (int relZ = -1; relZ <= 1; relZ++) {
			for (int relX = -1; relX <= 1; relX++) {
				BiomeGenBase biome = cache.getBiomeGenForCoords(x + relX, z + relZ);
				int color = this.getBiomeGrassColor(biome);

				b += (color & 0xFF0000) >>> 16;
				g += (color & 0x00FF00) >>> 8;
				r += (color & 0x0000FF) >>> 0;
			}
		}

		r /= 9;
		g /= 9;
		b /= 9;
		return (b & 0xFF) << 16 | (g & 0xFF) << 8 | (r & 0xFF);
	}

	public int getBlockWaterColor(SectionCache cache, int x, int y, int z) {
		int b = 0;
		int g = 0;
		int r = 0;

		for (int relZ = -1; relZ <= 1; relZ++) {
			for (int relX = -1; relX <= 1; relX++) {
				BiomeGenBase biome = cache.getBiomeGenForCoords(x + relX, z + relZ);
				int color = this.getWaterColorEvent(biome);

				b += (color & 0xFF0000) >>> 16;
				g += (color & 0x00FF00) >>> 8;
				r += (color & 0x0000FF) >>> 0;
			}
		}

		r /= 9;
		g /= 9;
		b /= 9;
		return (b & 0xFF) << 16 | (g & 0xFF) << 8 | (r & 0xFF);
	}

	private int getBiomeGrassColor(BiomeGenBase biome) {
		double temp = MathExt.clamp(biome.getFloatTemperature(), 0.0F, 1.0F);
		double rainFall = MathExt.clamp(biome.getFloatRainfall(), 0.0F, 1.0F);

		return getGrassColorEvent(biome, ColorizerGrass.getGrassColor(temp, rainFall));
	}

	private int getBiomeFoliageColor(BiomeGenBase biome) {
		double temp = MathExt.clamp(biome.getFloatTemperature(), 0.0F, 1.0F);
		double rainFall = MathExt.clamp(biome.getFloatRainfall(), 0.0F, 1.0F);

		return getFoliageColorEvent(biome, ColorizerFoliage.getFoliageColor(temp, rainFall));
	}

	private int getWaterColorEvent(BiomeGenBase biome) {
		BiomeEvent.GetWaterColor event;

		if (this.foliageEvent == null || this.foliageEvent.biome != biome) {
			event = this.waterEvent = new BiomeEvent.GetWaterColor(biome, biome.waterColorMultiplier);
		} else {
			event = this.waterEvent;
		}

		MinecraftForge.EVENT_BUS.post(event);
		return event.newColor;
	}

	private int getGrassColorEvent(BiomeGenBase biome, int original) {
		BiomeEvent.GetGrassColor event;

		if (this.foliageEvent == null || this.foliageEvent.biome != biome || this.foliageEvent.originalColor != original) {
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
