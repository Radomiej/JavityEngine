package org.javity.ecs.artemis;

import com.artemis.EntityEdit;
import java.util.*;
import java.util.function.Consumer;
import org.javity.core.GameObject;
import org.javity.serialization.Prefab;
import org.javity.serialization.SceneJson;

/** Instantiates core prefab objects and binds each to a fresh native entity. */
public final class LinkedPrefabFactory {
  private LinkedPrefabFactory() {}

  public static List<EntityLink> instantiate(ArtemisSystem system, Prefab prefab, SceneJson schema,
      Consumer<EntityEdit> configureEntity) {
    Objects.requireNonNull(system); Objects.requireNonNull(prefab); Objects.requireNonNull(schema);
    system.checkOwnerForMutation();
    var game = system.getGame();
    List<GameObject> created = new ArrayList<>();
    List<EntityLink> links = new ArrayList<>();
    try {
      created.addAll(prefab.instantiate(game, schema));
      for (GameObject object : created) {
        EntityLink link = system.allocateFactoryLink(object);
        links.add(link);
        if (configureEntity != null) configureEntity.accept(system.world().edit(link.entity()));
        if (!link.isValid()) throw new IllegalStateException("Factory configuration invalidated its link");
      }
      for (EntityLink link : links) if (!link.isValid())
        throw new IllegalStateException("Factory configuration invalidated an earlier link");
      // The transaction is only committed after every object has configured.
      for (EntityLink link : links) link.factoryOwned = false;
      return List.copyOf(links);
    } catch (RuntimeException | Error failure) {
      for (EntityLink link : List.copyOf(links)) try { system.rollbackCreatedLink(link); } catch (RuntimeException | Error e) { failure.addSuppressed(e); }
      for (GameObject object : created) object.destroy();
      throw failure;
    }
  }
}
