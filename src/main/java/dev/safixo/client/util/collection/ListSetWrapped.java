package dev.safixo.client.util.collection;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.*;

public class ListSetWrapped<E> extends ArrayList<E> {
	ObjectOpenHashSet<E> hashSet = new ObjectOpenHashSet<>();

	@Override
	public boolean add(E e) {
		return this.hashSet.add(e);
	}

	@Override
	public int size() {
		return this.hashSet.size();
	}

	@Override
	public Iterator<E> iterator() {
		return this.hashSet.iterator();
	}

	@Override
	public boolean isEmpty() {
		return this.hashSet.isEmpty();
	}

	public static ArrayList<?> newArrayList() {
		return new ListSetWrapped<>();
	}

	@Override
	public E get(int index) {
		throw new RuntimeException();
	}

	@Override
	public E remove(int index) {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public void trimToSize() {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public void ensureCapacity(int minCapacity) {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public boolean contains(Object o) {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public int indexOf(Object o) {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public int lastIndexOf(Object o) {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public boolean equals(Object o) {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public void add(int index, E element) {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public E set(int index, E element) {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public Object clone() {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public Object[] toArray() {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public <T> T[] toArray(T[] a) {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public int hashCode() {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public ListIterator<E> listIterator() {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		throw new RuntimeException("Unimplemented!");
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		throw new RuntimeException("Unimplemented!");
	}
}
