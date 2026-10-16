package org.javity.physics3d;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import java.util.List;
import java.util.Objects;

/** Bounded static box set for CPU-generated terrain; publication is explicit through Physics3D. */
public final class StaticCompoundCollider3D extends Collider3D {
  public static final int MAX_BOXES = 4096;
  private List<CompoundCollider3D.Child> boxes;
  private long configurationRevision;
  private boolean publicationRequested;

  public StaticCompoundCollider3D(List<? extends CompoundCollider3D.Child> boxes) {
    this.boxes = copyAndValidate(boxes);
  }

  /** Replaces the copied box set. Physics3D publishes it only through publishStaticCollision. */
  public StaticCompoundCollider3D setBoxes(List<? extends CompoundCollider3D.Child> boxes) {
    List<CompoundCollider3D.Child> copy = copyAndValidate(boxes);
    if (!this.boxes.equals(copy)) {
      this.boxes = copy;
      configurationRevision++;
      revision++;
    }
    return this;
  }

  public List<CompoundCollider3D.Child> getBoxes() {
    return boxes;
  }

  public long configurationRevision() {
    return configurationRevision;
  }

  public boolean isEmpty() {
    return boxes.isEmpty();
  }

  boolean publicationRequested() {
    return publicationRequested;
  }

  void publicationRequested(boolean value) {
    publicationRequested = value;
  }

  @Override
  void addTo(ShapeBuilder builder, Vector3 offset, Quaternion rotation) {
    if (isTrigger()) throw new IllegalStateException("Static compound terrain cannot be a trigger");
    if (boxes.isEmpty()) return;
    for (int start = 0; start < boxes.size(); start += 64)
      new CompoundCollider3D(boxes.subList(start, Math.min(start + 64, boxes.size())))
          .addTo(builder, offset, rotation);
  }

  @Override
  String descriptor() {
    return "static-compound:" + boxes;
  }

  private static List<CompoundCollider3D.Child> copyAndValidate(
      List<? extends CompoundCollider3D.Child> boxes) {
    Objects.requireNonNull(boxes, "boxes");
    if (boxes.size() > MAX_BOXES)
      throw new IllegalArgumentException("Static compound supports at most 4096 boxes");
    for (CompoundCollider3D.Child child : boxes) {
      if (!(Objects.requireNonNull(child) instanceof CompoundCollider3D.Box))
        throw new IllegalArgumentException("Static compound accepts box children only");
    }
    return List.copyOf(boxes);
  }
}
