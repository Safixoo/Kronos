package dev.safixo.client.render.pipelines.terrain.meshing;

import dev.safixo.client.util.MathExt;
import net.minecraft.world.ColorizerFoliage;
import net.minecraft.world.ColorizerGrass;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;

public class BlockColorSelector {
	public static final int DEFAULT_COLOR = 0;
	public static final int GRASS_COLOR = 1;
	public static final int LEAVES_COLOR = 2;
	public static final int WATER_COLOR = 3;

	public static int leavesColor(SectionCache cache, int x, int y, int z) {
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
				int biome = cache.getBiomeGenForCoords(x + relX, z + relZ).getBiomeFoliageColor();

				b += (biome & 0xFF0000) >>> 16;
				g += (biome & 0x00FF00) >>> 8;
				r += (biome & 0x0000FF) >>> 0;
			}
		}

		r /= 9;
		g /= 9;
		b /= 9;
		return (b & 0xFF) << 16 | (g & 0xFF) << 8 | (r & 0xFF);
	}

	public int grassColor(IBlockAccess cache, int x, int y, int z) {
		int b = 0;
		int g = 0;
		int r = 0;

		for (int relZ = -1; relZ <= 1; relZ++) {
			for (int relX = -1; relX <= 1; relX++) {
				int biome = cache.getBiomeGenForCoords(x + relX, z + relZ).getBiomeGrassColor();

				b += (biome & 0xFF0000) >>> 16;
				g += (biome & 0x00FF00) >>> 8;
				r += (biome & 0x0000FF) >>> 0;
			}
		}

		r /= 9;
		g /= 9;
		b /= 9;
		return (b & 0xFF) << 16 | (g & 0xFF) << 8 | (r & 0xFF);
	}

	public int waterColor(IBlockAccess cache, int x, int y, int z) {
		int b = 0;
		int g = 0;
		int r = 0;

		for (int relZ = -1; relZ <= 1; relZ++) {
			for (int relX = -1; relX <= 1; relX++) {
				int biome = cache.getBiomeGenForCoords(x + relX, z + relZ).getWaterColorMultiplier();

				b += (biome & 0xFF0000) >>> 16;
				g += (biome & 0x00FF00) >>> 8;
				r += (biome & 0x0000FF) >>> 0;
			}
		}

		r /= 9;
		g /= 9;
		b /= 9;
		return (b & 0xFF) << 16 | (g & 0xFF) << 8 | (r & 0xFF);
	}

	public static int getBiomeGrassColor(BiomeGenBase biome) {
		double d0 = MathExt.clamp(biome.getFloatTemperature(), 0.0F, 1.0F);
		double d1 = MathExt.clamp(biome.getFloatRainfall(), 0.0F, 1.0F);
		return biome.getModdedBiomeGrassColor(ColorizerGrass.getGrassColor(d0, d1));
	}

	public static int getBiomeFoliageColor(BiomeGenBase biome) {
		double d0 = MathExt.clamp(biome.getFloatTemperature(), 0.0F, 1.0F);
		double d1 = MathExt.clamp(biome.getFloatRainfall(), 0.0F, 1.0F);
		return biome.getModdedBiomeFoliageColor(ColorizerFoliage.getFoliageColor(d0, d1));
	}
}
