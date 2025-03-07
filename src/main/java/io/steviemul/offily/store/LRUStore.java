package io.steviemul.offily.store;

import io.steviemul.offily.event.LRUMapListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Basic key / value store that evicts objects once the number of items
 * has reached capacity. This is determined by looking at the least recently used objects.
 * <p>
 * When an object is evicted, an event is sent to any registered listeners.
 *
 * @param <K> the Key to store
 * @param <V> the Value to store
 */
public class LRUStore<K, V> extends LinkedHashMap<K, V> implements Store<K, V> {

  private static final float LOAD_FACTOR = 0.75f;
  private final int capacity;
  private final List<LRUMapListener<K, V>> eventListeners = new ArrayList<>();

  public LRUStore() {
    this.capacity = Integer.MAX_VALUE;
  }

  public LRUStore(int capacity) {
    super(capacity, LOAD_FACTOR, true);
    this.capacity = capacity;
  }

  public void addEventListener(LRUMapListener<K, V> eventListener) {
    eventListeners.add(eventListener);
  }

  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    boolean remove = (size() > capacity);

    if (remove) {
      for (LRUMapListener<K, V> listener : eventListeners) {
        listener.objectEvicted(eldest);
      }
    }

    return remove;
  }

  @Override
  public boolean contains(K key) {
    return this.containsKey(key);
  }

  @Override
  public void close() {
  }
}
