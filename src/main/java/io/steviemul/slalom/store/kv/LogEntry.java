package io.steviemul.slalom.store.kv;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LogEntry {

  public static final String OPERATION_PUT = "PUT";
  public static final String OPERATION_REMOVE = "REMOVE";

  private final String operation;

  @Getter private final String[] record;

  public boolean isPut() {
    return OPERATION_PUT.equalsIgnoreCase(operation);
  }

  public boolean isRemove() {
    return OPERATION_REMOVE.equalsIgnoreCase(operation);
  }
}
