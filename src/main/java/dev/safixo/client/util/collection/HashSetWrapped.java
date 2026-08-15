package dev.safixo.client.util.collection;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.*;

public class HashSetWrapped<K> extends HashSet<K> {
	ObjectOpenHashSet<K> hashSet;

	public HashSetWrapped() {
	}

	public HashSetWrapped(int size) {
		this.hashSet = new ObjectOpenHashSet<>(size);
	}

	@Override
	public Iterator<K> iterator() {
		if (this.hashSet == null) {
			return Collections.emptyIterator();
		}

		return this.hashSet.iterator();
	}

	@Override
	public Object[] toArray() {
		if (this.hashSet == null) {
			return new Object[0];
		}

		return this.hashSet.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		if (this.hashSet == null) {
			return (T[]) new Object[0];
		}

		return this.hashSet.toArray(a);
	}

	@Override
	public boolean isEmpty() {
		if (this.hashSet == null) {
			return true;
		}

		return this.hashSet.isEmpty();
	}

	@Override
	public int size() {
		if (this.hashSet == null) {
			return 0;
		}

		return this.hashSet.size();
	}

	@Override
	public boolean contains(Object o) {
		if (this.hashSet == null) {
			return true;
		}

		return this.hashSet.contains(o);
	}

	@Override
	public boolean add(K e) {
		if (this.hashSet == null) {
			this.hashSet = new ObjectOpenHashSet<>();
		}

		return this.hashSet.add(e);
	}

	@Override
	public boolean remove(Object o) {
		if (this.hashSet == null) {
			return false;
		}

		return this.hashSet.remove(o);
	}

	@Override
	public void clear() {
		if (this.hashSet == null) {
			return;
		}

		this.hashSet.clear();
	}

	public static HashSet<?> newHashSet() {
		return new HashSetWrapped<>();
	}

	public static HashSet<?> newHashSet(Set map) {
		HashSet<?> newMap = new HashSetWrapped<>();
		newMap.addAll(map);

		return newMap;
	}
}
