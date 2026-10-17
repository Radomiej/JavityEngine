package org.javity.ecs.artemis;

import java.util.List;
import java.util.Objects;
import org.javity.core.Component;
import org.javity.core.GameObject;

/** A typed, single-writer projection between one core and one native component type. */
public abstract class ComponentProjection<C extends Component, E extends com.artemis.Component> {
  private final Class<C> coreType;
  private final Class<E> ecsType;
  private final ProjectionDirection direction;
  private final ProjectionOwnership ownership;
  protected ComponentProjection(Class<C> coreType, Class<E> ecsType, ProjectionDirection direction, ProjectionOwnership ownership) {
    this.coreType = Objects.requireNonNull(coreType); this.ecsType = Objects.requireNonNull(ecsType);
    this.direction = Objects.requireNonNull(direction); this.ownership = Objects.requireNonNull(ownership);
    if (direction == ProjectionDirection.ECS_TO_GO && ownership != ProjectionOwnership.BORROWED)
      throw new IllegalArgumentException("ECS_TO_GO requires BORROWED core ownership");
  }
  public final Class<C> coreType() { return coreType; }
  public final Class<E> ecsType() { return ecsType; }
  public final ProjectionDirection direction() { return direction; }
  public final ProjectionOwnership ownership() { return ownership; }
  protected C select(GameObject object) {
    List<C> matches = object.getComponents(coreType);
    if (matches.size() > 1) throw new IllegalStateException("Multiple " + coreType.getName() + " components; override select(GameObject)");
    return matches.isEmpty() ? null : matches.get(0);
  }
  protected void copyToEntity(C source, E target) { throw new UnsupportedOperationException("GO_TO_ECS callback not implemented"); }
  protected void copyToObject(E source, C target) { throw new UnsupportedOperationException("ECS_TO_GO callback not implemented"); }
  final C selected(GameObject object) {
    C selected = select(object);
    if (selected != null && (!coreType.isInstance(selected) || !object.containsComponent(selected)))
      throw new IllegalArgumentException("Projection selector must return a matching component attached to the linked GameObject");
    return selected;
  }
  @SuppressWarnings("unchecked") final void toEntity(Component source, com.artemis.Component target) { copyToEntity((C) source, (E) target); }
  @SuppressWarnings("unchecked") final void toObject(com.artemis.Component source, Component target) { copyToObject((E) source, (C) target); }
}
