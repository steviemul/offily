package io.steviemul.slalom.store;

public class StoreException extends RuntimeException {

  public StoreException(String message, Exception cause) {
    super(message, cause);
  }
}
