package dev.safixo.client.render.pipelines.terrain.meshing.data;

import net.minecraft.world.biome.BiomeGenBase;

/**
 * As Forge pipeline is slow for generating events for meshing (I don't know who thought that the
 * system was a good idea), to avoid the slowness of it I use a fake Biome to colorize faster blocks that I don't
 * have a direct managed pipeline, using in advantage chunks with a unique biome and the usage of {@link SectionCache}.
 */
public class FakeInlinedBiome extends BiomeGenBase {
	public int waterColor, foliageColor, grassColor;

	public FakeInlinedBiome(int id) {
		super(id, false);
	}

	public void setColors(int waterColor, int foliageColor, int grassColor) {
		this.waterColor = waterColor;
		this.foliageColor = foliageColor;
		this.grassColor = grassColor;
	}

	@Override
	public int getBiomeGrassColor() {
		return this.grassColor;
	}

	@Override
	public int getBiomeFoliageColor() {
		return this.foliageColor;
	}

	@Override
	public int getWaterColorMultiplier() {
		return this.waterColor;
	}
}
