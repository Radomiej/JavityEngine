package org.javity.ui.flex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.javity.ui.yoga.YogaBackends;
import org.junit.Test;

public final class YogaNativeGuardTest {
  @Test
  public void defaultProviderCreatesPortableOpaqueNodes() {
    var backend = YogaBackends.createDefault();
    assertEquals("org.javity.ui.yoga.lwjgl.LwjglYogaBackend", backend.getClass().getName());
    var node = backend.createNode();
    try {
      node.setWidth(24);
      node.setHeight(12);
      node.calculateLayout(24, 12);
      assertEquals(24, node.layoutWidth(), .001f);
      assertEquals(12, node.layoutHeight(), .001f);
    } finally {
      node.freeNode();
    }
  }

  @Test
  public void invalidNativeCallsFailInJavaWithoutAbortingTheProcess() throws Exception {
    for (String probe :
        List.of(
            "unmeasured-dirty",
            "border-all",
            "border-horizontal",
            "border-vertical",
            "padding-all",
            "padding-horizontal",
            "padding-vertical",
            "margin-all",
            "margin-horizontal",
            "margin-vertical")) {
      ProbeResult result = run(probe);
      assertEquals(probe, 0, result.exitCode());
      assertTrue(probe, result.output().contains("guarded"));
    }
  }

  @Test
  public void measuredDirtyAndPhysicalLayoutEdgesRemainValid() throws Exception {
    for (String probe : List.of("measured-dirty", "physical-layout-edges")) {
      ProbeResult result = run(probe);
      assertEquals(probe, 0, result.exitCode());
      assertTrue(probe, result.output().contains("valid"));
    }
  }

  private static ProbeResult run(String operation) throws Exception {
    Path directory = Files.createTempDirectory("yoga-native-guard-");
    try {
      String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
      Process process =
          new ProcessBuilder(
                  java,
                  "--enable-native-access=ALL-UNNAMED",
                  "-XX:ErrorFile=" + directory.resolve("hs_err_pid%p.log"),
                  "-cp",
                  absoluteClassPath(),
                  YogaNativeGuardProbe.class.getName(),
                  operation)
              .directory(directory.toFile())
              .redirectErrorStream(true)
              .start();
      if (!process.waitFor(20, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new AssertionError("Yoga guard probe timed out: " + operation);
      }
      return new ProbeResult(
          process.exitValue(), new String(process.getInputStream().readAllBytes()));
    } finally {
      try (var files = Files.walk(directory)) {
        files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> deleteQuietly(path));
      }
    }
  }

  private static String absoluteClassPath() {
    String separator = System.getProperty("path.separator");
    return java.util.Arrays.stream(System.getProperty("java.class.path").split(separator))
        .map(entry -> Path.of(entry).toAbsolutePath().toString())
        .collect(java.util.stream.Collectors.joining(separator));
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Child-process diagnostics may be held briefly by the OS.
    }
  }

  private record ProbeResult(int exitCode, String output) {}
}
