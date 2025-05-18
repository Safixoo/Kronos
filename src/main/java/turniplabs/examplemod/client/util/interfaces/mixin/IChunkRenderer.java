package turniplabs.examplemod.client.util.interfaces.mixin;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.minecraft.client.render.terrain.ChunkRenderer;
import turniplabs.examplemod.client.renderer.gl.GlVertexBuffer;

public interface IChunkRenderer {
	GlVertexBuffer solidBuffer();
	GlVertexBuffer translucentBuffer();

	int solidVertices();
	int translucentVertices();

	void setAdjacentNeighbor(IChunkRenderer render, int direction);
	IChunkRenderer getAdjacent(int direction);

	ChunkRenderer getRender();

	int getFrame();
	void setFrame(int frame);
	int getSolidFaces();

	boolean solidSection();
	boolean emptySection();

	Long2ReferenceMap<ChunkRenderer> chunkMap();
}
