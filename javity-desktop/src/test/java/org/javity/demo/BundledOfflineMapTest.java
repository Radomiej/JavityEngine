package org.javity.demo;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class BundledOfflineMapTest {
  private static final String GORZOW = "org/javity/demo/maplibre-gorzow/";

  @Test
  public void opensAndOwnsAnOfflineCopyOfTheBundledGorzowPackage() throws Exception {
    Path copiedRoot;
    try (BundledOfflineMap bundled = BundledOfflineMap.open(GORZOW)) {
      copiedRoot = bundled.mapPackage().root();
      assertTrue(Files.isRegularFile(copiedRoot.resolve("gorzow.mbtiles")));
      assertTrue(Files.isRegularFile(copiedRoot.resolve("map.json")));
      assertTrue(bundled.mapPackage().styleNames().contains("day"));
      assertTrue(bundled.mapPackage().styleNames().contains("night"));
      assertTrue(bundled.mapPackage().resolvedStyle("day").contains("gorzow.mbtiles"));
      bundled.close();
      bundled.close();
    }
    assertFalse("close must remove the copied package", Files.exists(copiedRoot));
  }

  @Test
  public void rejectsUnsafeIndexedPathsBeforeCopyingThem() throws Exception {
    Path resources = Files.createTempDirectory("bundled-map-unsafe-test");
    String prefix = "synthetic/";
    writeResource(
        resources,
        prefix + "fixture-source.json",
        "{\"sha256\":{\"../escape\":\"" + "0".repeat(64) + "\"}}");
    Set<Path> before = ownedTempDirectories();
    try (URLClassLoader loader = loader(resources)) {
      IllegalArgumentException error =
          assertThrows(
              IllegalArgumentException.class, () -> BundledOfflineMap.open(prefix, loader));
      assertTrue(error.getMessage().toLowerCase().contains("path"));
    } finally {
      deleteTree(resources);
    }
    assertEquals(
        "invalid paths must not leave an owned copy behind", before, ownedTempDirectories());
  }

  @Test
  public void removesPartialCopyWhenAnIndexedResourceIsMissing() throws Exception {
    Path resources = Files.createTempDirectory("bundled-map-missing-test");
    String prefix = "synthetic/";
    writeResource(resources, prefix + "fixture-source.json", index("missing.bin", "0".repeat(64)));
    Set<Path> before = ownedTempDirectories();
    try (URLClassLoader loader = loader(resources)) {
      assertThrows(IOException.class, () -> BundledOfflineMap.open(prefix, loader));
    } finally {
      deleteTree(resources);
    }
    assertEquals("missing resources must clean the partial copy", before, ownedTempDirectories());
  }

  @Test
  public void removesPartialCopyWhenAnIndexedResourceHashDoesNotMatch() throws Exception {
    Path resources = Files.createTempDirectory("bundled-map-hash-test");
    String prefix = "synthetic/";
    writeResource(resources, prefix + "fixture-source.json", index("payload.bin", "0".repeat(64)));
    writeResource(resources, prefix + "payload.bin", "wrong bytes");
    Set<Path> before = ownedTempDirectories();
    try (URLClassLoader loader = loader(resources)) {
      assertThrows(IOException.class, () -> BundledOfflineMap.open(prefix, loader));
    } finally {
      deleteTree(resources);
    }
    assertEquals("hash failures must clean the partial copy", before, ownedTempDirectories());
  }

  private static String index(String path, String hash) {
    return "{\"sha256\":{\"" + path + "\":\"" + hash + "\"}}";
  }

  private static URLClassLoader loader(Path root) throws IOException {
    URL url = root.toUri().toURL();
    return new URLClassLoader(new URL[] {url}, BundledOfflineMapTest.class.getClassLoader());
  }

  private static void writeResource(Path root, String name, String value) throws IOException {
    Path path = root.resolve(name);
    Files.createDirectories(path.getParent());
    Files.writeString(path, value, StandardCharsets.UTF_8);
  }

  private static Set<Path> ownedTempDirectories() throws IOException {
    Set<Path> result = new HashSet<>();
    try (var entries = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
      entries
          .filter(p -> p.getFileName().toString().startsWith("javity bundled map "))
          .forEach(result::add);
    }
    return result;
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) return;
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
        Files.delete(path);
    }
  }
}
