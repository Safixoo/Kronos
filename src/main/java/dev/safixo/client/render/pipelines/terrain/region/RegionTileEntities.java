package dev.safixo.client.render.pipelines.terrain.region;

import it.unimi.dsi.fastutil.shorts.Short2ReferenceOpenHashMap;
import net.minecraft.tileentity.TileEntity;

import java.util.Collections;
import java.util.List;

public class RegionTileEntities {
	private final Short2ReferenceOpenHashMap<TileEntity[]> tileEntitiesPerSection = new Short2ReferenceOpenHashMap<>();

	public void addTileEntities(TileEntity[] tileEntities, int regionIndex) {
		this.tileEntitiesPerSection.put((short) regionIndex, tileEntities);
	}

	public void removeTileEntities(int regionIndex) {
		this.tileEntitiesPerSection.remove((short) regionIndex);
	}

	public void iterateTileEntities(List<TileEntity> globalList) {
		for (TileEntity[] tileEntities : this.tileEntitiesPerSection.values()) {
			Collections.addAll(globalList, tileEntities);
		}
	}

	public boolean hasTileEntities() {
		return !this.tileEntitiesPerSection.isEmpty();
	}
}
