package dev.safixo.client.util;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class HashMapWrapped<K, V> extends HashMap<K, V> {
	Object2ObjectOpenHashMap<K, V> hashMap = new Object2ObjectOpenHashMap<>();

	public V get(Object key) {
		return this.hashMap.get(key);
	}

	public V put(K key, V value) {
		return this.hashMap.put(key, value);
	}

	public boolean isEmpty() {
		return this.hashMap.isEmpty();
	}

	public int size() {
		return this.hashMap.size();
	}

	public boolean containsValue(Object value) {
		return this.hashMap.containsValue(value);
	}

	public boolean containsKey(Object key) {
		return this.hashMap.containsKey(key);
	}

	public V remove(Object key) {
		return this.hashMap.remove(key);
	}

	public void putAll(Map<? extends K, ? extends V> m) {
		this.hashMap.putAll(m);
	}

	public @NotNull Set<K> keySet() {
		return this.hashMap.keySet();
	}

	public static HashMap<?, ?> newHashMap() {
		return new HashMapWrapped<>();
	}
}
