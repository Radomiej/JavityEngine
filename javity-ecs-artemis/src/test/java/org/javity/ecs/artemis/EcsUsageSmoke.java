package org.javity.ecs.artemis;

import com.artemis.*;
import com.artemis.utils.Bag;
import com.artemis.utils.IntBag;
import java.nio.file.*;
import java.util.*;
import org.javity.core.*;
import org.javity.serialization.*;

/** CPU-only executable usage proof; intentionally lives in the test source set. */
public final class EcsUsageSmoke {
  public static final class Velocity extends org.javity.core.Component { public float dx; }
  public static final class Position extends org.javity.core.Component { public float x; }
  public static final class Motion extends com.artemis.Component { public float dx; }
  public static final class Location extends com.artemis.Component { public float x; }
  public static final class Stats { int processCalls, queried; }

  private static final class MotionProjection extends ComponentProjection<Velocity, Motion> {
    MotionProjection() { super(Velocity.class, Motion.class, ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED); }
    @Override protected void copyToEntity(Velocity source, Motion target) { target.dx = source.dx; }
  }

  private static final class NativeMover extends BaseSystem {
    private ComponentMapper<Motion> motion;
    private ComponentMapper<Location> location;
    private final Stats stats;
    NativeMover(Stats stats) { this.stats = stats; }
    @Override protected void processSystem() {
      IntBag entities = getWorld().getAspectSubscriptionManager().get(Aspect.all(Motion.class, Location.class)).getEntities();
      stats.queried += entities.size();
      for (int i = 0; i < entities.size(); i++) {
        int id = entities.get(i);
        location.get(id).x += motion.get(id).dx * world.getDelta();
      }
      stats.processCalls++;
    }
  }

  private EcsUsageSmoke() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 2) throw new IllegalArgumentException("expected <source-commit> <report-path>");
    String sourceCommit = args[0];
    Stats stats = new Stats();
    World world = new World(new WorldConfigurationBuilder().with(new NativeMover(stats)).build());

    // Native-only entity is queryable without a GameObject.
    Entity pure = world.createEntity(new ArchetypeBuilder().add(Motion.class).add(Location.class).build(world));
    world.process(); // Materialize the native archetype before transferring World ownership.
    world.getMapper(Motion.class).get(pure.getId()).dx = 2;
    world.getMapper(Location.class).get(pure.getId()).x = 1;
    stats.processCalls = 0; stats.queried = 0;

    Game game = new Game();
    ArtemisSystem ecs = game.addSystem(new ArtemisSystem(world, EcsPhase.BEFORE_WORLD));

    Velocity velocity = new Velocity(); velocity.dx = 3;
    EntityLink linked = ecs.createLinked("linked-mover", object -> {
      object.addComponent(velocity);
      object.addComponent(new Position());
    }, edit -> { edit.create(Location.class).x = 4; });
    linked.addProjection(new MotionProjection());

    List<EntityLink> prefabLinks = LinkedPrefabFactory.instantiate(ecs,
        new Prefab("{\"version\":1,\"objects\":[{\"name\":\"prefab-mover\",\"components\":[{\"type\":\"velocity\",\"data\":{\"dx\":5}}]}]}"),
        new SceneJson().register("velocity", Velocity.class, value -> {
          var jsonValue = SceneJson.object(); jsonValue.addChild("dx", new com.badlogic.gdx.utils.JsonValue(value.dx)); return jsonValue;
        }, data -> { Velocity value = new Velocity(); value.dx = data.getFloat("dx"); return value; }),
        edit -> edit.create(Location.class).x = 8);
    prefabLinks.get(0).addProjection(new MotionProjection());
    game.start();
    Bag<BaseSystem> nativeSystems = new Bag<>(BaseSystem.class);
    for (BaseSystem system : world.getSystems()) nativeSystems.add(system);
    boolean moverFound = false;
    for (int i = 0; i < nativeSystems.size(); i++) moverFound |= nativeSystems.get(i) instanceof NativeMover;
    require(moverFound, "native World system Bag omitted the configured mover");
    game.step(1f / 60f);
    require(close(world.getMapper(Location.class).get(pure.getId()).x, 1 + 2f / 60f), "native-only mover did not update");
    require(close(world.getMapper(Location.class).get(linked.entity()).x, 4 + 3f / 60f), "linked mover did not update");
    require(close(world.getMapper(Location.class).get(prefabLinks.get(0).entity()).x, 8 + 5f / 60f), "prefab mover did not update");
    require(stats.processCalls == 1 && stats.queried == 3, "native system/aspect query counts differ");

    int prefabId = prefabLinks.get(0).entity();
    prefabLinks.get(0).setSynchronizationEnabled(false);
    prefabLinks.get(0).object().getComponent(Velocity.class).dx = 7;
    game.step(1f / 60f);
    require(world.getMapper(Motion.class).get(prefabId).dx == 5, "sync-off unexpectedly copied core data");
    prefabLinks.get(0).setSynchronizationEnabled(true);
    game.step(1f / 60f);
    require(world.getMapper(Motion.class).get(prefabId).dx == 7, "sync resume did not reconcile current data");
    require(stats.processCalls == 3 && stats.queried == 9, "World process/query totals differ");
    int linksCreated = 1 + prefabLinks.size();
    linked.close(); prefabLinks.get(0).close();
    int linksClosed = (linked.isValid() ? 0 : 1) + (prefabLinks.get(0).isValid() ? 0 : 1);
    require(linksClosed == linksCreated, "explicit link close did not invalidate every handle");
    game.close();

    String json = "{\n" +
        "  \"sourceCommit\": \"" + escape(sourceCommit) + "\",\n" +
        "  \"deltaSeconds\": " + (1.0 / 60.0) + ",\n" +
        "  \"nativeProcessCalls\": " + stats.processCalls + ",\n" +
        "  \"aspectQueryEntities\": " + stats.queried + ",\n" +
        "  \"linksCreated\": " + linksCreated + ",\n" +
        "  \"linksClosed\": " + linksClosed + ",\n" +
        "  \"prefabInstances\": 1,\n" +
        "  \"syncTransitions\": 2,\n" +
        "  \"nativeOnlyEntities\": 1,\n" +
        "  \"nativeSystemsInBag\": " + nativeSystems.size() + ",\n" +
        "  \"glScreenshotClaim\": false\n" +
        "}\n";
    Path report = Path.of(args[1]);
    Files.createDirectories(report.getParent());
    Files.writeString(report, json);
    System.out.print(json);
  }

  private static boolean close(float actual, float expected) { return Math.abs(actual - expected) < 0.00001f; }
  private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
  private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
