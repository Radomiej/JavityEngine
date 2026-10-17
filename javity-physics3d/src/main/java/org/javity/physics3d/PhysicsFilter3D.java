package org.javity.physics3d;

/** Query filter. Groups and masks use Bullet's mutual unsigned 16-bit convention. */
public record PhysicsFilter3D(
    int group, int mask, boolean includeTriggers, Integer ignoredObjectId) {
  public PhysicsFilter3D {
    validateBits(group, "group");
    validateBits(mask, "mask");
  }

  public PhysicsFilter3D(int group, int mask, boolean includeTriggers) {
    this(group, mask, includeTriggers, null);
  }

  public static PhysicsFilter3D all() {
    return new PhysicsFilter3D(0xffff, 0xffff, false, null);
  }

  static void validateBits(int bits, String name) {
    if (bits < 0 || bits > 0xffff)
      throw new IllegalArgumentException(name + " must be unsigned 16-bit");
  }
}
