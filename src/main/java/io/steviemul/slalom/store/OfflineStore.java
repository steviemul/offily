package io.steviemul.slalom.store;

import static io.steviemul.slalom.store.Utils.deleteDirectory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OfflineStore<K, V> implements Store<K, V> {

  private static final String INDEX = ".index";
  private static final String BASE_ROOT_FOLDER = ".cache";

  private final LinkedHashMap<K, UUID> index = new LinkedHashMap<>();
  private final File root;

  public OfflineStore(String location) {
    this.root = new File(BASE_ROOT_FOLDER, location);

    if (this.root.exists() && this.root.isFile()) {
      throw new IllegalArgumentException("Invalid root location");
    }

    this.root.mkdirs();
  }

  @Override
  public boolean contains(K key) {
    return index.containsKey(key);
  }

  @Override
  public V get(K key) {
    UUID location = index.get(key);

    if (location != null) {
      Object object = readObject(new File(root, location.toString()));

      return (V) object;
    }

    return null;
  }

  @Override
  public V put(K key, V value) {
    UUID location = UUID.randomUUID();

    File objectLocation = new File(root, location.toString());

    writeObject(value, objectLocation);

    index.put(key, location);

    saveIndex();

    return value;
  }

  @Override
  public V remove(K key) {
    UUID location = index.get(key);

    if (location != null) {
      File objectFile = new File(root, location.toString());

      Object object = readObject(new File(root, location.toString()));

      objectFile.delete();

      index.remove(key);

      saveIndex();

      return (V) object;
    }

    return null;
  }

  @Override
  public void clear() {
    deleteDirectory(root);
  }

  @Override
  public void close() {}

  private void saveIndex() {
    writeObject(index, new File(root, INDEX));
  }

  private void writeObject(Object obj, File location) {

    if (location.exists()) return;

    try (OutputStream fOut = new FileOutputStream(location)) {
      try (ObjectOutputStream oOut = new ObjectOutputStream(fOut)) {
        oOut.writeObject(obj);
        oOut.flush();
      }
    } catch (Exception e) {
      throw new RuntimeException("Unable to write object", e);
    }
  }

  private Object readObject(File location) {
    try (InputStream fIn = new FileInputStream(location)) {
      try (ObjectInputStream oIn = new ObjectInputStream(fIn)) {
        return oIn.readObject();
      }
    } catch (Exception e) {
      throw new RuntimeException("Unable to read object", e);
    }
  }
}
