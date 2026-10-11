package dev.safixo.client.render.pipelines.terrain.meshing.task;

import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.render.pipelines.terrain.meshing.MesherManager;
import dev.safixo.client.render.pipelines.terrain.meshing.data.SectionCache;
import dev.safixo.client.render.vertex.VertexWriter;
import dev.safixo.client.util.MeshDirection;
import dev.safixo.client.util.data.CameraData;
import dev.safixo.client.util.memory.MemoryReference;

public class SectionTask {
	public SectionCache cache;
	public MesherManager meshManager;
	public SectionRender section;
	public CameraData camera;

	public TaskType taskType;

	public MemoryReference[] returningRefs;
	public int taskId;

	private final VertexWriter[] writers;

	public SectionTask(VertexWriter[] writers) {
		this.writers = writers;
	}

	public SectionTask(MemoryReference[] returningRefs) {
		this.returningRefs = returningRefs;
		this.taskType = TaskType.MEMORY_REF_RETURNER;
		this.writers = null;
	}

	public VertexWriter getSolidWriter(int dir) {
		if (this.taskType == TaskType.MEMORY_REF_RETURNER) {
			throw new RuntimeException("This type of task doesn't have vertex writing!");
		}

		return this.writers[dir];
	}

	public VertexWriter getTranslucentWriter() {
		if (this.taskType == TaskType.MEMORY_REF_RETURNER) {
			throw new RuntimeException("This type of task doesn't have vertex writing!");
		}

		return this.getSolidWriter(MeshDirection.COUNT);
	}

	public MemoryReference[] getReturningRefs() {
		return this.returningRefs;
	}

	public VertexWriter[] getWriters() {
		return this.writers;
	}

	public enum TaskType {
		IMPORTANT,
		REGULAR,
		MEMORY_REF_RETURNER
	}
}
