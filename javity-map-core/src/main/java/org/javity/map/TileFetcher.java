package org.javity.map;

/** Worker-thread byte provider. null means missing; must honor interruption/timeouts. */
@FunctionalInterface
public interface TileFetcher extends AutoCloseable {
  byte[] fetch(TileKey key) throws Exception;

  default String attribution() {
    return "";
  }

  default void close() {}
}
