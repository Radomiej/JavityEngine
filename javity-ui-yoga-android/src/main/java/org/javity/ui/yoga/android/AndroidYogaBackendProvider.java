package org.javity.ui.yoga.android;

import org.javity.ui.yoga.YogaBackend;
import org.javity.ui.yoga.YogaBackendProvider;

public final class AndroidYogaBackendProvider implements YogaBackendProvider {
  @Override
  public YogaBackend createBackend() {
    return new AndroidYogaBackend();
  }
}
