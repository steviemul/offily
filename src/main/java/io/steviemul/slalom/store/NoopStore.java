package io.steviemul.slalom.store;

public class NoopStore<K, V> implements Store<K, V> {

  @Override
  public boolean contains(K key) {
    return false;
  }

  @Override
  public V get(K key) {
    return null;
  }

  @Override
  public V put(K key, V value) {
    return null;
  }

  @Override
  public V remove(K key) {
    return null;
  }

  @Override
  public void clear() {}

  @Override
  public void close() {}
}
