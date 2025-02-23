package io.steviemul.offily.event;

import java.util.Map;

public interface LRUMapListener<K, V> {

  void objectEvicted(Map.Entry<K, V> entry);
}
