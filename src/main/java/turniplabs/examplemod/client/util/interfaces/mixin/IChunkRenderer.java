package turniplabs.examplemod.client.util.interfaces.mixin;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.minecraft.client.render.terrain.ChunkRenderer;
import turniplabs.examplemod.client.render.gl.GlVertexBuffer;

public interface IChunkRenderer {
	GlVertexBuffer solidBuffer();
	GlVertexBuffer translucentBuffer();

	int solidVertices();
	int translucentVertices();

	void setAdjacentNeighbor(IChunkRenderer render, int direction);
	IChunkRenderer getAdjacent(int direction);

	ChunkRenderer getRender();

	void setFrame(int frame);
	int getFrame();
	int getSolidFaces();

	int getAdjacentMask();

	boolean solidSection();
	boolean emptySection();
	boolean isDirty();
	void setDirty(boolean dirty);
	void queueRebuild();

	void connectNeighbors();
	void disconnectNeighbors();


	Long2ReferenceMap<ChunkRenderer> chunkMap();
}
