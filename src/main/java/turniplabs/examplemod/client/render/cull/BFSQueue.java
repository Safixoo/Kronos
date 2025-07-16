package turniplabs.examplemod.client.render.cull;

import turniplabs.examplemod.client.render.SectionRender;

public class BFSQueue {
	private SectionRender[] sectionRenders;
	private int capacity;
	private int position;

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

	public void clear() {
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
