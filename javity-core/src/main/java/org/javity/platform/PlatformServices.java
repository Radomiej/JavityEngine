package org.javity.platform;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Portable owner-thread facade. Call pump() from the game loop to deliver completions. */
public final class PlatformServices implements AutoCloseable {
  private final PlatformProvider provider;
  private final Thread owner = Thread.currentThread();
  private final ArrayDeque<Runnable> deliveries = new ArrayDeque<>();
  private final List<Request<?>> active = new ArrayList<>();
  private volatile boolean closed;

  public PlatformServices(PlatformProvider provider) {
    if (provider == null) throw new IllegalArgumentException("provider is required");
    this.provider = provider;
  }

  public PlatformProvider.Capabilities capabilities() {
    checkOwner();
    return provider.capabilities();
  }

  public PlatformProvider.Operation player(PlatformProvider.Callback<PlatformProvider.Player> cb) {
    return submit(cb, provider::player);
  }

  public PlatformProvider.Operation profile(
      PlatformProvider.Callback<PlatformProvider.Profile> cb) {
    return submit(cb, provider::profile);
  }

  public PlatformProvider.Operation getSave(
      String slot, PlatformProvider.Callback<PlatformProvider.Save> cb) {
    return submit(cb, done -> provider.getSave(slot, done));
  }

  public PlatformProvider.Operation putSave(
      String slot,
      String payload,
      long expectedRevision,
      PlatformProvider.Callback<PlatformProvider.Save> cb) {
    return submit(cb, done -> provider.putSave(slot, payload, expectedRevision, done));
  }

  public PlatformProvider.Operation listRooms(
      PlatformProvider.Callback<PlatformProvider.RoomList> cb) {
    return submit(cb, provider::listRooms);
  }

  public PlatformProvider.Operation createRoom(
      PlatformProvider.RoomRequest request, PlatformProvider.Callback<PlatformProvider.Room> cb) {
    return submit(cb, done -> provider.createRoom(request, done));
  }

  public PlatformProvider.Operation joinRoom(
      String id, String inviteCode, PlatformProvider.Callback<PlatformProvider.RoomTicket> cb) {
    return submit(cb, done -> provider.joinRoom(id, inviteCode, done));
  }

  public PlatformProvider.Operation closeRoom(
      String id, PlatformProvider.Callback<PlatformProvider.RoomCloseResult> cb) {
    return submit(cb, done -> provider.closeRoom(id, done));
  }

  /**
   * Processes the current completion batch on the constructing thread and returns consumed entries.
   */
  public int pump() {
    checkOwner();
    final int budget;
    synchronized (deliveries) {
      budget = deliveries.size();
    }
    int count = 0;
    while (count < budget) {
      Runnable next;
      synchronized (deliveries) {
        next = deliveries.pollFirst();
      }
      if (next == null) break;
      next.run();
      count++;
    }
    return count;
  }

  private interface Starter<T> {
    PlatformProvider.Operation start(PlatformProvider.Callback<T> callback);
  }

  private <T> PlatformProvider.Operation submit(
      PlatformProvider.Callback<T> callback, Starter<T> starter) {
    if (callback == null) throw new IllegalArgumentException("callback is required");
    checkOwner();
    Request<T> request = new Request<>(callback);
    if (closed) throw new IllegalStateException("PlatformServices is closed");
    active.add(request);
    try {
      request.delegate = starter.start(result -> enqueue(request, result));
      if (request.delegate == null)
        enqueue(
            request,
            PlatformResult.failure(
                PlatformResult.ErrorCode.REQUEST_FAILED, "provider returned a null operation"));
    } catch (RuntimeException failure) {
      enqueue(
          request,
          PlatformResult.failure(PlatformResult.ErrorCode.REQUEST_FAILED, failure.getMessage()));
    }
    return request;
  }

  private <T> void enqueue(Request<T> request, PlatformResult<T> result) {
    synchronized (deliveries) {
      if (request.cancelled || request.queued || closed) return;
      if (result == null)
        result =
            PlatformResult.failure(
                PlatformResult.ErrorCode.REQUEST_FAILED, "provider returned a null result");
      final PlatformResult<T> completion = result;
      request.queued = true;
      deliveries.addLast(
          () -> {
            if (request.cancelled || closed) return;
            request.cancelled = true;
            active.remove(request);
            request.callback.complete(completion);
          });
    }
  }

  private void checkOwner() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("PlatformServices must be used on its owner thread");
  }

  @Override
  public void close() {
    checkOwner();
    if (closed) return;
    closed = true;
    Throwable failure = null;
    for (Request<?> request : List.copyOf(active)) {
      try {
        request.cancel();
      } catch (RuntimeException | Error problem) {
        failure = combine(failure, problem);
      }
    }
    active.clear();
    synchronized (deliveries) {
      deliveries.clear();
    }
    try {
      provider.close();
    } catch (RuntimeException | Error problem) {
      failure = combine(failure, problem);
    }
    if (failure instanceof RuntimeException runtime) throw runtime;
    if (failure instanceof Error error) throw error;
  }

  private static Throwable combine(Throwable first, Throwable next) {
    if (first == null) return next;
    if (next != first) first.addSuppressed(next);
    return first;
  }

  private final class Request<T> implements PlatformProvider.Operation {
    final PlatformProvider.Callback<T> callback;
    PlatformProvider.Operation delegate;
    volatile boolean cancelled;
    boolean queued;

    Request(PlatformProvider.Callback<T> callback) {
      this.callback = callback;
    }

    @Override
    public void cancel() {
      checkOwner();
      if (cancelled) return;
      cancelled = true;
      active.remove(this);
      if (delegate != null) delegate.cancel();
    }
  }
}
