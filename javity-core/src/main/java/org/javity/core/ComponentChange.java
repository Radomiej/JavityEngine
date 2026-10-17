package org.javity.core;

public record ComponentChange(Type type, GameObject object, Component component) {
  public enum Type {
    ADDED,
    REMOVED
  }
}
