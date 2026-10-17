package org.javity.ecs.artemis;

import com.artemis.ComponentMapper;
import org.javity.core.Component;

final class ProjectionSession {
  final ComponentProjection<?, ?> projection;
  Component ownedCore;
  com.artemis.Component ownedEcs;
  ProjectionSession(ComponentProjection<?, ?> projection) { this.projection = projection; }
  void clear() { ownedCore = null; ownedEcs = null; }
}
