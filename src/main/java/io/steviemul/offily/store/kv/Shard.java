package io.steviemul.offily.store.kv;

import io.steviemul.offily.store.Store;
import io.steviemul.offily.store.StoreException;
import io.steviemul.offily.store.Utils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Shard<K, V> implements Store<K, V> {

  @Getter
  private int id;

  private final DataFile<K> dataFile;
  private final Index<K, Long> index;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final ScheduledExecutorService compactionScheduler =
      Executors.newSingleThreadScheduledExecutor();

  private final WriteAheadLog<K, V> writeAheadLog;

  public Shard(File root, int id) {
    try {
      this.id = id;
      this.index = new Index<>(root, Long::parseLong, id);
      this.dataFile = new DataFile<>(root, this.index, id);
      this.writeAheadLog = new WriteAheadLog<>(root, id);

      recoverFromLog();
      startBackgroundCompaction();
    } catch (IOException e) {
      throw new StoreException("Unable to create key value store", e);
    }
  }

  @Override
  public boolean contains(K key) {
    return index.contains(key);
  }

  @Override
  @SuppressWarnings("unchecked")
  public V get(K key) {
    lock.readLock().lock();

    try {
      Long position = index.get(key);

      if (position == null)
        return null;

      DataFileRecord record = dataFile.get(position);

      return (V) Utils.bytesToObject(record.value());
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public V put(K key, V value) throws StoreException {
    return put(key, value, true);
  }

  public boolean hasSpaceFor(DataFileRecord record) {
    return dataFile.hasSpaceFor(record);
  }

  private V put(K key, V value, boolean addToLog) throws StoreException {
    lock.writeLock().lock();

    try {
      byte[] keyBytes = Utils.objectToBytes(key);
      byte[] valueBytes = Utils.objectToBytes(value);

      DataFileRecord record = new DataFileRecord(keyBytes, valueBytes);

      if (!dataFile.hasSpaceFor(record)) {
        log.error("Data file is full, consider compacting or increasing size");
        return null;
      }

      if (addToLog) {
        writeAheadLog.put(keyBytes, valueBytes);
      }

      long position = dataFile.put(record);

      index.put(key, position);

      printUsageStats();

      return value;
    } catch (IOException e) {
      throw new StoreException("Unable to put object", e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void printUsageStats() {
    double usage = ((double) dataFile.getUsedSize() / (double) dataFile.getMaxSize()) * 100f;

    log.info("Shard statistics [shardId={}, numberOfItems={}, maxSize={}, used={}%]",
        this.getId(),
        index.entrySet().size(),
        dataFile.getMaxSize(),
        String.format("%.2f", usage));
  }

  @Override
  public V remove(K key) throws StoreException {
    lock.writeLock().lock();

    try {
      V value = get(key);

      if (value != null && index.remove(key) != null) {
        writeAheadLog.remove(key, null);
      }

      return value;
    } catch (IOException e) {
      throw new StoreException("Unable to remove object", e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public void clear() {
    index.clear();
  }

  @SuppressWarnings("unchecked")
  private void recoverFromLog() throws IOException, StoreException {

    AtomicInteger puts = new AtomicInteger();
    AtomicInteger removes = new AtomicInteger();

    writeAheadLog.processRecords(
        entry -> {
          String[] record = entry.getRecord();

          K key = (K) Utils.base64StringToObject(record[0]);
          V value = (V) (record.length == 2 ? Utils.base64StringToObject(record[1]) : null);

          if (entry.isPut()) {
            puts.getAndIncrement();
            put(key, value, false);
          } else if (entry.isRemove()) {
            removes.getAndIncrement();
            remove(key);
          }
        });

    log.info("Recovered from log file[puts={}, removes={}]", puts, removes);
  }

  private void startBackgroundCompaction() {

    compactionScheduler.scheduleAtFixedRate(
        () -> {
          try {
            dataFile.compactDataFile();
          } catch (Exception e) {
            log.error("Error during compaction", e);
          }
        },
        60,
        60,
        TimeUnit.SECONDS);

    log.info("Compaction process initialized in background");
  }

  public void close() {
    try {
      dataFile.close();
      writeAheadLog.close();
      index.close();
      compactionScheduler.shutdown();
    } catch (IOException e) {
      log.error("Error releasing resources", e);
    }
  }
}
