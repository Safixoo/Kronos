package dev.safixo.client.render.pipelines.terrain.region;

import dev.safixo.client.render.pipelines.terrain.SectionSet;
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

	public void iterateTileEntities(SectionSet sectionSet, List<TileEntity> globalList) {
		for (TileEntity[] tileEntities : this.tileEntitiesPerSection.values()) {
			for (TileEntity tileEntity : tileEntities) {
				int sectionX = tileEntity.xCoord >> 4;
				int sectionY = tileEntity.yCoord >> 4;
				int sectionZ = tileEntity.zCoord >> 4;

				if (sectionSet.isVisible(sectionX, sectionY, sectionZ)) {
					globalList.add(tileEntity);
				}
			}
		}
	}

	public boolean hasTileEntities() {
		return !this.tileEntitiesPerSection.isEmpty();
	}
}
