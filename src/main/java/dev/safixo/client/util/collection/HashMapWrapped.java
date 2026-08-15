package dev.safixo.client.util.collection;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class HashMapWrapped<K, V> extends HashMap<K, V> {
	Object2ObjectOpenHashMap<K, V> hashMap;

	public HashMapWrapped() {
	}

	public HashMapWrapped(int size) {
		this.hashMap = new Object2ObjectOpenHashMap<>(size);
	}

	public HashMapWrapped(Map map) {
		this.hashMap = new Object2ObjectOpenHashMap<>();
		this.hashMap.putAll(map);
	}

	@Override
	public void clear() {
		if (this.hashMap == null) {
			return;
		}

		this.hashMap.clear();
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		if (this.hashMap == null) {
			return Collections.emptySet();
		}

		return this.hashMap.entrySet();
	}

	@Override
	public V get(Object key) {
		if (this.hashMap == null) {
			return null;
		}

		return this.hashMap.get(key);
	}

	@Override
	public V put(K key, V value) {
		if (this.hashMap == null) {
			this.hashMap = new Object2ObjectOpenHashMap<>();
		}

		return this.hashMap.put(key, value);
	}

	@Override
	public boolean isEmpty() {
		if (this.hashMap == null) {
			return true;
		}

		return this.hashMap.isEmpty();
	}

	@Override
	public int size() {
		if (this.hashMap == null) {
			return 0;
		}

		return this.hashMap.size();
	}

	@Override
	public boolean containsValue(Object value) {
		if (this.hashMap == null) {
			return false;
		}

		return this.hashMap.containsValue(value);
	}

	@Override
	public boolean containsKey(Object key) {
		if (this.hashMap == null) {
			return false;
		}

		return this.hashMap.containsKey(key);
	}

	@Override
	public V remove(Object key) {
		if (this.hashMap == null) {
			return null;
		}

		return this.hashMap.remove(key);
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		if (this.hashMap == null) {
			this.hashMap = new Object2ObjectOpenHashMap<>();
		}

		if (m == null || m.isEmpty()) {
			return;
		}

		this.hashMap.putAll(m);
	}

	@Override
	public boolean equals(Object o) {
		return Objects.equals(this.hashMap, o);
	}

	@Override
	public HashMapWrapped<K, V> clone() {
		if (this.hashMap == null) {
			return new HashMapWrapped<>();
		}

		HashMapWrapped<K, V> clone = new HashMapWrapped<>();
		clone.hashMap.putAll(this.hashMap);
		return clone;
	}

	@Override
	public Collection<V> values() {
		if (this.hashMap == null) {
			return Collections.emptyList();
		}

		return this.hashMap.values();
	}

	@Override
	public String toString() {
		if (this.hashMap == null) {
			return null;
		}

		return this.hashMap.toString();
	}

	@Override
	public int hashCode() {
		if (this.hashMap == null) {
			return 0;
		}

		return this.hashMap.hashCode();
	}

	public @NotNull Set<K> keySet() {
		if (this.hashMap == null) {
			return Collections.emptySet();
		}

		return this.hashMap.keySet();
	}

	public static HashMap<?, ?> newHashMap() {
		return new HashMapWrapped<>();
	}

	public static HashMap<?, ?> newHashMap(Map map) {
		HashMap<?, ?> newMap = new HashMapWrapped<>();
		newMap.putAll(map);

		return newMap;
	}
}
