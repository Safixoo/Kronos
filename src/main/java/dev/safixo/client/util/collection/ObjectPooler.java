package dev.safixo.client.util.collection;

import java.util.LinkedList;

public class ObjectPooler<T> {
	private final LinkedList<T> references = new LinkedList<>();
	private final ObjectFactory<T> factory;
	private final int maxSize;

	public ObjectPooler(ObjectFactory<T> factory, int maxSize) {
		this.maxSize = maxSize;
		this.factory = factory;
	}

	public T poll() {
		return this.references.isEmpty() ? this.factory.create() : this.references.poll();
	}

	public void push(T reference) {
		if (this.references.size() > this.maxSize) {
			return;
		}
		this.references.push(reference);
	}

	public interface ObjectFactory<T> {
		T create();
	}
}
