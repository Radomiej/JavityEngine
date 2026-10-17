package org.javity.ecs.artemis;

/** Internal witness for the native entity lifetime owned by one link. */
public final class LinkLifetime extends com.artemis.Component {
  private EntityLink link;

  public LinkLifetime() {}

  void bind(EntityLink link) { this.link = link; }
  boolean matches(EntityLink link) { return this.link == link; }
  void clear() { link = null; }
}
