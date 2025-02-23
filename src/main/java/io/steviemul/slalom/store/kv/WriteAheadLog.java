package io.steviemul.slalom.store.kv;

import static io.steviemul.slalom.store.Utils.encodeBytes;
import static io.steviemul.slalom.store.Utils.getStoreFilename;
import static io.steviemul.slalom.store.Utils.objectToBase64String;
import static io.steviemul.slalom.store.kv.LogEntry.OPERATION_PUT;
import static io.steviemul.slalom.store.kv.LogEntry.OPERATION_REMOVE;

import io.steviemul.slalom.store.StoreException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WriteAheadLog<K, V> {

  private static final String LOG_FILE = "ops";
  private static final String EXT = "log";
  private static final String LOG_DELIM = ":";
  private static final String NEWLINE = "\n";

  private final File logFile;
  private final BufferedWriter logWriter;

  public WriteAheadLog(File root) throws IOException {
    this(root, null);
  }

  public WriteAheadLog(File root, Integer identifier) throws IOException {
    String logFilename = getStoreFilename(LOG_FILE, EXT, identifier);

    this.logFile = new File(root, logFilename);
    this.logWriter = new BufferedWriter(new FileWriter(this.logFile, true));
  }

  public void put(K key, V value) throws IOException {
    addToLog(OPERATION_PUT, objectToBase64String(key), objectToBase64String(value));
  }

  public void put(byte[] key, byte[] value) throws IOException {
    addToLog(OPERATION_PUT, encodeBytes(key), encodeBytes(value));
  }

  public void remove(K key, V value) throws IOException {
    addToLog(OPERATION_REMOVE, objectToBase64String(key), objectToBase64String(value));
  }

  public void remove(byte[] key, byte[] value) throws IOException {
    addToLog(OPERATION_REMOVE, encodeBytes(key), encodeBytes(value));
  }

  public void processRecords(Consumer<LogEntry> processor) {

    if (!logFile.exists()) return;

    try (Reader reader = new FileReader(logFile)) {
      try (BufferedReader buffer = new BufferedReader(reader)) {
        String line;

        while ((line = buffer.readLine()) != null) {
          String[] parts = line.split(LOG_DELIM);

          String operation = parts[0];
          String[] record = Arrays.copyOfRange(parts, 1, parts.length);

          processor.accept(new LogEntry(operation, record));
        }
      }
    } catch (IOException e) {
      throw new StoreException("Unable to process records", e);
    }
  }

  public void close() throws IOException {
    logWriter.close();
  }

  private void addToLog(String operation, String key, String value) throws IOException {

    synchronized (logWriter) {
      logWriter.write(
          operation + LOG_DELIM + key + LOG_DELIM + (value != null ? value : "") + NEWLINE);
      logWriter.flush();
    }
  }
}
