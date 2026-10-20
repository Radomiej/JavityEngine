package org.javity.rooms.replication;

import java.nio.ByteBuffer;

/**
 * Small sample schema for position replication; applications should define their own schema IDs.
 */
public record Transform2DState(float x, float y) {
  public static final String SCHEMA_ID = "javity.transform2d.position";
  public static final int VERSION = 1;

  public Transform2DState {
    if (!Float.isFinite(x) || !Float.isFinite(y))
      throw new IllegalArgumentException("non-finite position");
  }

  public static ReplicationSchema<Transform2DState> schema() {
    return new ReplicationSchema<>(
        SCHEMA_ID,
        VERSION,
        new StateCodec<>() {
          public byte[] encode(Transform2DState s) {
            return ByteBuffer.allocate(8).putFloat(s.x).putFloat(s.y).array();
          }

          public Transform2DState decode(byte[] b) {
            if (b.length != 8) throw new IllegalArgumentException("expected 8 bytes");
            ByteBuffer in = ByteBuffer.wrap(b);
            return new Transform2DState(in.getFloat(), in.getFloat());
          }

          public Transform2DState interpolate(
              Transform2DState a, Transform2DState b, double alpha) {
            return new Transform2DState(
                (float) (a.x + (b.x - a.x) * alpha), (float) (a.y + (b.y - a.y) * alpha));
          }
        });
  }
}
