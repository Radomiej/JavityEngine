package org.javity.demo;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.javity.maplibre.OfflineMapPackage;

/** Copies a checksummed classpath map package to a private directory for local-file consumers. */
public final class BundledOfflineMap implements AutoCloseable {
  private static final String INDEX_NAME = "fixture-source.json";
  private static final long MAX_INDEX_BYTES = 2L * 1024 * 1024;
  private static final long MAX_RESOURCE_BYTES = 32L * 1024 * 1024;
  private static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
  private static final int SHA256_HEX_LENGTH = 64;

  private final Path ownedRoot;
  private final OfflineMapPackage mapPackage;

  private BundledOfflineMap(Path ownedRoot, OfflineMapPackage mapPackage) {
    this.ownedRoot = ownedRoot;
    this.mapPackage = mapPackage;
  }

  /** Opens the indexed map resources visible to this class's classloader. */
  public static BundledOfflineMap open(String resourcePrefix) throws IOException {
    return open(resourcePrefix, BundledOfflineMap.class.getClassLoader());
  }

  /** Opens indexed resources from a supplied classloader, primarily useful for isolated tests. */
  public static BundledOfflineMap open(String resourcePrefix, ClassLoader classLoader)
      throws IOException {
    validatePrefix(resourcePrefix);
    if (classLoader == null) throw new IllegalArgumentException("Classloader is required");

    byte[] indexBytes =
        readBoundedResource(classLoader, resourcePrefix + INDEX_NAME, MAX_INDEX_BYTES);
    List<IndexedResource> indexedResources = parseIndex(indexBytes);
    long indexedTotal = indexBytes.length;
    for (IndexedResource resource : indexedResources) {
      if (resource.path.equals(INDEX_NAME))
        throw new IllegalArgumentException("The resource index cannot index itself: " + INDEX_NAME);
    }

    Path root = Files.createTempDirectory("javity bundled map ");
    try {
      Files.write(root.resolve(INDEX_NAME), indexBytes);
      for (IndexedResource resource : indexedResources) {
        Path destination = safeDestination(root, resource.path);
        Files.createDirectories(destination.getParent());
        long copied =
            copyAndVerify(
                classLoader, resourcePrefix + resource.path, destination, resource.sha256);
        indexedTotal = Math.addExact(indexedTotal, copied);
        if (indexedTotal > MAX_TOTAL_BYTES)
          throw new IOException("Bundled map exceeds the 64 MiB total resource limit");
      }
      return new BundledOfflineMap(root, OfflineMapPackage.open(root));
    } catch (IOException | RuntimeException | Error failure) {
      try {
        deleteTree(root);
      } catch (IOException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
  }

  public OfflineMapPackage mapPackage() {
    return mapPackage;
  }

  /** Deletes the private package copy. If deletion fails, a later close call can retry it. */
  @Override
  public void close() throws IOException {
    deleteTree(ownedRoot);
  }

  private static List<IndexedResource> parseIndex(byte[] bytes) {
    final JsonValue index;
    try {
      index = new JsonReader().parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid bundled map resource index", e);
    }
    if (!index.isObject())
      throw new IllegalArgumentException("Bundled map resource index must be an object");
    JsonValue hashes = index.get("sha256");
    if (hashes == null || !hashes.isObject() || hashes.size == 0)
      throw new IllegalArgumentException(
          "Bundled map resource index must contain a non-empty sha256 object");

    List<IndexedResource> result = new ArrayList<>(hashes.size);
    Set<String> seen = new HashSet<>();
    for (JsonValue entry = hashes.child; entry != null; entry = entry.next) {
      String path = entry.name;
      validateResourcePath(path);
      if (!seen.add(path))
        throw new IllegalArgumentException("Duplicate indexed resource path: " + path);
      if (!entry.isString())
        throw new IllegalArgumentException("SHA-256 digest must be a string for: " + path);
      String hash = entry.asString();
      if (hash.length() != SHA256_HEX_LENGTH || !hash.matches("[0-9a-fA-F]{64}"))
        throw new IllegalArgumentException("Invalid SHA-256 digest for: " + path);
      result.add(new IndexedResource(path, hash.toLowerCase(java.util.Locale.ROOT)));
    }
    return result;
  }

  private static void validatePrefix(String prefix) {
    if (prefix == null || prefix.isEmpty() || !prefix.endsWith("/"))
      throw new IllegalArgumentException("Resource prefix must be non-empty and end with '/'");
    if (prefix.startsWith("/") || prefix.indexOf('\\') >= 0)
      throw new IllegalArgumentException("Resource prefix must be a relative classpath path");
    for (String segment : prefix.split("/")) {
      if (segment.equals(".") || segment.equals(".."))
        throw new IllegalArgumentException("Resource prefix contains an unsafe path segment");
    }
  }

  private static void validateResourcePath(String value) {
    if (value == null || value.isEmpty() || value.indexOf('\\') >= 0 || value.startsWith("/"))
      throw new IllegalArgumentException("Unsafe bundled map resource path: " + value);
    for (String segment : value.split("/", -1)) {
      if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
        throw new IllegalArgumentException("Unsafe bundled map resource path: " + value);
    }
    Path path = Path.of(value);
    if (path.isAbsolute() || path.normalize().startsWith(".."))
      throw new IllegalArgumentException("Unsafe bundled map resource path: " + value);
  }

  private static Path safeDestination(Path root, String relative) throws IOException {
    Path destination = root.resolve(Path.of(relative)).normalize();
    if (!destination.startsWith(root))
      throw new IOException(
          "Indexed map resource escapes its private package directory: " + relative);
    return destination;
  }

  private static byte[] readBoundedResource(ClassLoader loader, String name, long limit)
      throws IOException {
    try (InputStream input = loader.getResourceAsStream(name)) {
      if (input == null) throw new IOException("Missing bundled map resource: " + name);
      byte[] bytes = input.readNBytes(Math.toIntExact(limit + 1));
      if (bytes.length > limit) throw new IOException("Bundled map index exceeds the 2 MiB limit");
      return bytes;
    }
  }

  private static long copyAndVerify(
      ClassLoader loader, String name, Path destination, String expectedHash) throws IOException {
    MessageDigest digest = sha256();
    long count = 0;
    try (InputStream input = loader.getResourceAsStream(name)) {
      if (input == null) throw new IOException("Missing bundled map resource: " + name);
      try (OutputStream output = Files.newOutputStream(destination)) {
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
          count += read;
          if (count > MAX_RESOURCE_BYTES)
            throw new IOException("Bundled map resource exceeds the 32 MiB limit: " + name);
          digest.update(buffer, 0, read);
          output.write(buffer, 0, read);
        }
      }
    }
    if (!HexFormat.of().formatHex(digest.digest()).equals(expectedHash))
      throw new IOException("SHA-256 mismatch for bundled map resource: " + name);
    return count;
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError("Java runtime does not provide SHA-256", e);
    }
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) return;
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
    }
  }

  private record IndexedResource(String path, String sha256) {}
}
