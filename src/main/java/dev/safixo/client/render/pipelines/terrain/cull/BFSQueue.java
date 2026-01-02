package dev.safixo.client.render.pipelines.terrain.cull;

import dev.safixo.client.render.pipelines.terrain.SectionRender;
import dev.safixo.client.render.pipelines.terrain.region.RegionRender;

import java.util.Arrays;

public class BFSQueue {
	public SectionRender[] sectionRenders;
	public RegionRender[] regionRenders;

	public int regionPos;
	public int capacity;
	public int position;

	public BFSQueue(int size) {
		this.sectionRenders = new SectionRender[size];
		this.capacity = size;
	}

	public BFSQueue() {
		this(256);
	}

	public void resize() {
		SectionRender[] newArray = new SectionRender[this.capacity *= 2];
		System.arraycopy(this.sectionRenders, 0, newArray, 0, this.position);
		this.sectionRenders = newArray;
	}

	public void verifyCapacity(int offset) {
		if (this.position + offset >= this.capacity) {
			this.resize();
		}
	}

	public void prepareRegionArr(int renderDistance) {
		int region8 = ((renderDistance * 2 + 1) >> 3);
		int region4 = (renderDistance >> 2);

		this.regionRenders = new RegionRender[region8 * region4 * region8 + 16];
		this.regionPos = 0;
	}

	public void clear() {
		Arrays.fill(this.sectionRenders, null);
		this.position = 0;
	}

	public SectionRender get(int position) {
		return this.sectionRenders[position];
	}

	public void addToQueueUnsafe(SectionRender render) {
		this.sectionRenders[this.position++] = render;
	}

	public void addToQueue(SectionRender render) {
		if (this.position >= this.capacity) {
			this.resize();
		}

		this.sectionRenders[this.position++] = render;
	}

	public int size() {
		return this.position;
	}
}
