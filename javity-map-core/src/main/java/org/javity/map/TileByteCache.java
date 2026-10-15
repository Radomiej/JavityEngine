package org.javity.map;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Nonblocking poll, bounded workers/pending/retry metadata and bounded disk XYZ cache. Use a
 * distinct cache directory for each provider/style. Owns fetcher and executor.
 */
public final class TileByteCache implements AutoCloseable {
  public static final int MAX_BYTES = 1024 * 1024;

  private record Retry(long deadline, String reason) {}

  private final TileFetcher fetcher;
  private final Path directory;
  private final ExecutorService executor;
  private final int maxPending, maxDiskTiles;
  private final long retryNanos;
  private final Object diskLock = new Object();
  private final Map<TileKey, Future<byte[]>> pending = new LinkedHashMap<>();
  private final LinkedHashMap<TileKey, Retry> retries = new LinkedHashMap<>();
  private volatile boolean closed;

  public TileByteCache(
      TileFetcher fetcher,
      Path directory,
      int workers,
      int maxPending,
      int maxDiskTiles,
      long retryMillis)
      throws IOException {
    if (workers < 1 || maxPending < 1 || maxDiskTiles < 1 || retryMillis < 1)
      throw new IllegalArgumentException("Positive limits required");
    this.fetcher = Objects.requireNonNull(fetcher);
    this.directory = Objects.requireNonNull(directory);
    this.maxPending = maxPending;
    this.maxDiskTiles = maxDiskTiles;
    retryNanos = TimeUnit.MILLISECONDS.toNanos(retryMillis);
    Files.createDirectories(directory);
    executor =
        Executors.newFixedThreadPool(
            workers,
            task -> {
              Thread thread = new Thread(task, "javity-tile-fetch");
              thread.setDaemon(true);
              return thread;
            });
  }

  public String attribution() {
    return fetcher.attribution();
  }

  private Path path(TileKey key) {
    return directory.resolve(key.zoom() + "-" + key.x() + "-" + key.y() + ".tile");
  }

  public synchronized int pendingCount() {
    return pending.size();
  }

  public synchronized String lastFailure(TileKey key) {
    Retry r = retries.get(key);
    return r == null ? null : r.reason;
  }

  private void failed(TileKey key, String reason) {
    retries.remove(key);
    retries.put(key, new Retry(System.nanoTime() + retryNanos, reason));
    while (retries.size() > 256) retries.remove(retries.keySet().iterator().next());
  }

  /** Returns bytes once ready, null while pending/missing/backoff. Never waits on a download. */
  public synchronized byte[] poll(TileKey key) {
    if (closed) throw new IllegalStateException("Tile source closed");
    Objects.requireNonNull(key);
    Future<byte[]> future = pending.get(key);
    if (future != null) {
      if (!future.isDone()) return null;
      pending.remove(key);
      try {
        byte[] bytes = future.get();
        if (bytes == null) {
          failed(key, "Missing tile");
          return null;
        }
        retries.remove(key);
        return bytes;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        failed(key, "Interrupted");
        return null;
      } catch (ExecutionException | CancellationException e) {
        failed(key, "Tile load failed: " + e.getMessage());
        return null;
      }
    }
    Retry retry = retries.get(key);
    if (retry != null && System.nanoTime() < retry.deadline) return null;
    if (pending.size() >= maxPending) pending.entrySet().removeIf(e -> e.getValue().isDone());
    if (pending.size() >= maxPending) return null;
    pending.put(key, executor.submit(() -> load(key)));
    return null;
  }

  private byte[] load(TileKey key) throws Exception {
    Path file = path(key);
    synchronized (diskLock) {
      if (Files.exists(file)) {
        if (Files.size(file) > MAX_BYTES) {
          Files.delete(file);
        } else {
          byte[] bytes = Files.readAllBytes(file);
          if (bytes.length > 0) {
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
            return bytes;
          }
          Files.delete(file);
        }
      }
    }
    byte[] bytes = fetcher.fetch(key);
    if (bytes == null) return null;
    if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Invalid tile bytes");
    if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedException();
    synchronized (diskLock) {
      if (closed) throw new InterruptedException();
      Path temporary = Files.createTempFile(directory, "tile-", ".tmp");
      try {
        Files.write(temporary, bytes);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temporary);
      }
      List<Path> files;
      try (var stream = Files.list(directory)) {
        files =
            stream
                .filter(p -> p.getFileName().toString().endsWith(".tile"))
                .sorted(
                    Comparator.comparingLong(
                        p -> {
                          try {
                            return Files.getLastModifiedTime(p).toMillis();
                          } catch (IOException e) {
                            return 0;
                          }
                        }))
                .toList();
      }
      for (int i = 0; i < files.size() - maxDiskTiles; i++) Files.deleteIfExists(files.get(i));
    }
    return bytes;
  }

  /** Corrupt/unsupported image: discard cached bytes and back off. */
  public synchronized void invalidate(TileKey key, String reason) {
    if (closed) throw new IllegalStateException("Tile source closed");
    failed(key, reason);
    executor.submit(
        () -> {
          synchronized (diskLock) {
            try {
              Files.deleteIfExists(path(key));
            } catch (IOException ignored) {
            }
          }
        });
  }

  public synchronized void close() {
    if (closed) return;
    closed = true;
    for (var future : pending.values()) future.cancel(true);
    pending.clear();
    retries.clear();
    executor.shutdownNow();
    fetcher.close();
  }
}
