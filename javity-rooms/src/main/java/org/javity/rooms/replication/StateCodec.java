package org.javity.rooms.replication;

/** Application-owned, deterministic state encoding. Implementations must reject malformed data. */
public interface StateCodec<T> {
  byte[] encode(T state);

  T decode(byte[] bytes);

  /** Interpolates between decoded states; return an endpoint for non-interpolated schemas. */
  T interpolate(T older, T newer, double alpha);
}
