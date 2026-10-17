package org.javity.ecs.artemis;

import com.artemis.*;
import com.artemis.utils.IntBag;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import org.javity.core.*;

/** Repeatable CPU benchmark for the native ECS, GameObject data path, and the bridge. */
public final class ArtemisBridgeBenchmark {
  private static final long SEED = 0x4a61766974794543L;
  private static final float DT = 1f / 60f;
  private static final int ENTITIES = 10_000, WARMUP = 100, MEASURED = 300, REPS = 3;

  public static final class CoreMotion extends org.javity.core.Component { float x, velocity; }
  public static final class Motion extends com.artemis.Component { float x, velocity; }
  private static final class CoreMover extends Game {
    final List<CoreMotion> movers = new ArrayList<>();
    long updates;
    @Override public void onUpdate(float delta) { for (CoreMotion mover : movers) { mover.x += mover.velocity * delta; updates++; } }
  }
  private static final class NativeMover extends BaseSystem {
    ComponentMapper<Motion> motion;
    long updates;
    @Override protected void processSystem() {
      IntBag entities = getWorld().getAspectSubscriptionManager().get(Aspect.all(Motion.class)).getEntities();
      for (int i = 0; i < entities.size(); i++) {
        Motion value = motion.get(entities.get(i)); value.x += value.velocity * getWorld().getDelta(); updates++;
      }
    }
  }
  private static final class MotionProjection extends ComponentProjection<CoreMotion, Motion> {
    MotionProjection() { super(CoreMotion.class, Motion.class, ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED); }
    @Override protected void copyToEntity(CoreMotion source, Motion target) { target.velocity = source.velocity; }
  }
  private record Result(String name, long updates, int linked, long wallNanos, long allocatedBytes, double checksum) {}

