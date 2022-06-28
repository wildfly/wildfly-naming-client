/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.naming.client.util;

import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A hashtable that is implemented in terms of a fast concurrent map instead of a slow synchronized hash table.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class FastHashtable<K, V> extends Hashtable<K, V> {
    private static final long serialVersionUID = 85183000443454665L;

    private final ConcurrentHashMap<K, V> backingMap;

    public FastHashtable(final int initialCapacity, final float loadFactor) {
        super(0);
        backingMap = new ConcurrentHashMap<>(initialCapacity, loadFactor);
    }

    public FastHashtable(final int initialCapacity) {
        super(0);
        backingMap = new ConcurrentHashMap<>(initialCapacity);
    }

    public FastHashtable() {
        super(0);
        backingMap = new ConcurrentHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public FastHashtable(final Map<? extends K, ? extends V> t) {
        super(0);
        backingMap = new ConcurrentHashMap<>(t instanceof FastHashtable ? ((FastHashtable<? extends K, ? extends V>)t).backingMap : t);
    }

    public int size() {
        return backingMap.size();
    }

    public boolean isEmpty() {
        return backingMap.isEmpty();
    }

    public V get(final Object key) {
        return backingMap.get(key);
    }

    public boolean containsKey(final Object key) {
        return backingMap.containsKey(key);
    }

    public boolean containsValue(final Object value) {
        return backingMap.containsValue(value);
    }

    public V put(final K key, final V value) {
        return backingMap.put(key, value);
    }

    public void putAll(final Map<? extends K, ? extends V> m) {
        backingMap.putAll(m);
    }

    public V remove(final Object key) {
        return backingMap.remove(key);
    }

    public void clear() {
        backingMap.clear();
    }

    public ConcurrentHashMap.KeySetView<K, V> keySet() {
        return backingMap.keySet();
    }

    public Collection<V> values() {
        return backingMap.values();
    }

    public Set<Map.Entry<K, V>> entrySet() {
        return backingMap.entrySet();
    }

    public int hashCode() {
        return System.identityHashCode(this);
    }

    public String toString() {
        return backingMap.toString();
    }

    public boolean equals(final Object o) {
        return this == o;
    }

    public V putIfAbsent(final K key, final V value) {
        return backingMap.putIfAbsent(key, value);
    }

    public boolean remove(final Object key, final Object value) {
        return backingMap.remove(key, value);
    }

    public boolean replace(final K key, final V oldValue, final V newValue) {
        return backingMap.replace(key, oldValue, newValue);
    }

    public V replace(final K key, final V value) {
        return backingMap.replace(key, value);
    }

    public V getOrDefault(final Object key, final V defaultValue) {
        return backingMap.getOrDefault(key, defaultValue);
    }

    public void forEach(final BiConsumer<? super K, ? super V> action) {
        backingMap.forEach(action);
    }

    public void replaceAll(final BiFunction<? super K, ? super V, ? extends V> function) {
        backingMap.replaceAll(function);
    }

    public V computeIfAbsent(final K key, final Function<? super K, ? extends V> mappingFunction) {
        return backingMap.computeIfAbsent(key, mappingFunction);
    }

    public V computeIfPresent(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return backingMap.computeIfPresent(key, remappingFunction);
    }

    public V compute(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return backingMap.compute(key, remappingFunction);
    }

    public V merge(final K key, final V value, final BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        return backingMap.merge(key, value, remappingFunction);
    }

    public boolean contains(final Object value) {
        return backingMap.contains(value);
    }

    public Enumeration<K> keys() {
        return backingMap.keys();
    }

    public Enumeration<V> elements() {
        return backingMap.elements();
    }

    public FastHashtable<K, V> clone() {
        return new FastHashtable<>(this);
    }

    protected void rehash() {
        // no-op
    }

    public long mappingCount() {
        return backingMap.mappingCount();
    }

    public ConcurrentHashMap.KeySetView<K, V> keySet(final V mappedValue) {
        return backingMap.keySet(mappedValue);
    }

    public static <K, V> FastHashtable<K, V> of(final Hashtable<K, V> other) {
        if (other instanceof FastHashtable) {
            return (FastHashtable<K, V>) other;
        } else {
            return new FastHashtable<>(other);
        }
    }
}
