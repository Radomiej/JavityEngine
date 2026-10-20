package org.javity.demo;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.render.lighting.*;

/**
 * Opt-in, CPU-only allocation probe. No backend or GL context is created. Measures the complete
 * snapshot call, including copying scene queries; it does not claim a zero-allocation scene query.
 * Run identical arguments/JDK before and after changes; timing is descriptive, not a rendering
 * gate.
 */
public final class LightSnapshotBenchmark {
  private static volatile double sink;

  public static void main(String[] args) {
    int warmup = 10000, iterations = 20000, samples = 5;
    for (String arg : args) {
      if (arg.startsWith("--warmup=")) warmup = positive(arg, "--warmup=");
      else if (arg.startsWith("--iterations=")) iterations = positive(arg, "--iterations=");
      else if (arg.startsWith("--samples=")) samples = positive(arg, "--samples=");
      else throw new IllegalArgumentException("Unknown argument: " + arg);
    }
    if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean)
        || !bean.isThreadAllocatedMemorySupported())
      throw new UnsupportedOperationException("Thread allocation measurement is unavailable");
    if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId();
    if (bean.getThreadAllocatedBytes(thread) < 0)
      throw new IllegalStateException("Thread allocation counter is unavailable");
    StringBuilder json = new StringBuilder();
    json.append("{\"benchmark\":\"light-snapshot-cpu\",\"javaVersion\":\"")
        .append(System.getProperty("java.version"))
        .append("\",\"warmupIterations\":")
        .append(warmup)
        .append(",\"measuredIterations\":")
        .append(iterations)
        .append(",\"samples\":")
        .append(samples)
        .append(",\"scope\":\"snapshot including scene query copies; no GL\",\"results\":[");
    String[] names = {"empty", "default", "dense", "overflow"};
    int[][] layouts = {
      {0, 0, 0, 0, 2, 4, 2},
      {8, 2, 4, 2, 2, 4, 2},
      {16, 16, 64, 16, 16, 64, 16},
      {16, 16, 64, 16, 2, 4, 2}
    };
    for (int workload = 0; workload < layouts.length; workload++) {
      int[] layout = layouts[workload];
      try (Game game = new Game()) {
        RenderEnvironment3D scene = game.addSystem(new RenderEnvironment3D());
        scene.setAmbient(.1f, .2f, .3f);
        scene.setFog(.3f, .4f, .5f, 10, 100);
        GameObject[] owners = new GameObject[layout[0]];
        for (int i = 0; i < owners.length; i++) {
          owners[i] = game.createObject("benchmark-light-" + i);
          owners[i].addComponent(new Transform3D()).position.set(i % 4, i / 4, -i);
        }
        for (int i = 0; i < layout[1]; i++)
          owners[i % owners.length].addComponent(new DirectionalLight3D());
        for (int i = 0; i < layout[2]; i++)
          owners[i % owners.length].addComponent(new PointLight3D()).setRange(10 + i);
        for (int i = 0; i < layout[3]; i++)
          owners[i % owners.length].addComponent(new SpotLight3D()).setRange(20 + i);
        game.start();
        run(scene, layout, warmup);
        for (int sample = 0; sample < samples; sample++) {
          long allocatedBefore = bean.getThreadAllocatedBytes(thread);
          long start = System.nanoTime();
          run(scene, layout, iterations);
          long nanos = System.nanoTime() - start;
          long allocated = bean.getThreadAllocatedBytes(thread) - allocatedBefore;
          if (workload != 0 || sample != 0) json.append(',');
          json.append(
              String.format(
                  Locale.ROOT,
                  "{\"workload\":\"%s\",\"owners\":%d,\"lights\":[%d,%d,%d],"
                      + "\"capacities\":[%d,%d,%d],\"sample\":%d,\"allocatedBytes\":%d,"
                      + "\"bytesPerSnapshot\":%.3f,\"nanosPerSnapshot\":%.3f}",
                  names[workload],
                  layout[0],
                  layout[1],
                  layout[2],
                  layout[3],
                  layout[4],
                  layout[5],
                  layout[6],
                  sample,
                  allocated,
                  (double) allocated / iterations,
                  (double) nanos / iterations));
        }
      }
    }
    System.out.println(json.append("],\"checksum\":").append(sink).append('}'));
  }

  private static int positive(String argument, String prefix) {
    int value = Integer.parseInt(argument.substring(prefix.length()));
    if (value <= 0) throw new IllegalArgumentException(prefix + " must be positive");
    return value;
  }

  private static void run(RenderEnvironment3D scene, int[] layout, int count) {
    double checksum = 0;
    for (int i = 0; i < count; i++) {
      scene.setAmbient((i & 255) / 256f, .2f, .3f);
      Environment env = scene.snapshot(layout[4], layout[5], layout[6]);
      var ranges = env.get(SceneLightingAttribute.class, SceneLightingAttribute.Type);
      checksum += ranges.pointRange(0) + ranges.spotRange(0) + ranges.fogFar();
      var points = env.get(PointLightsAttribute.class, PointLightsAttribute.Type);
      if (points != null) checksum += points.lights.first().position.x + points.lights.size;
      checksum += scene.overflow().point();
    }
    sink = checksum;
  }
}
