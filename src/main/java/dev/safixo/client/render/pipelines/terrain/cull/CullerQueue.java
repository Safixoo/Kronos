package dev.safixo.client.render.pipelines.terrain.cull;

import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.render.pipelines.terrain.SectionSet;
import dev.safixo.client.render.pipelines.terrain.WorldManager;
import dev.safixo.client.util.MathExt;

public class CullerQueue {
	private int[] updateQueue, renderIndices, graphIndices;
	private int bfsIndex, renderIndex, dirtyIndex;

	public CullerQueue(int maxRenderDistance) {
		this.resize(maxRenderDistance);
	}

	public void resize(int renderDistance) {
		int size = MathExt.square(renderDistance * 2 + 1) * 16;

		if (this.renderIndices == null || size > this.renderIndices.length) {
			this.updateQueue = new int[WorldManager.MAX_UPDATES_TRIES];

			this.renderIndices = new int[size];
			this.graphIndices = new int[size];
		}
	}

	public void clear() {
		this.bfsIndex = 0;
		this.renderIndex = 0;
		this.dirtyIndex = 0;
	}

	public void addToRenderList(int sectionIndex) {
		this.renderIndices[this.renderIndex++] = sectionIndex;
	}

	public void addToRebuildList(int sectionIndex) {
		if (this.dirtyIndex >= WorldManager.MAX_UPDATES_TRIES) {
			return;
		}

		this.updateQueue[this.dirtyIndex++] = sectionIndex;
	}

	public SectionRender getDirtySection(WorldManager worldManager, SectionSet sectionSet, int index) {
		return sectionSet.getSectionInstance(worldManager, this.updateQueue[index]);
	}

	public int getRenderId(int index) {
		return this.renderIndices[index];
	}

	public int getRebuildIndex() {
		return this.dirtyIndex;
	}

	public int getGraphIndex() {
		return this.bfsIndex;
	}

	public int getRenderIndex() {
		return this.renderIndex;
	}

	public void setGraphIndex(int bfsIndex) {
		this.bfsIndex = bfsIndex;
	}

	public int[] getGraphQueue() {
		return this.graphIndices;
	}
}
