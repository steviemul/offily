package io.steviemul.offily.store.kv;

import static io.steviemul.offily.store.Utils.getStoreFilename;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataFile<K> {

  private static final String RW_MODE = "rw";

  public static final String DATA_FILE = "data";
  public static final String DATA_EXT = "db";
  private static final String TEMP_DATA_FILE = "temp_data";

  private static final int MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final RandomAccessFile dataFile;
  private final FileChannel dataChannel;
  private final Index<K, Long> index;
  private final File root;
  private final String dataFilename;
  private final String tempFilename;

  private MappedByteBuffer dataBuffer;

  public DataFile(File root, Index<K, Long> index, Integer identifier) throws IOException {
    this.root = root;

    this.dataFilename = getStoreFilename(DATA_FILE, DATA_EXT, identifier);
    this.tempFilename = getStoreFilename(TEMP_DATA_FILE, DATA_EXT, identifier);

    this.dataFile = new RandomAccessFile(new File(root, this.dataFilename), RW_MODE);

    log.info("Data file initialized [location={}]", this.dataFilename);

    this.dataChannel = dataFile.getChannel();
    this.dataBuffer = dataChannel.map(FileChannel.MapMode.READ_WRITE, 0, MAX_FILE_SIZE);

    this.index = index;
  }

  public DataFileRecord get(Long position) {

    lock.readLock().lock();

    try {
      int savedPosition = dataBuffer.position();

      dataBuffer.position(position.intValue());

      byte[] keyBytes = getCurrentBufferValue();
      byte[] valueBytes = getCurrentBufferValue();

      dataBuffer.position(savedPosition);

      return new DataFileRecord(keyBytes, valueBytes);
    } finally {
      lock.readLock().unlock();
    }
  }

  public Long put(DataFileRecord record) {

    lock.writeLock().lock();

    try {
      long position = dataBuffer.position();

      if (!hasSpaceFor(record)) {
        throw new IllegalArgumentException(
            "Not enough space for record, use hasSpaceFor prior to put");
      }

      putInBuffer(record.key());
      putInBuffer(record.value());

      return position;
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void compactDataFile() throws IOException {
    lock.writeLock().lock();

    try {
      File tempFile = new File(root, this.tempFilename);

      try (RandomAccessFile tempRaf = new RandomAccessFile(tempFile, RW_MODE)) {
        FileChannel tempChannel = tempRaf.getChannel();

        MappedByteBuffer newBuffer =
            tempChannel.map(FileChannel.MapMode.READ_WRITE, 0, MAX_FILE_SIZE);
        Map<K, Long> newIndex = new HashMap<>();

        long newPosition = 0;

        for (Map.Entry<K, Long> entry : index.entrySet()) {
          dataBuffer.position(entry.getValue().intValue());

          byte[] keyBytes = getCurrentBufferValue();
          byte[] valueBytes = getCurrentBufferValue();

          newBuffer.position((int) newPosition);

          putInBuffer(keyBytes);
          putInBuffer(valueBytes);

          newIndex.put(entry.getKey(), newPosition);
          newPosition = newBuffer.position();
        }

        dataChannel.close();
        tempChannel.close();
        Files.deleteIfExists(new File(root, this.dataFilename).toPath());
        tempFile.renameTo(new File(root, this.dataFilename));

        this.dataBuffer = dataChannel.map(FileChannel.MapMode.READ_WRITE, 0, MAX_FILE_SIZE);

        index.refreshWith(newIndex);
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  public boolean hasSpaceFor(DataFileRecord record) {

    long position = dataBuffer.position();

    return position + record.key().length + record.value().length + 10 <= MAX_FILE_SIZE;
  }

  public void close() throws IOException {
    dataChannel.close();
  }

  public int getMaxSize() {
    return MAX_FILE_SIZE;
  }

  public int getUsedSize() {
    return dataBuffer.position();
  }

  private byte[] getCurrentBufferValue() {
    int size = dataBuffer.getInt();
    byte[] bytes = new byte[size];
    dataBuffer.get(bytes);

    return bytes;
  }

  private void putInBuffer(byte[] bytes) {
    dataBuffer.putInt(bytes.length);
    dataBuffer.put(bytes);
  }
}
