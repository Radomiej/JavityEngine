package org.javity.ui.yoga;

import java.util.Iterator;
import java.util.ServiceLoader;

/** Resolves the single Yoga implementation installed by the application runtime. */
public final class YogaBackends {
  private YogaBackends() {}

  public static YogaBackend createDefault() {
    Iterator<YogaBackendProvider> providers =
        ServiceLoader.load(YogaBackendProvider.class).iterator();
    if (!providers.hasNext())
      throw new IllegalStateException(
          "No Yoga backend is installed; add javity-ui-yoga-lwjgl or javity-ui-yoga-android");
    YogaBackend backend = providers.next().createBackend();
    if (providers.hasNext())
      throw new IllegalStateException("More than one Yoga backend is installed");
    return backend;
  }
}
