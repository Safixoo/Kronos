package dev.safixo.client.render.pipelines.terrain.cull;

public class BFSQueue {
	public long[] renderIndices;
	public int[] graphIndices;

	public int capacity;

	public int bfsIndex;
	public int renderListIndex;

	public BFSQueue(int size) {
		this.graphIndices = new int[size];
		this.renderIndices = new long[size];
		this.capacity = size;
	}

	public BFSQueue() {
		this(256);
	}

	public void resize() {
		int capacity = this.capacity *= 2;

		int[] newIndices = new int[capacity];
		long[] newRender = new long[capacity];

		System.arraycopy(this.graphIndices, 0, newIndices, 0, this.bfsIndex);
		this.graphIndices = newIndices;

		System.arraycopy(this.renderIndices, 0, newRender, 0, this.renderListIndex);
		this.renderIndices = newRender;
	}

	public void verifyCapacity(int offset) {
		if (this.bfsIndex + offset >= this.capacity) {
			this.resize();
		}
	}

	public void addToRenderList(long sectionIndex) {
		this.renderIndices[this.renderListIndex++] = sectionIndex;
	}

	public void clear() {
		this.bfsIndex = 0;
		this.renderListIndex = 0;
	}

	public int size() {
		return this.bfsIndex;
	}
}
