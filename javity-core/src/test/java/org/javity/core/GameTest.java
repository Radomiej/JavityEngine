package org.javity.core;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class GameTest {
  static class Base extends Component {}

  static class Child extends Base {}

  @Test
  public void multipleComponentsAndInheritanceUpdateIndexes() {
    try (Game game = new Game()) {
      GameObject o = game.createObject("player");
      Child first = o.addComponent(new Child());
      Child second = o.addComponent(new Child());
      assertSame(first, o.getComponent(Base.class));
      assertEquals(List.of(first, second), o.getComponents(Base.class));
      assertEquals(List.of(o), game.getObjectsWith(Base.class));
      o.removeComponent(first);
      assertEquals(List.of(o), game.getObjectsWith(Base.class));
      o.removeComponent(second);
      assertFalse(o.hasComponent(Base.class));
      assertTrue(game.getObjectsWith(Base.class).isEmpty());
    }
  }

  @Test
  public void mutationsDuringUpdateAreDeferredAndDestroyedOnce() {
    List<String> events = new ArrayList<>();
    try (Game game = new Game()) {
      GameObject o = game.createObject("test");
      Component late =
          new Component() {
            public void onCreate() {
              events.add("late:create");
            }

            public void onUpdate(float dt) {
              events.add("late:update");
            }

            public void onDestroy() {
              events.add("late:destroy");
            }
          };
      o.addComponent(
          new Component() {
            boolean added;

            public void onUpdate(float dt) {
              if (!added) {
                o.addComponent(late);
                added = true;
              }
            }
          });
      game.start();
      game.step(.016f);
      assertTrue(events.isEmpty());
      game.step(.016f);
      assertEquals(List.of("late:create", "late:update"), events);
      o.destroy();
      o.destroy();
      game.step(.016f);
      assertEquals(1, Collections.frequency(events, "late:destroy"));
      assertTrue(game.getObjects().isEmpty());
    }
  }

  @Test
  public void pendingInitializationDoesNotAdvanceWorldTimeOrRunUpdates() {
    int[] lifecycle = new int[2];
    try (Game game = new Game()) {
      game.start();
      game.pause();
      long frame = game.getFrameIndex();
      game.unscaledElapsed = 3;
      game.elapsed = 2;
      Component component =
          game.createObject("render attachment")
              .addComponent(
                  new Component() {
                    @Override
                    public void onCreate() {
                      lifecycle[0]++;
                    }

                    @Override
                    public void onUpdate(float delta) {
                      lifecycle[1]++;
                    }
                  });

      game.initializePendingComponents();

      assertTrue(component.isInitialized());
      assertArrayEquals(new int[] {1, 0}, lifecycle);
      assertEquals(frame, game.getFrameIndex());
      assertEquals(3, game.unscaledElapsed, 0);
      assertEquals(2, game.elapsed, 0);
    }
  }

  @Test
  public void pendingInitializationRejectsReentryAndGameStepFromOnCreate() {
    try (Game game = new Game()) {
      game.start();
      game.createObject("reentrant")
          .addComponent(
              new Component() {
                @Override
                public void onCreate() {
                  getGame().initializePendingComponents();
                }
              });
      assertThrows(IllegalStateException.class, game::initializePendingComponents);
      assertEquals(0, game.getFrameIndex());
    }
    try (Game game = new Game()) {
      game.start();
      game.createObject("recursive step")
          .addComponent(
              new Component() {
                @Override
                public void onCreate() {
                  getGame().step(0);
                }
              });
      assertThrows(IllegalStateException.class, game::initializePendingComponents);
      assertEquals(0, game.getFrameIndex());
    }
  }

  @Test
  public void pauseLeavesUiUpdatingAndRejectsInvalidTime() {
    int[] updates = new int[2];
    try (Game game = new Game()) {
      GameObject o = game.createObject("ui");
      o.addComponent(
          new Component() {
            public void onUpdate(float dt) {
              updates[0]++;
            }
          });
      Component ui =
          o.addComponent(
              new Component() {
                public void onUpdate(float dt) {
                  updates[1]++;
                }
              });
      ui.updateMode = Component.UpdateMode.UI;
      game.start();
      game.pause();
      game.step(1);
      assertArrayEquals(new int[] {0, 1}, updates);
      assertEquals(.1f, game.unscaledDeltaTime, .0001f);
      assertEquals(0, game.deltaTime, 0);
      assertThrows(IllegalArgumentException.class, () -> game.step(Float.NaN));
      assertThrows(IllegalArgumentException.class, () -> game.step(-1));
    }
  }

  @Test
  public void queriesAreSceneLocalAndTagsExcludeDestroyedObjects() {
    try (Game a = new Game();
        Game b = new Game()) {
      GameObject o = a.createObject("player").addTag("player");
      o.addComponent(new Child());
      o.setActive(false);
      assertEquals(List.of(o), a.getObjectsWith(Base.class));
      assertTrue(b.getObjectsWith(Base.class).isEmpty());
      assertEquals(List.of(o), a.getObjectsWithTag("player"));
      o.destroy();
      assertTrue(a.getObjectsWithTag("player").isEmpty());
    }
  }

  @Test
  public void attachedComponentsCannotBeReusedAndRequireIsOptional() {
    try (Game game = new Game()) {
      GameObject a = game.createObject("a"), b = game.createObject("b");
      Child c = a.addComponent(new Child());
      assertThrows(IllegalArgumentException.class, () -> b.addComponent(c));
      assertNull(b.getComponent(Base.class));
      assertThrows(IllegalStateException.class, () -> b.requireComponent(Base.class));
    }
  }

  @Test
  public void pauseFromUiDoesNotAdvanceWorldTime() {
    try (Game game = new Game()) {
      Component ui =
          game.createObject("pause")
              .addComponent(
                  new Component() {
                    public void onUpdate(float delta) {
                      getGame().pause();
                    }
                  });
      ui.updateMode = Component.UpdateMode.UI;
      game.start();
      game.step(.05f);
      assertEquals(0, game.elapsed, 0);
      assertEquals(0, game.deltaTime, 0);
      assertEquals(.05, game.unscaledElapsed, .0001);
    }
  }
}
