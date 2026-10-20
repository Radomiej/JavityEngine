package org.javity.gltf;

import com.badlogic.gdx.files.FileHandle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.javity.assets.AssetLease;
import org.javity.core.GameSystem;
import org.javity.serialization.AssetId;

/**
 * Optional scene-owned synchronous glTF manifest/lease service. Register before consumers. Uploads
 * happen on request, outside render batches. This is not the budgeted AssetStreams queue.
 */
public final class GltfAssets extends GameSystem implements AutoCloseable {
  private final Thread owner = Thread.currentThread();
  private final int maxBones;
  private final Map<AssetId<GltfAsset>, FileHandle> manifest = new LinkedHashMap<>();
  private final Map<AssetId<GltfAsset>, Entry> loaded = new LinkedHashMap<>();

  public GltfAssets(int maxBones) {
    if (maxBones < 1) throw new IllegalArgumentException("maxBones must be positive");
    this.maxBones = maxBones;
  }

  public GltfAssets register(AssetId<GltfAsset> id, FileHandle file) {
    requireOpen();
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(file, "file");
    if (manifest.containsKey(id))
      throw new IllegalArgumentException("Duplicate glTF AssetId: " + id.value());
    manifest.put(id, file);
    return this;
  }

  public AssetLease<GltfAsset> request(AssetId<GltfAsset> id) {
    requireOpen();
    FileHandle file = manifest.get(Objects.requireNonNull(id));
    if (file == null) throw new IllegalArgumentException("Unknown glTF AssetId: " + id.value());
    Entry entry = loaded.get(id);
    if (entry == null) {
      entry = new Entry();
      try {
        entry.asset = GltfAsset.load(file, maxBones);
      } catch (RuntimeException failure) {
        entry.failure = failure;
      }
      loaded.put(id, entry);
    }
    entry.users++;
    return new Lease(id, entry);
  }

  private void requireOpen() {
    checkThread();
    if (isDisposed()) throw new IllegalStateException("glTF asset service is closed");
  }

  private void checkThread() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("glTF service requires its owner thread");
  }

  @Override
  protected void validateDispose() {
    checkThread();
  }

  @Override
  protected void onRelease() {
    Throwable failure = null;
    for (Entry entry : loaded.values()) {
      try {
        if (entry.asset != null) entry.asset.close();
      } catch (RuntimeException | Error next) {
        if (failure == null) failure = next;
        else if (failure != next) failure.addSuppressed(next);
      }
    }
    loaded.clear();
    manifest.clear();
    if (failure instanceof RuntimeException next) throw next;
    if (failure instanceof Error next) throw next;
  }

  @Override
  public void close() {
    dispose();
  }

  private static final class Entry {
    GltfAsset asset;
    Throwable failure;
    int users;
  }

  private final class Lease implements AssetLease<GltfAsset> {
    private final AssetId<GltfAsset> id;
    private final Entry entry;
    private boolean closed;

    Lease(AssetId<GltfAsset> id, Entry entry) {
      this.id = id;
      this.entry = entry;
    }

    @Override
    public State state() {
      checkThread();
      return closed || isDisposed()
          ? State.CLOSED
          : entry.failure == null ? State.READY : State.FAILED;
    }

    @Override
    public GltfAsset get() {
      if (state() != State.READY)
        throw new IllegalStateException("glTF lease is " + state(), entry.failure);
      return entry.asset;
    }

    @Override
    public Throwable failure() {
      checkThread();
      return entry.failure;
    }

    @Override
    public void close() {
      checkThread();
      if (closed) return;
      closed = true;
      if (--entry.users == 0) {
        loaded.remove(id, entry);
        if (entry.asset != null) entry.asset.close();
      }
    }
  }
}
