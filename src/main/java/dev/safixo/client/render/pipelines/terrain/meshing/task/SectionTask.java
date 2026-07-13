package dev.safixo.client.render.pipelines.terrain.meshing.task;

import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.render.pipelines.terrain.meshing.MesherManager;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.MeshDirection;
import dev.safixo.client.util.data.CameraData;

public class SectionTask {
	public SectionCache cache;
	public MesherManager mesherManager;
	public SectionRender section;
	public CameraData camera;

	private final VertexWriter[] writers;

	public SectionTask(VertexWriter[] writers) {
		this.writers = writers;

		for (VertexWriter writer : writers) {
			writer.startDrawing();
		}
	}

	public VertexWriter getSolidWriter(int dir) {
		return this.writers[dir];
	}

	public VertexWriter getTranslucentWriter() {
		return this.getSolidWriter(MeshDirection.COUNT);
	}

	public VertexWriter[] getWriters() {
		return this.writers;
	}
}
