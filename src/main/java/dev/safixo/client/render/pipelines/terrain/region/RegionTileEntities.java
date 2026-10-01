package dev.safixo.client.render.pipelines.terrain.region;

import dev.safixo.client.render.pipelines.terrain.SectionSet;
import it.unimi.dsi.fastutil.shorts.Short2ReferenceOpenHashMap;
import net.minecraft.tileentity.TileEntity;

import java.util.Collections;
import java.util.List;

public class RegionTileEntities {
	private final Short2ReferenceOpenHashMap<TileEntity[]> tileEntitiesPerSection = new Short2ReferenceOpenHashMap<>();
	private final int sectionX, sectionY, sectionZ;

	public RegionTileEntities(int regionX, int regionY, int regionZ) {
		this.sectionX = regionX << (RegionConstants.BLOCK_SHIFT_X - 4);
		this.sectionY = regionY << (RegionConstants.BLOCK_SHIFT_Y - 4);
		this.sectionZ = regionZ << (RegionConstants.BLOCK_SHIFT_Z - 4);
	}

	public void addTileEntities(TileEntity[] tileEntities, int regionIndex) {
		this.tileEntitiesPerSection.put((short) regionIndex, tileEntities);
	}

	public void removeTileEntities(int regionIndex) {
		this.tileEntitiesPerSection.remove((short) regionIndex);
	}

	public void iterateTileEntities(SectionSet sectionSet, List<TileEntity> globalList) {
		for (short regionIndex : this.tileEntitiesPerSection.keySet()) {
			int sectionX = this.sectionX + RegionConstants.getLocalSectionX(regionIndex);
			int sectionY = this.sectionY + RegionConstants.getLocalSectionY(regionIndex);
			int sectionZ = this.sectionZ + RegionConstants.getLocalSectionZ(regionIndex);

			if (!sectionSet.isVisible(sectionX, sectionY, sectionZ)) {
				continue;
			}

			TileEntity[] tileEntities = this.tileEntitiesPerSection.get(regionIndex);

			for (int i = 0; i < tileEntities.length; i++) {
				globalList.add(tileEntities[i]);
			}
		}
	}

	public boolean hasTileEntities() {
		return !this.tileEntitiesPerSection.isEmpty();
	}
}