  private ArtemisBridgeBenchmark() {}
  public static void main(String[] args) throws Exception {
    if (args.length != 2) throw new IllegalArgumentException("expected <source-commit> <report-path>");
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) throw new IllegalStateException("Thread allocation measurement unavailable");
    bean.setThreadAllocatedMemoryEnabled(true);
    List<Result> results = new ArrayList<>();
    for (String mode : List.of("native-ecs", "game-object", "mixed-sync-on", "mixed-sync-off")) {
      long[] times = new long[REPS], allocations = new long[REPS];
      long expectedUpdates = 0; int linkedCount = 0; double checksum = 0;
      for (int rep = 0; rep < REPS; rep++) {
        Run run = new Run(mode);
        linkedCount = run.linked;
        for (int i = 0; i < WARMUP; i++) run.step();
        long threadId = Thread.currentThread().getId();
        long allocatedBefore = bean.getThreadAllocatedBytes(threadId);
        long started = System.nanoTime();
        for (int i = 0; i < MEASURED; i++) run.step();
        times[rep] = System.nanoTime() - started;
        allocations[rep] = bean.getThreadAllocatedBytes(threadId) - allocatedBefore;
        expectedUpdates = run.updates;
        checksum = run.checksum();
        if (expectedUpdates != (long) ENTITIES * (WARMUP + MEASURED)) throw new AssertionError(mode + " update count " + expectedUpdates);
        run.close();
      }
      Arrays.sort(times); Arrays.sort(allocations);
      results.add(new Result(mode, expectedUpdates, linkedCount, times[1], allocations[1], checksum));
    }
    StringBuilder json = new StringBuilder("{\n  \"sourceCommit\": \"").append(escape(args[0])).append("\",\n")
        .append("  \"seed\": ").append(SEED).append(",\n  \"deltaSeconds\": ").append(1.0 / 60.0)
        .append(",\n  \"runtime\": {\"javaVersion\": \"").append(escape(System.getProperty("java.runtime.version")))
        .append("\", \"vm\": \"").append(escape(System.getProperty("java.vm.name")))
        .append("\", \"os\": \"").append(escape(System.getProperty("os.name")))
        .append("\", \"architecture\": \"").append(escape(System.getProperty("os.arch")))
        .append("\", \"availableProcessors\": ").append(Runtime.getRuntime().availableProcessors()).append("}")
        .append(",\n  \"entities\": ").append(ENTITIES).append(",\n  \"warmupSteps\": ").append(WARMUP)
        .append(",\n  \"measuredSteps\": ").append(MEASURED).append(",\n  \"repetitions\": ").append(REPS)
        .append(",\n  \"cases\": [\n");
    for (int i = 0; i < results.size(); i++) {
      Result r = results.get(i);
      json.append("    {\"case\": \"").append(r.name).append("\", \"updates\": ").append(r.updates)
          .append(", \"linkedEntities\": ").append(r.linked).append(", \"medianWallNanos\": ").append(r.wallNanos)
          .append(", \"medianOwnerThreadAllocatedBytes\": ").append(r.allocatedBytes)
          .append(", \"checksum\": ").append(r.checksum).append("}").append(i + 1 == results.size() ? "\n" : ",\n");
    }
    json.append("  ],\n  \"interpretation\": \"Workload-specific measurements; bridge mirroring adds code and allocation overhead. No universal speed ranking or target improvement is implied.\"\n}\n");
    Path report = Path.of(args[1]); Files.createDirectories(report.getParent()); Files.writeString(report, json);
    System.out.print(json);
  }

  private static final class Run implements AutoCloseable {
    final String mode; final CoreMover game; final World world; final NativeMover nativeMover;
    final List<CoreMotion> allCore = new ArrayList<>(); final List<EntityLink> links = new ArrayList<>();
    final int linked; long updates;
    Run(String mode) {
      this.mode = mode;
      int ecsCount = mode.equals("native-ecs") ? ENTITIES : mode.startsWith("mixed-") ? ENTITIES / 10 : 0;
      linked = mode.startsWith("mixed-") ? ecsCount : 0;
      boolean usesEcs = !mode.equals("game-object");
      nativeMover = usesEcs ? new NativeMover() : null;
      world = usesEcs ? new World(new WorldConfigurationBuilder().with(nativeMover).build()) : null;
      game = new CoreMover();
      ArtemisSystem bridge = usesEcs ? game.addSystem(new ArtemisSystem(world)) : null;
      Random random = new Random(SEED);
      int coreCount = ENTITIES - ecsCount;
      for (int i = 0; i < coreCount; i++) {
        GameObject object = game.createObject("core-" + i);
        CoreMotion motion = object.addComponent(new CoreMotion()); motion.x = random.nextFloat(); motion.velocity = random.nextFloat() - .5f;
        game.movers.add(motion); allCore.add(motion);
      }
      for (int i = 0; i < ecsCount; i++) {
        float x = random.nextFloat(), velocity = random.nextFloat() - .5f;
        if (mode.equals("native-ecs")) {
          Entity entity = world.createEntity(); Motion motion = entity.edit().create(Motion.class); motion.x = x; motion.velocity = velocity;
        } else {
          GameObject object = game.createObject("linked-" + i);
          CoreMotion source = object.addComponent(new CoreMotion()); source.x = x; source.velocity = velocity;
          EntityLink link = bridge.bind(object, world.createEntity()); link.addProjection(new MotionProjection());
          if (mode.equals("mixed-sync-off")) link.setSynchronizationEnabled(false);
          links.add(link);
          Motion nativeMotion = world.edit(link.entity()).create(Motion.class); nativeMotion.x = x; nativeMotion.velocity = velocity;
        }
      }
      game.start();
    }
    void step() {
      long before = game.updates + (nativeMover == null ? 0 : nativeMover.updates);
      game.step(DT);
      updates += game.updates + (nativeMover == null ? 0 : nativeMover.updates) - before;
    }
    double checksum() {
      double sum = 0;
      for (CoreMotion motion : allCore) sum += motion.x;
      if (mode.equals("native-ecs")) {
        IntBag entities = world.getAspectSubscriptionManager().get(Aspect.all(Motion.class)).getEntities();
        for (int i = 0; i < entities.size(); i++) sum += world.getMapper(Motion.class).get(entities.get(i)).x;
      } else if (!links.isEmpty()) {
        ComponentMapper<Motion> mapper = world.getMapper(Motion.class);
        for (EntityLink link : links) sum += mapper.get(link.entity()).x;
      }
      return sum;
    }
    @Override public void close() { game.close(); }
  }
  private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
