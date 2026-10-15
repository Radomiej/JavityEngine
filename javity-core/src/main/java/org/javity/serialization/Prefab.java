package org.javity.serialization;

import java.util.*;
import org.javity.core.*;

/** Immutable serialized template. Instantiation creates fresh components and IDs. */
public record Prefab(String json) {
  public Prefab {
    Objects.requireNonNull(json);
  }

  public List<GameObject> instantiate(Game game, SceneJson schema) {
    return schema.read(game, json);
  }
}
