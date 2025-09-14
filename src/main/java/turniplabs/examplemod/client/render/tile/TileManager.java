package turniplabs.examplemod.client.render.tile;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;

public class TileManager {
	public Long2ReferenceMap<TileRender> tileMap = new Long2ReferenceOpenHashMap<>();

	public TileRender getTile(int sectionX, int sectionY, int sectionZ) {
		long position = tilePos(sectionX >> 2, sectionY >> 2, sectionZ >> 2);
		TileRender tile = this.tileMap.get(tilePos(sectionX >> 2, sectionY >> 2, sectionZ >> 2));

		if (tile == null) {
			tile = new TileRender(sectionX >> 2, sectionY >> 2, sectionZ >> 2);
			this.tileMap.put(position, tile);
		}

		return tile;
	}

	private static long tilePos(int tileX, int tileY, int tileZ) {
		long bitsX = tileX & 0xFFFFF;
		long bitsZ = tileZ & 0xFFFFF;
		long bitsY = tileY & 0xFF;

		return bitsX << 0L | bitsZ << 20L | bitsY << 40L;
	}
}
