package dev.safixo.client.util.collection;

import java.util.LinkedList;

public class ObjectPooler<T> {
	private final T[] references;
	private final ObjectFactory<T> factory;

	private final int maxSize;
	private int index = -1;

	public ObjectPooler(ObjectFactory<T> factory, int maxSize) {
		this.maxSize = maxSize;
		this.factory = factory;

		this.references = (T[]) new Object[maxSize + 1];
	}

	public T poll() {
		if (this.index == -1) {
			return this.factory.create();
		}

		T object = this.references[this.index];
		this.references[this.index--] = null;
		return object;
	}

	public void push(T reference) {
		if (this.index >= this.maxSize) {
			return;
		}
		this.references[++this.index] = reference;
	}

	public interface ObjectFactory<T> {
		T create();
	}
}
