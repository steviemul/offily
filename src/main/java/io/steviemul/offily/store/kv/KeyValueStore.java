package io.steviemul.offily.store.kv;

import io.steviemul.offily.store.Store;
import io.steviemul.offily.store.StoreException;
import io.steviemul.offily.store.Utils;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KeyValueStore<K, V> implements Store<K, V> {

  private static final String BASE_ROOT_FOLDER = ".cache";
  private final File root;

  private final Map<Integer, Shard<K, V>> shards = new ConcurrentHashMap<>();
  private final Index<K, Integer> keyShardIndex;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  public KeyValueStore(String name) throws StoreException {
    this.root = initRoot(name);
    this.keyShardIndex = new Index<>(this.root, Integer::parseInt);

    loadShards();
  }

  private void loadShards() {
    int shardId = 0;

    File shardDataFile = new File(root, Utils.getStoreFilename(DataFile.DATA_FILE, DataFile.DATA_EXT, shardId));

    while (shardDataFile.exists()) {
      Shard<K, V> shard = new Shard<>(root, shardId);
      shards.put(shardId, shard);

      log.info("Loaded shard [shardId={}]", shardId);

      shardId++;
      shardDataFile = new File(root, Utils.getStoreFilename(DataFile.DATA_FILE, DataFile.DATA_EXT, shardId));
    }

    if (shards.isEmpty()) {
      Shard<K, V> shard = new Shard<>(root, 0);
      shards.put(0, shard);
      log.info("Created new shard [shardId={}]", 0);
    }
  }

  @Override
  public boolean contains(K key) {
    lock.readLock().lock();

    try {
      return getShard(key).map(s -> s.contains(key)).orElse(false);
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public V get(K key) {
    lock.readLock().lock();

    try {
      return getShard(key).map(s -> s.get(key)).orElse(null);
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public V put(K key, V value) throws StoreException {
    lock.writeLock().lock();
    try {
      DataFileRecord record = Utils.getDataFileRecord(key, value);

      Shard<K, V> shard = getWritableShardFor(record);

      shard.put(key, value);
      keyShardIndex.put(key, shard.getId());

      return value;
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public V remove(K key) throws StoreException {
    lock.writeLock().lock();
    try {
      return removeShardKey(key).map(s -> s.remove(key)).orElse(null);
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public void clear() {
    Utils.deleteDirectory(root);

    for (Shard<K, V> shard : shards.values()) {
      shard.clear();
    }
  }

  private Optional<Shard<K, V>> getShard(K key) {
    Integer shardId = keyShardIndex.get(key);

    return shardId != null ? Optional.ofNullable(shards.get(shardId)) : Optional.empty();
  }

  private Optional<Shard<K, V>> removeShardKey(K key) {
    Integer shardId = keyShardIndex.remove(key);

    return shardId != null ? Optional.ofNullable(shards.get(shardId)) : Optional.empty();
  }

  private Shard<K, V> getWritableShardFor(DataFileRecord record) {
    return shards.values().stream()
        .filter(s -> s.hasSpaceFor(record))
        .findFirst()
        .orElseGet(this::createNewShard);
  }

  private Shard<K, V> createNewShard() {
    int newShardId = shards.size();

    Shard<K, V> shard = new Shard<>(this.root, newShardId);

    shards.put(newShardId, shard);
    return shard;
  }

  private File initRoot(String name) {
    File root = new File(BASE_ROOT_FOLDER, name);

    if (root.exists() && root.isFile()) {
      throw new IllegalArgumentException("Invalid root location");
    }

    root.mkdirs();

    log.info("Root initialized [location={}]", root.getAbsolutePath());

    return root;
  }

  public void close() {
    for (Shard<K, V> shard : shards.values()) {
      shard.close();
    }
  }
}
