package dev.safixo.core.hooks;

import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.event.terraingen.BiomeEvent;

// Avoid allocations and big
public class BiomeGenBaseHook {
	public static int getWaterColorMultiplier(BiomeGenBase biome) {
//		BiomeEvent.GetWaterColor event = BIOME_CONTAINER.get().waterColor;
//
//		HookUtils.setField(event, "biome", "biome", biome);
//		HookUtils.setField(event, "originalColor", "originalColor", biome.waterColorMultiplier);
//		try {
//			biomeEvent.set(event, biome);
//			originalColorEvent.set(event, biome.waterColorMultiplier);
//		} catch (IllegalAccessException e) {
//			throw new RuntimeException(e);
//		}

//		MinecraftForge.EVENT_BUS.post(event);
		return biome.waterColorMultiplier;
	}

	public static int getModdedBiomeGrassColor(BiomeGenBase biome, int original) {
//		BiomeEvent.GetGrassColor event = BIOME_CONTAINER.get().grassColor;
//		HookUtils.setField(event, "biome", "biome", biome);
//		HookUtils.setField(event, "originalColor", "originalColor", original);
//
//		MinecraftForge.EVENT_BUS.post(event);
		return original;
	}

	public static int getModdedBiomeFoliageColor(BiomeGenBase biome, int original) {
//		BiomeEvent.GetFoliageColor event = BIOME_CONTAINER.get().foliageColor;
//		HookUtils.setField(event, "biome", "biome", biome);
//		HookUtils.setField(event, "originalColor", "originalColor", original);
//
//		MinecraftForge.EVENT_BUS.post(event);
		return original;
	}

	private static class BiomeEventLocal extends ThreadLocal<BiomeEventContainer> {
		@Override
		public BiomeEventContainer initialValue() {
			BiomeEventContainer biomeEvents = new BiomeEventContainer();

			biomeEvents.foliageColor = new BiomeEvent.GetFoliageColor(null, 0);
			biomeEvents.grassColor = new BiomeEvent.GetGrassColor(null, 0);
			biomeEvents.waterColor = new BiomeEvent.GetWaterColor(null, 0);

			return biomeEvents;
		}
	}

	private static class BiomeEventContainer {
		protected BiomeEvent.GetWaterColor waterColor;
		protected BiomeEvent.GetFoliageColor foliageColor;
		protected BiomeEvent.GetGrassColor grassColor;
	}
}
