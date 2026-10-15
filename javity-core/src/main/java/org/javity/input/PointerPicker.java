package org.javity.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import java.util.*;
import org.javity.core.*;
import org.javity.render.Camera2D;

/** After Stage in InputMultiplexer. Captures matching left clicks, checks lifecycle on release. */
public final class PointerPicker extends InputAdapter {
  private final Game game;
  private final Camera2D camera;
  private final Map<Integer, Pickable2D> captures = new HashMap<>();

  public PointerPicker(Game game, Camera2D camera) {
    this.game = Objects.requireNonNull(game);
    this.camera = Objects.requireNonNull(camera);
    if (camera.getGame() != game) throw new IllegalArgumentException("Camera from another scene");
  }

  private boolean live(Component c) {
    return !game.isDisposed()
        && c.isInitialized()
        && c.isEnabled()
        && c.getGameObject().isActive()
        && !c.getGameObject().isDestroyed();
  }

  public Pickable2D pick(Vector2 world) {
    List<Pickable2D> all = new ArrayList<>();
    for (GameObject o : game.getObjectsWith(Pickable2D.class))
      for (Pickable2D c : o.getComponents(Pickable2D.class))
        if (live(c) && c.contains(world)) all.add(c);
    all.sort(
        Comparator.comparingInt((Pickable2D c) -> c.layer)
            .thenComparingInt(c -> c.order)
            .thenComparingInt(c -> c.getGameObject().id)
            .reversed());
    return all.isEmpty() ? null : all.getFirst();
  }

  public boolean touchDown(int x, int y, int pointer, int button) {
    if (button != Input.Buttons.LEFT) return false;
    Pickable2D hit = pick(camera.screenToWorld(x, y));
    if (hit == null) return false;
    captures.put(pointer, hit);
    return true;
  }

  public boolean touchUp(int x, int y, int pointer, int button) {
    if (button != Input.Buttons.LEFT) return false;
    Pickable2D hit = captures.remove(pointer);
    if (hit == null) return false;
    Vector2 world = camera.screenToWorld(x, y);
    if (live(hit) && hit.contains(world)) hit.click(world);
    return true;
  }

  public boolean touchCancelled(int x, int y, int pointer, int button) {
    return captures.remove(pointer) != null;
  }

  public void cancelAll() {
    captures.clear();
  }
}
