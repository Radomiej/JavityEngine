package org.javity.map;

import static org.junit.Assert.*;

import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class AsyncTilesTest {
  private byte[] await(TileByteCache cache, TileKey key) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    byte[] result;
    do {
      result = cache.poll(key);
      if (result != null) return result;
      Thread.sleep(10);
    } while (System.nanoTime() < deadline);
    throw new AssertionError("Tile not ready");
  }

  @Test
  public void deduplicatesBoundsPendingAndReusesDisk() throws Exception {
    Path root = Files.createTempDirectory("javity-tiles");
    AtomicInteger loads = new AtomicInteger();
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    TileFetcher fetcher =
        key -> {
          loads.incrementAndGet();
          entered.countDown();
          release.await(5, TimeUnit.SECONDS);
          return new byte[] {1, 2, 3};
        };
    TileKey key = new TileKey(2, 0, 0);
    try (TileByteCache cache = new TileByteCache(fetcher, root, 1, 1, 2, 1000)) {
      assertNull(cache.poll(key));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertNull(cache.poll(key));
      assertNull(cache.poll(new TileKey(2, 1, 0)));
      assertEquals(1, cache.pendingCount());
      release.countDown();
      assertArrayEquals(new byte[] {1, 2, 3}, await(cache, key));
      assertEquals(1, loads.get());
    }
    try (TileByteCache cache = new TileByteCache(fetcher, root, 1, 2, 2, 1000)) {
      assertArrayEquals(new byte[] {1, 2, 3}, await(cache, key));
      assertEquals(1, loads.get());
    }
  }

  @Test
  public void missingTilesBackOffAndCloseCancels() throws Exception {
    AtomicInteger loads = new AtomicInteger();
    TileKey key = new TileKey(0, 0, 0);
    TileByteCache cache =
        new TileByteCache(
            k -> {
              loads.incrementAndGet();
              return null;
            },
            Files.createTempDirectory("javity-missing"),
            1,
            2,
            2,
            60000);
    cache.poll(key);
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (cache.pendingCount() != 0 && System.nanoTime() < deadline) {
      Thread.sleep(10);
      cache.poll(key);
    }
    for (int i = 0; i < 20; i++) assertNull(cache.poll(key));
    assertEquals(1, loads.get());
    assertNotNull(cache.lastFailure(key));
    cache.close();
    assertThrows(IllegalStateException.class, () -> cache.poll(key));
  }

  @Test
  public void httpUsesConfiguredPathIdentityAndAttribution() throws Exception {
    var server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/1/0/1.png",
        exchange -> {
          assertEquals("Javity-test", exchange.getRequestHeaders().getFirst("User-Agent"));
          byte[] b = {4, 5};
          exchange.sendResponseHeaders(200, b.length);
          try (var out = exchange.getResponseBody()) {
            out.write(b);
          }
        });
    server.start();
    try (HttpTileFetcher http =
        new HttpTileFetcher(
            "http://127.0.0.1:" + server.getAddress().getPort() + "/{z}/{x}/{y}.png",
            "Javity-test",
            "Local test tiles")) {
      assertArrayEquals(new byte[] {4, 5}, http.fetch(new TileKey(1, 0, 1)));
      assertNull(http.fetch(new TileKey(1, 1, 0)));
      assertEquals("Local test tiles", http.attribution());
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void oversizedHttpBodyIsRejected() throws Exception {
    var server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/0/0/0.png",
        exchange -> {
          byte[] bytes = new byte[TileByteCache.MAX_BYTES + 1];
          exchange.sendResponseHeaders(200, bytes.length);
          try (var out = exchange.getResponseBody()) {
            out.write(bytes);
          } catch (java.io.IOException ignored) {
          }
        });
    server.start();
    try (var http =
        new HttpTileFetcher(
            "http://127.0.0.1:" + server.getAddress().getPort() + "/{z}/{x}/{y}.png",
            "Javity-test",
            "Test")) {
      assertThrows(java.io.IOException.class, () -> http.fetch(new TileKey(0, 0, 0)));
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void diskCacheEvictsToConfiguredCapacity() throws Exception {
    Path root = Files.createTempDirectory("javity-bounded-disk");
    try (var cache = new TileByteCache(k -> new byte[] {1}, root, 1, 2, 2, 1000)) {
      await(cache, new TileKey(2, 0, 0));
      await(cache, new TileKey(2, 1, 0));
      await(cache, new TileKey(2, 2, 0));
      try (var files = Files.list(root)) {
        assertEquals(2, files.filter(p -> p.toString().endsWith(".tile")).count());
      }
    }
  }

  @Test
  public void closeInterruptsActiveDownload() throws Exception {
    CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1);
    var cache =
        new TileByteCache(
            k -> {
              started.countDown();
              try {
                new CountDownLatch(1).await();
                return null;
              } catch (InterruptedException e) {
                interrupted.countDown();
                throw e;
              }
            },
            Files.createTempDirectory("javity-cancel"),
            1,
            1,
            1,
            1000);
    try {
      cache.poll(new TileKey(0, 0, 0));
      assertTrue(started.await(5, TimeUnit.SECONDS));
      cache.close();
      assertTrue(interrupted.await(5, TimeUnit.SECONDS));
      assertEquals(0, cache.pendingCount());
    } finally {
      cache.close();
    }
  }
}
