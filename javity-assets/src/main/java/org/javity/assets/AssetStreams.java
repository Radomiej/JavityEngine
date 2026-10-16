package org.javity.assets;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.JavityAssetInternals142;
import com.badlogic.gdx.assets.JavityAssetInternals142.SavedAsset;
import java.util.*;
import org.javity.core.GameSystem;

/** Scene-owned local asset leases. Transfer an exclusively owned, configured EMPTY manager.
 * Do not subsequently use it through another owner (including SceneAssets); stateful loaders
 * must also be exclusive. Custom loaders own their private/unregistered allocations on errors.
 * Manager operations, leases, and disposal belong to the constructing (render) thread.
 * Call finishFrame outside all render batches. Disposal blocks to drain the active load.
 * This implementation admits at most one root, even when maxInflight is larger. */
public final class AssetStreams extends GameSystem {
  private final AssetManager manager;
  private final StreamBudget budget;
  private final Thread owner = Thread.currentThread();
  private final Map<String, Entry> current = new LinkedHashMap<>();
  private final List<Entry> entries = new ArrayList<>();
  private final Set<Lease<?>> leases = new LinkedHashSet<>();
  private Entry active;
  private Map<String, SavedAsset> baseline;
  private long sequence;

  public AssetStreams(AssetManager manager, StreamBudget budget) {
    this.manager = Objects.requireNonNull(manager, "manager");
    this.budget = Objects.requireNonNull(budget, "budget");
    JavityAssetInternals142.requireEmpty(manager);
    manager.setErrorListener((descriptor, cause) -> {
      if (active != null) fail(active, descriptor.fileName, cause);
    });
  }

  public <T> AssetLease<T> request(AssetKey<T> key, int priority, long estimatedResidentBytes) {
    requireOpen();
    Objects.requireNonNull(key, "key");
    if (estimatedResidentBytes < 0 || estimatedResidentBytes > budget.estimatedResidentBytes())
      throw new IllegalArgumentException("Estimate must fit the configured resident budget");
    // Also check obsolete generations still physically owned until finishFrame.
    for (Entry entry : entries)
      if (entry.key.path().equals(key.path()) && (!entry.key.type().equals(key.type()) || entry.bytes != estimatedResidentBytes))
        throw new IllegalArgumentException("Conflicting type or estimate for " + key.path());
    Class<?> loadedType = manager.getAssetType(key.path());
    if (loadedType != null && loadedType != key.type())
      throw new IllegalArgumentException("Conflicting loaded dependency type for " + key.path());
    Entry entry = current.get(key.path());
    if (entry == null) {
      entry = new Entry(key, priority, estimatedResidentBytes, sequence++);
      current.put(key.path(), entry);
      entries.add(entry);
    }
    Lease<T> lease = new Lease<>(key, entry);
    entry.users++;
    leases.add(lease);
    return lease;
  }

  @Override public void onUiUpdate(float unscaledDelta) {
    requireOpen();
    if (active != null && active.failure != null) return; // rollback waits for finishFrame
    if (active == null) admit();
    long started = System.nanoTime();
    do {
      if (active == null || active.failure != null) return;
      step();
    } while (active != null && active.failure == null
        && System.nanoTime() - started < budget.updateMillis() * 1_000_000L);
  }

  private void admit() {
    Entry next = entries.stream().filter(e -> e.state == AssetLease.State.QUEUED && e.users > 0)
        .min(Comparator.<Entry>comparingInt(e -> e.priority).reversed().thenComparingLong(e -> e.sequence)).orElse(null);
    if (next == null) return;
    // A closed active generation must be physically released before a same-path fresh load.
    if (entries.stream().anyMatch(e -> e != next && e.key.path().equals(next.key.path()))) return;
    active = next;
    baseline = JavityAssetInternals142.snapshot(manager);
    next.state = AssetLease.State.LOADING;
    try { manager.load(next.key.path(), next.key.type()); }
    catch (RuntimeException | Error ex) { fail(next, next.key.path(), ex); }
  }

