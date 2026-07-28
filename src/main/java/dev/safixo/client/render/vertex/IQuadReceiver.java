package dev.safixo.client.render.vertex;

import dev.safixo.client.render.pipelines.terrain.meshing.data.Quad;

public interface IQuadReceiver {
	int writeQuad(Quad quad, long ptr);
	int getStride();
}
