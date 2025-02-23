package io.steviemul.offily.store;

public interface Store<K, V> {

  boolean contains(K key) throws StoreException;

  V get(K key) throws StoreException;

  V put(K key, V value) throws StoreException;

  V remove(K key) throws StoreException;

  void clear() throws StoreException;

  void close();
}
