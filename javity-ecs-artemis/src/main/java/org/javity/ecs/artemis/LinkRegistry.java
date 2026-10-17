package org.javity.ecs.artemis;

import java.util.*;
import org.javity.core.GameObject;

/** Owner-thread registry of current GameObject/entity generations. */
final class LinkRegistry {
  private final IdentityHashMap<GameObject, EntityLink> byObject = new IdentityHashMap<>();
  private final Map<Integer, EntityLink> byEntity = new HashMap<>();
  private final List<EntityLink> links = new ArrayList<>();
  private long nextGeneration = 1;

  long nextGeneration() {
    if (nextGeneration <= 0 || nextGeneration == Long.MAX_VALUE)
      throw new IllegalStateException("Link generation exhausted");
    return nextGeneration++;
  }
  boolean contains(GameObject object) { return byObject.containsKey(object); }
  boolean contains(int entityId) { return byEntity.containsKey(entityId); }
  EntityLink forEntity(int entityId) { return byEntity.get(entityId); }
  void add(EntityLink link) {
    byObject.put(link.object, link); byEntity.put(link.id, link); links.add(link);
  }
  void remove(EntityLink link) {
    byObject.remove(link.object, link); byEntity.remove(link.id, link); links.remove(link);
  }
  List<EntityLink> snapshot() { return List.copyOf(links); }
}
