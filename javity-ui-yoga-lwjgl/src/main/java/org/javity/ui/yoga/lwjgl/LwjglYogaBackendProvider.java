package org.javity.ui.yoga.lwjgl;

import org.javity.ui.yoga.YogaBackend;
import org.javity.ui.yoga.YogaBackendProvider;

public final class LwjglYogaBackendProvider implements YogaBackendProvider {
  @Override
  public YogaBackend createBackend() {
    return new LwjglYogaBackend();
  }
}
