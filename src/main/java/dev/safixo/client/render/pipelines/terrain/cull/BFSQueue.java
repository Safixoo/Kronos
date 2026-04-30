package dev.safixo.client.render.pipelines.terrain.cull;

import it.unimi.dsi.fastutil.longs.LongArrayList;

public class BFSQueue {
	public LongArrayList renderList = new LongArrayList(512);
	public int[] indices;

	public int capacity;
	public int bfsIndex;

	public BFSQueue(int size) {
		this.indices = new int[size];
		this.capacity = size;
	}

	public BFSQueue() {
		this(256);
	}

	public void resize() {
		int[] newArray = new int[this.capacity *= 2];
		System.arraycopy(this.indices, 0, newArray, 0, this.bfsIndex);
		this.indices = newArray;
	}

	public void verifyCapacity(int offset) {
		if (this.bfsIndex + offset >= this.capacity) {
			this.resize();
		}
	}

	public void addToRenderList(long position) {
		this.renderList.add(position);
	}

	public void clear() {
		this.bfsIndex = 0;
		this.renderList.clear();
	}

	public int get(int position) {
		return this.indices[position];
	}

	public int size() {
		return this.bfsIndex;
	}
}
