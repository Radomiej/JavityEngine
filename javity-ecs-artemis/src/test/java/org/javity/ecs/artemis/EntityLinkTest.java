package org.javity.ecs.artemis;

import static org.junit.Assert.*;

import com.artemis.*;
import org.javity.core.*;
import org.junit.Test;

public class EntityLinkTest {
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
}
