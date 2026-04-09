package dev.safixo.client.render.pipelines.terrain.cull;

import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;

import java.util.Arrays;

public class BFSQueue {
	public SectionRender[] sectionRenders;

	public int capacity;
	public int sectionPos;

	public BFSQueue(int size) {
		this.sectionRenders = new SectionRender[size];
		this.capacity = size;
	}

	public BFSQueue() {
		this(256);
	}

	public void resize() {
		SectionRender[] newArray = new SectionRender[this.capacity *= 2];
		System.arraycopy(this.sectionRenders, 0, newArray, 0, this.sectionPos);
		this.sectionRenders = newArray;
	}

	public void verifyCapacity(int offset) {
		if (this.sectionPos + offset >= this.capacity) {
			this.resize();
		}
	}

	public void clear() {
		Arrays.fill(this.sectionRenders, null);
		this.sectionPos = 0;
	}

	public SectionRender get(int position) {
		return this.sectionRenders[position];
	}

	public void addSectionToQueue(SectionRender render) {
		this.sectionRenders[this.sectionPos++] = render;
	}

	public void addToQueue(SectionRender render) {
		if (this.sectionPos >= this.capacity) {
			this.resize();
		}

		this.sectionRenders[this.sectionPos++] = render;
	}

	public int size() {
		return this.sectionPos;
	}
}
