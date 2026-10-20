package org.javity.core;

import java.util.ArrayDeque;
import java.util.Objects;

/** Owns closeable resources and releases them in reverse registration order. */
public final class ResourceScope implements AutoCloseable {
  private final ArrayDeque<AutoCloseable> resources = new ArrayDeque<>();
  private boolean closed;

  /** Adds a resource to this scope and returns it for convenient ownership registration. */
  public synchronized <T extends AutoCloseable> T own(T resource) {
    Objects.requireNonNull(resource, "resource");
    if (closed) throw new IllegalStateException("Resource scope is closed");
    resources.push(resource);
    return resource;
  }

  public synchronized boolean isClosed() {
    return closed;
  }

  /** Closes every resource once, in LIFO order, then reports the first failure. */
  @Override
  public void close() {
    synchronized (this) {
      if (closed) return;
      closed = true;
    }

    Throwable failure = null;
    while (true) {
      AutoCloseable resource;
      synchronized (this) {
        resource = resources.poll();
      }
      if (resource == null) break;
      try {
        resource.close();
      } catch (RuntimeException | Error e) {
        failure = append(failure, e);
      } catch (Exception e) {
        failure = append(failure, new ResourceCloseException(e));
      }
    }
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }

  private static Throwable append(Throwable failure, Throwable next) {
    if (failure == null) return next;
    if (failure != next) failure.addSuppressed(next);
    return failure;
  }

  private static final class ResourceCloseException extends RuntimeException {
    private ResourceCloseException(Exception cause) {
      super("Failed to close an owned resource", cause);
    }
  }
}
