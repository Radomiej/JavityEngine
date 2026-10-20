package org.javity.render;

import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;

/** Exclusive ModelBatch provider with explicit recovery after managed programs relink. */
public interface RecoverableShaderProvider extends ShaderProvider {
  /** Starts a view outside ModelBatch.begin/end; borrowed environment adapters may reset here. */
  default void beginView() {}

  void contextRestored();
}
