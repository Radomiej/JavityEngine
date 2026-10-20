package org.javity.render;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import java.util.IdentityHashMap;
import org.javity.core.Component;

/** Optional owned stage after presented world collection and before ordinary color submission. */
public interface WorldRenderPass extends Disposable {
  /** Validate before ownership transfer. A failed check leaves ownership with the caller. */
  default void validateShaderProvider(RecoverableShaderProvider provider) {}

  /**
   * All arguments are borrowed for this invocation only. Renderables already have the selected
   * scene environment; a pass may assign stable per-renderable environments for color submission.
   * Source geometry, transforms, materials and shader references remain borrowed and unmodified.
   */
  void render(
      Camera camera,
      Array<Renderable> renderables,
      IdentityHashMap<Renderable, Component> owners,
      Environment environment);

  /** GL owner thread, outside active batches, after managed resources have been restored. */
  default void contextRestored() {}
}
