package org.javity.ecs.artemis;

import com.artemis.Component;
import org.javity.core.GameObject;

/** Reserved bridge component. Consumers may read the object but cannot forge its token. */
public final class GameObjectRef extends Component {
  private GameObject object;
  private EntityLink token;
  public GameObjectRef() {}
  public GameObject objectOrNull() { return object; }
  void bind(GameObject object, EntityLink token) { this.object = object; this.token = token; }
  void clear() { object = null; token = null; }
  boolean matches(GameObject object, EntityLink token) { return this.object == object && this.token == token; }
}
