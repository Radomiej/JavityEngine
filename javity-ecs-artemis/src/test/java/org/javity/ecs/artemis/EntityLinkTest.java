package org.javity.ecs.artemis;

import static org.junit.Assert.*;

import com.artemis.*;
import org.javity.core.*;
import org.junit.Test;

public class EntityLinkTest {
  public static class SelectorValue extends org.javity.core.Component {}
  public static class SelectorNative extends com.artemis.Component {}
  private World world() { return new World(new WorldConfigurationBuilder().build()); }

  @Test public void nativeDeleteAndIdReuseCannotReviveAnOldHandle() {
    Game game = new Game();
    World world = world();
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("linked");
    EntityLink old = bridge.bind(object, world.createEntity());
    game.start();
    long generation = old.generation();
    int id = old.entity();

    world.delete(id);
    game.step(.01f);
    assertFalse(old.isValid());
    assertThrows(IllegalStateException.class, old::object);

    EntityLink replacement = bridge.bind(game.createObject("replacement"), world.createEntity());
    assertEquals("Artemis 2.3.0 recycles the just-deleted entity ID", id, replacement.entity());
    assertTrue(replacement.generation() > generation);
    old.close();
    assertTrue(replacement.isValid());
    assertNotNull(replacement.object());
    game.close();
  }

  @Test public void closeOnlyUnlinksAndDestroyedObjectIsReconciledOnUiWhilePaused() {
    Game game = new Game();
    World world = world();
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("linked");
    EntityLink link = bridge.bind(object, world.createEntity());
    link.close();
    assertFalse(link.isValid());
    assertFalse(world.getAspectSubscriptionManager().get(Aspect.all(GameObjectRef.class))
        .getEntities().size() > 0);

    EntityLink doomed = bridge.bind(object, world.createEntity());
    game.start();
    doomed.setSynchronizationEnabled(false);
    object.destroy();
    game.pause();
    game.step(.01f);
    assertFalse(doomed.isValid());
    game.close();
  }

  @Test public void rejectsForeignAndDuplicateBindingsAndDetectsRemovedReservedReference() {
    Game game = new Game(); Game otherGame = new Game();
    World world = world(); World otherWorld = world();
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject object = game.createObject("one");
    Entity entity = world.createEntity();
    EntityLink link = bridge.bind(object, entity);
    assertThrows(IllegalArgumentException.class, () -> bridge.bind(object, world.createEntity()));
    assertThrows(IllegalArgumentException.class, () -> bridge.bind(game.createObject("two"), entity));
    assertThrows(IllegalArgumentException.class, () -> bridge.bind(otherGame.createObject("foreign"), world.createEntity()));
    assertThrows(IllegalArgumentException.class, () -> bridge.bind(game.createObject("wrong world"), otherWorld.createEntity()));
    game.start();
    world.edit(entity.getId()).remove(GameObjectRef.class);
    game.step(.01f);
    assertFalse(link.isValid());
    game.close(); otherGame.close(); otherWorld.dispose();
  }

  @Test public void oldDestroyedHandleCannotDeleteOrdinaryWorldReplacementAfterDeletionRecyclesId() {
    boolean[] armed = {false}, failingSelector = {false}; int[] doomedId = {-1};
    BaseSystem deleter = new BaseSystem() {
      @Override protected void processSystem() { if (armed[0]) { armed[0] = false; world.delete(doomedId[0]); failingSelector[0] = true; } }
    };
    World world = new World(new WorldConfigurationBuilder().with(deleter).build());
    Game game = new Game(); ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    GameObject earlier = game.createObject("earlier"); earlier.addComponent(new SelectorValue());
    EntityLink earlierLink = bridge.bind(earlier, world.createEntity());
    earlierLink.addProjection(new ComponentProjection<SelectorValue, SelectorNative>(SelectorValue.class,
        SelectorNative.class, ProjectionDirection.GO_TO_ECS, ProjectionOwnership.OWNED) {
      @Override protected SelectorValue select(GameObject go) {
        if (failingSelector[0]) throw new IllegalStateException("selector failure");
        return go.getComponent(SelectorValue.class);
      }
      @Override protected void copyToEntity(SelectorValue source, SelectorNative target) {}
    });
    GameObject doomed = game.createObject("doomed");
    EntityLink old = bridge.bind(doomed, world.createEntity()); doomedId[0] = old.entity();
    game.start(); game.step(.01f); armed[0] = true;
    try { game.step(.01f); } catch (IllegalStateException expected) { }
    Entity replacement = world.createEntity();
    assertEquals(doomedId[0], replacement.getId());
    doomed.destroy();
    assertThrows(IllegalStateException.class, old::entity);
    failingSelector[0] = false;
    try { game.step(.01f); } catch (IllegalStateException expected) { }
    assertTrue("replacement entity must survive stale handle access", replacement.isActive());
    game.close();
  }

  @Test public void nativeDeleteAndReferenceRemovalClearRetainedReferenceObjects() {
    Game game = new Game(); World world = world();
    ArtemisSystem bridge = game.addSystem(new ArtemisSystem(world));
    EntityLink deleted = bridge.bind(game.createObject("deleted"), world.createEntity());
    EntityLink removed = bridge.bind(game.createObject("removed"), world.createEntity());
    game.start(); game.step(.01f);
    GameObjectRef deletedRef = world.getMapper(GameObjectRef.class).get(deleted.entity());
    GameObjectRef removedRef = world.getMapper(GameObjectRef.class).get(removed.entity());
    world.delete(deleted.entity()); world.edit(removed.entity()).remove(GameObjectRef.class);
    game.step(.01f);
    assertNull(deletedRef.objectOrNull());
    assertNull(removedRef.objectOrNull());
    game.close();
  }
}
