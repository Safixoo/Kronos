package dev.safixo.client.render.pipelines.terrain.cull;

import dev.safixo.client.render.pipelines.terrain.SectionRender;

import java.util.Arrays;

public class BFSQueue {
	public SectionRender[] sectionRenders;

	public int capacity;
	public int bfsIndex;

	public BFSQueue(int size) {
		this.sectionRenders = new SectionRender[size];
		this.capacity = size;
	}

	public BFSQueue() {
		this(256);
	}

	public void resize() {
		SectionRender[] newArray = new SectionRender[this.capacity *= 2];
		System.arraycopy(this.sectionRenders, 0, newArray, 0, this.bfsIndex);
		this.sectionRenders = newArray;
	}

	public void verifyCapacity(int offset) {
		if (this.bfsIndex + offset >= this.capacity) {
			this.resize();
		}
	}

	public void clear() {
		for (int i = 0; i < this.bfsIndex; i++) {
			this.sectionRenders[i] = null;
		}
		this.bfsIndex = 0;
	}

	public SectionRender get(int position) {
		return this.sectionRenders[position];
	}

	public void addSectionToQueue(SectionRender render) {
		this.sectionRenders[this.bfsIndex++] = render;
	}

	public void addToQueue(SectionRender render) {
		if (this.bfsIndex >= this.capacity) {
			this.resize();
		}

		this.sectionRenders[this.bfsIndex++] = render;
	}

	public int size() {
		return this.bfsIndex;
	}
}
