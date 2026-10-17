package org.javity.script;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/** Polls one caller-selected development bundle and reloads it on the owning UI thread. */
public final class ScriptUiReloader implements AutoCloseable {
  public static final int MAX_SOURCE_BYTES = 4 * 1024 * 1024;

  public enum PollResult {
    UNCHANGED,
    RELOADED,
    ERROR,
    CLOSED
  }

  private final Thread ownerThread = Thread.currentThread();
  private final ScriptUiRuntime runtime;
  private final Path bundle;
  private String lastAttemptedDigest;
  private String lastError;
  private boolean closed;

  public ScriptUiReloader(ScriptUiRuntime runtime, Path bundle) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
    this.runtime.document();
    this.bundle = Objects.requireNonNull(bundle, "bundle").toAbsolutePath().normalize();
  }

  public PollResult poll() {
    checkOwner();
    if (closed) return PollResult.CLOSED;

    final byte[] bytes;
    try {
      bytes = readBoundedBundle();
    } catch (IOException failure) {
      return error(failure);
    } catch (RuntimeException failure) {
      return error(failure);
    }

    String digest = sha256(bytes);
    if (digest.equals(lastAttemptedDigest)) return PollResult.UNCHANGED;
    lastAttemptedDigest = digest;

    final String source;
    try {
      source = decodeUtf8(bytes);
    } catch (CharacterCodingException failure) {
      return error(new IOException("Bundle is not valid UTF-8", failure));
    }

    try {
      runtime.reload(source);
      lastError = null;
      return PollResult.RELOADED;
    } catch (RuntimeException failure) {
      return error(failure);
    }
  }

  public Optional<String> lastError() {
    checkOwner();
    return Optional.ofNullable(lastError);
  }

  @Override
  public void close() {
    checkOwner();
    closed = true;
  }

  private byte[] readBoundedBundle() throws IOException {
    BasicFileAttributes before = Files.readAttributes(bundle, BasicFileAttributes.class);
    if (!before.isRegularFile()) throw new IOException("Bundle is not a regular file: " + bundle);
    if (before.size() > MAX_SOURCE_BYTES)
      throw new IOException("Bundle exceeds the 4 MiB limit");

    byte[] bytes = readBytes((int) before.size());
    BasicFileAttributes middle = Files.readAttributes(bundle, BasicFileAttributes.class);
    if (!middle.isRegularFile() || middle.size() > MAX_SOURCE_BYTES)
      throw new IOException("Bundle changed while it was being read; retrying on next poll");
    byte[] confirmation = readBytes((int) middle.size());
    BasicFileAttributes after = Files.readAttributes(bundle, BasicFileAttributes.class);
    if (!after.isRegularFile()
        || before.size() != after.size()
        || before.size() != middle.size()
        || bytes.length != after.size()
        || !before.lastModifiedTime().equals(after.lastModifiedTime())
        || !middle.lastModifiedTime().equals(after.lastModifiedTime())
        || !Objects.equals(before.fileKey(), after.fileKey())
        || !Objects.equals(middle.fileKey(), after.fileKey())
        || !java.util.Arrays.equals(bytes, confirmation))
      throw new IOException("Bundle changed while it was being read; retrying on next poll");
    return bytes;
  }

  private byte[] readBytes(int expectedSize) throws IOException {
    if (expectedSize > MAX_SOURCE_BYTES) throw new IOException("Bundle exceeds the 4 MiB limit");
    try (InputStream input = Files.newInputStream(bundle);
        ByteArrayOutputStream output = new ByteArrayOutputStream(expectedSize)) {
      byte[] chunk = new byte[8192];
      int total = 0;
      for (int count; (count = input.read(chunk)) != -1; ) {
        total += count;
        if (total > MAX_SOURCE_BYTES) throw new IOException("Bundle exceeds the 4 MiB limit");
        output.write(chunk, 0, count);
      }
      return output.toByteArray();
    }
  }

  private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
    String source =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString();
    return source.startsWith("\uFEFF") ? source.substring(1) : source;
  }

  private PollResult error(RuntimeException failure) {
    lastError = diagnostic(failure);
    return PollResult.ERROR;
  }

  private PollResult error(IOException failure) {
    lastError = diagnostic(failure);
    return PollResult.ERROR;
  }

  private static String diagnostic(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError("SHA-256 is required by the Java platform", impossible);
    }
  }

  private void checkOwner() {
    if (Thread.currentThread() != ownerThread)
      throw new IllegalStateException("ScriptUiReloader must be used on its creating thread");
  }
}
