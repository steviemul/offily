package io.steviemul.slalom.store;

import io.steviemul.slalom.store.kv.DataFileRecord;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;

public class Utils {

  private Utils() {}

  public static String encodeBytes(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }

  public static byte[] decodeBytes(String string) {
    return Base64.getDecoder().decode(string);
  }

  public static String objectToBase64String(Object obj) {
    return encodeBytes(objectToBytes(obj));
  }

  public static Object base64StringToObject(String string) {
    return bytesToObject(decodeBytes(string));
  }

  public static byte[] objectToBytes(Object obj) {

    try (ByteArrayOutputStream bOut = new ByteArrayOutputStream()) {
      try (ObjectOutputStream oOut = new ObjectOutputStream(bOut)) {
        oOut.writeObject(obj);
        oOut.flush();
      }

      bOut.close();
      return bOut.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("Unable to serialize object", e);
    }
  }

  public static Object bytesToObject(byte[] objectBytes) {
    try (ByteArrayInputStream bIn = new ByteArrayInputStream(objectBytes)) {
      try (ObjectInputStream oIn = new ObjectInputStream(bIn)) {
        return oIn.readObject();
      }
    } catch (Exception e) {
      throw new RuntimeException("Unable to deserialize object", e);
    }
  }

  public static void deleteDirectory(File directory) {

    File[] children = directory.listFiles();

    for (File child : children) {
      if (child.isFile()) {
        child.delete();
      } else {
        deleteDirectory(child);
      }
    }

    directory.delete();
  }

  public static String getStoreFilename(String name, String extension, Integer identifier) {
    return identifier == null ? name + "." + extension : name + "_" + identifier + "." + extension;
  }

  public static DataFileRecord getDataFileRecord(Object key, Object value) {
    byte[] keyBytes = objectToBytes(key);
    byte[] valueBytes = objectToBytes(value);

    return new DataFileRecord(keyBytes, valueBytes);
  }
}
