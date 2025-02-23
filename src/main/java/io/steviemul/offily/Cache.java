package io.steviemul.offily;

import io.steviemul.offily.event.LRUMapListener;
import io.steviemul.offily.store.LRUStore;
import io.steviemul.offily.store.NoopStore;
import io.steviemul.offily.store.Store;
import io.steviemul.offily.store.StoreException;
import io.steviemul.offily.store.kv.KeyValueStore;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Cache<K, V> implements LRUMapListener<K, V>, Store<K, V> {

  private final LRUStore<K, V> memoryStore;
  private final Store<K, V> backingStore;

  /**
   * Creates an unbounded in memory store with a no op backing store.
   * This is basically the same as just using a Map<K,V>
   */
  public Cache() {
    this.memoryStore = new LRUStore<>();
    this.backingStore = new NoopStore<>();
  }

  /**
   * Creates a limited in memory store with a disk backing store.
   *
   * @param maxMemoryObjects the number of objects that can reside in memory.
   * @param name             the name of the disk backing store.
   */
  public Cache(int maxMemoryObjects, String name) {
    memoryStore = new LRUStore<>(maxMemoryObjects);
    memoryStore.addEventListener(this);

    log.info("Memory store initialized with capacity [capacity={}]", maxMemoryObjects);

    backingStore = new KeyValueStore<>(name);

    log.info("Backing store initialized with name [name={}]", name);
  }

  @Override
  public boolean contains(K key) throws StoreException {
    return memoryStore.containsKey(key) || backingStore.contains(key);
  }

  @Override
  public V get(K key) throws StoreException {
    if (memoryStore.containsKey(key)) {
      log.info("Memory store hit [key={}]", key);

      return memoryStore.get(key);
    }

    V value = backingStore.remove(key);

    if (value != null) {
      log.info("Backing store hit [key={}]", key);

      memoryStore.put(key, value);
    }

    return value;
  }

  @Override
  public V put(K key, V value) {
    return memoryStore.put(key, value);
  }

  @Override
  public V remove(K key) throws StoreException {
    return memoryStore.remove(key);
  }

  @Override
  public void clear() throws StoreException {
    memoryStore.clear();
    backingStore.clear();
  }

  @Override
  public void close() {
    backingStore.close();
  }
  
  @Override
  public void objectEvicted(Map.Entry<K, V> entry) {
    try {
      backingStore.put(entry.getKey(), entry.getValue());

      log.info(
          "Object evicted from memory store and persisted to backing store [key={}]",
          entry.getKey());
    } catch (StoreException e) {
      log.error("Error persisting object to backing store", e);
    }
  }
}