  private void step() {
    Entry entry = active;
    try {
      boolean done = manager.update();
      if (entry.failure != null) return;
      if (done) {
        entry.value = manager.get(entry.key.path(), entry.key.type());
        entry.state = AssetLease.State.READY;
        active = null;
        baseline = null;
      }
    } catch (RuntimeException | Error ex) {
      fail(entry, entry.key.path(), ex);
    }
  }

  private void fail(Entry entry, String dependency, Throwable cause) {
    if (entry.failure == null) {
      entry.failure = new IllegalStateException("Loading root '" + entry.key.path()
          + "' failed at '" + dependency + "'", cause);
      entry.state = AssetLease.State.FAILED;
      current.remove(entry.key.path(), entry);
    } else JavityAssetInternals142.append(entry.failure, cause);
  }

  /** Safe physical release/rollback boundary after rendering, outside every batch. */
  public void finishFrame() {
    requireOpen();
    throwIfFailure(cleanup());
  }

  private Throwable cleanup() {
    Throwable failure = null;
    if (active != null) {
      if (active.failure == null) return null; // freeze baseline until the transaction settles
      Entry failed = active;
      JavityAssetInternals142.abandonFailedTasks(manager);
      failure = JavityAssetInternals142.restore(manager, baseline);
      JavityAssetInternals142.append(failed.failure, failure);
      entries.remove(failed);
      active = null;
      baseline = null;
    }
    for (Entry entry : List.copyOf(entries)) {
      if (entry.users != 0) continue;
      if (entry.state == AssetLease.State.READY)
        failure = JavityAssetInternals142.append(failure, JavityAssetInternals142.releaseRoot(manager, entry.key.path()));
      entries.remove(entry);
      entry.value = null;
    }
    return failure;
  }

  private void requireOwner() {
    if (Thread.currentThread() != owner) throw new IllegalStateException("AssetStreams requires its owner thread");
  }
  private void requireOpen() {
    requireOwner();
    if (isDisposed()) throw new IllegalStateException("AssetStreams is disposed");
  }
  @Override protected void validateDispose() { requireOwner(); }

  @Override protected void onRelease() {
    for (Lease<?> lease : List.copyOf(leases)) lease.close();
    Throwable failure = null;
    try {
      while (active != null && active.failure == null) {
        step();
        Thread.yield(); // no manager monitor held while waiting for dependency injection
      }
      failure = cleanup();
    } catch (RuntimeException | Error ex) { failure = ex; }
    try {
      JavityAssetInternals142.abandonFailedTasks(manager);
      failure = JavityAssetInternals142.append(failure, JavityAssetInternals142.restore(manager, Map.of()));
    } catch (RuntimeException | Error ex) { failure = JavityAssetInternals142.append(failure, ex); }
    try { manager.dispose(); }
    catch (RuntimeException | Error ex) { failure = JavityAssetInternals142.append(failure, ex); }
    entries.clear(); current.clear(); active = null; baseline = null;
    throwIfFailure(failure);
  }

  private static void throwIfFailure(Throwable failure) {
    if (failure instanceof RuntimeException ex) throw ex;
    if (failure instanceof Error ex) throw ex;
  }

  private static final class Entry {
    final AssetKey<?> key;
    final int priority;
    final long bytes, sequence;
    int users;
    AssetLease.State state = AssetLease.State.QUEUED;
    Object value;
    Throwable failure;
    Entry(AssetKey<?> key, int priority, long bytes, long sequence) {
      this.key = key; this.priority = priority; this.bytes = bytes; this.sequence = sequence;
    }
  }

  private final class Lease<T> implements AssetLease<T> {
    private final AssetKey<T> key;
    private final Entry entry;
    private boolean closed;
    Lease(AssetKey<T> key, Entry entry) { this.key = key; this.entry = entry; }
    @Override public State state() { requireOwner(); return closed ? State.CLOSED : entry.state; }
    @Override public T get() {
      requireOwner();
      if (state() != State.READY) throw new IllegalStateException("Asset is not READY: " + key.path());
      return key.type().cast(entry.value);
    }
    @Override public Throwable failure() { requireOwner(); return entry.failure; }
    @Override public void close() {
      requireOwner();
      if (closed) return;
      closed = true;
      leases.remove(this);
      if (--entry.users == 0) current.remove(key.path(), entry);
    }
  }
}
