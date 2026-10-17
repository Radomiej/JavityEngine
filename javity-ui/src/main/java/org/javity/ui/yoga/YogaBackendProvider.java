package org.javity.ui.yoga;

/** Platform service loaded from the selected runtime backend module. */
public interface YogaBackendProvider {
  YogaBackend createBackend();
}
