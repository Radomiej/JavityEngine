package org.javity.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class LifecycleSafetyTest {
  private static final class EqualComponent extends Component {
    @Override
    public boolean equals(Object value) {
      return value instanceof EqualComponent;
    }

    @Override
    public int hashCode() {
      return 1;
    }
  }

  @Test
  public void removingAnInstanceDoesNotRemoveAnEqualSibling() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("siblings");
      EqualComponent first = object.addComponent(new EqualComponent());
      EqualComponent second = object.addComponent(new EqualComponent());
      game.start();
      List<Component> removed = new ArrayList<>();
      object.onComponentChange(
          change -> {
            if (change.type() == ComponentChange.Type.REMOVED) removed.add(change.component());
          });
      assertTrue(object.removeComponent(second));
      assertSame(first, object.getComponent(EqualComponent.class));
      assertTrue(object.containsComponent(first));
      assertFalse(object.containsComponent(second));
      assertTrue(first.isInitialized());
      assertFalse(second.isInitialized());
      assertEquals(1, removed.size());
      assertSame(second, removed.getFirst());
      assertEquals(List.of(object), game.getObjectsWith(EqualComponent.class));
      game.step(0);
      assertTrue(first.isInitialized());
    }
  }

  @Test
  public void anEqualForeignComponentCannotRemoveAnAttachedOne() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("owned");
      EqualComponent attached = object.addComponent(new EqualComponent());
      assertFalse(object.removeComponent(new EqualComponent()));
      assertSame(attached, object.getComponent(EqualComponent.class));
      assertEquals(1, object.getComponents().size());
    }
  }

  @Test
  public void componentCleanupPreservesARepeatedFailure() {
    Game game = new Game();
    RuntimeException failure = new RuntimeException("shared");
    int[] released = {0};
    game.createObject("component")
        .addComponent(
            new Component() {
              @Override
              public void onDestroy() {
                throw failure;
              }

              @Override
              protected void onRelease() {
                released[0]++;
                throw failure;
              }
            });
    game.start();
    assertSame(failure, assertThrows(RuntimeException.class, game::dispose));
    assertEquals(1, released[0]);
    game.dispose();
    assertEquals(1, released[0]);
  }

  @Test
  public void systemCleanupPreservesARepeatedFailure() {
    Game game = new Game();
    RuntimeException failure = new RuntimeException("shared");
    int[] released = {0};
    game.addSystem(
        new GameSystem() {
          @Override
          public void onDestroy() {
            throw failure;
          }

          @Override
          protected void onRelease() {
            released[0]++;
            throw failure;
          }
        });
    game.start();
    assertSame(failure, assertThrows(RuntimeException.class, game::dispose));
    assertEquals(1, released[0]);
  }

  @Test
  public void sharedFailureDoesNotSkipOtherComponentsObjectsOrSystems() {
    RuntimeException failure = new RuntimeException("shared");
    List<String> released = new ArrayList<>();
    Game game =
        new Game() {
          @Override
          public void onDestroy() {
            released.add("game");
            throw failure;
          }
        };
    GameObject first = game.createObject("first");
    first.addComponent(throwingComponent(failure, released, "a"));
    first.addComponent(throwingComponent(failure, released, "b"));
    first.addComponent(throwingComponent(failure, released, "c"));
    game.createObject("second").addComponent(throwingComponent(failure, released, "d"));
    game.addSystem(
        new GameSystem() {
          @Override
          protected void onRelease() {
            released.add("system");
            throw failure;
          }
        });
    game.start();
    assertSame(failure, assertThrows(RuntimeException.class, game::dispose));
    assertEquals(List.of("a", "b", "c", "d", "system", "game"), released);
    assertTrue(first.getComponents().isEmpty());
    assertTrue(game.getObjects().isEmpty());
  }

  @Test
  public void sharedFailureDoesNotStopTheRemovedComponentQueue() {
    try (Game game = new Game()) {
      RuntimeException failure = new RuntimeException("shared");
      List<String> released = new ArrayList<>();
      GameObject object = game.createObject("removed");
      object.addComponent(throwingComponent(failure, released, "a"));
      object.addComponent(throwingComponent(failure, released, "b"));
      object.addComponent(throwingComponent(failure, released, "c"));
      game.start();
      object.removeComponents(Component.class);
      assertSame(failure, assertThrows(RuntimeException.class, () -> game.step(0)));
      assertEquals(List.of("a", "b", "c"), released);
      game.step(0);
      assertEquals(3, released.size());
    }
  }

  @Test
  public void updateFailureRemainsPrimaryWhenDeferredCleanupFails() {
    try (Game game = new Game()) {
      RuntimeException update = new RuntimeException("update");
      RuntimeException cleanup = new RuntimeException("cleanup");
      game.createObject("failing")
          .addComponent(
              new Component() {
                @Override
                public void onUpdate(float delta) {
                  getGameObject().removeComponent(this);
                  throw update;
                }

                @Override
                protected void onRelease() {
                  throw cleanup;
                }
              });
      game.start();
      assertSame(update, assertThrows(RuntimeException.class, () -> game.step(0)));
      assertArrayEquals(new Throwable[] {cleanup}, update.getSuppressed());
      game.step(0);
    }
  }

  @Test
  public void identicalUpdateAndCleanupFailureIsNotSelfSuppressed() {
    try (Game game = new Game()) {
      AssertionError failure = new AssertionError("shared error");
      game.createObject("failing")
          .addComponent(
              new Component() {
                @Override
                public void onUpdate(float delta) {
                  getGameObject().removeComponent(this);
                  throw failure;
                }

                @Override
                protected void onRelease() {
                  throw failure;
                }
              });
      game.start();
      assertSame(failure, assertThrows(AssertionError.class, () -> game.step(0)));
      assertEquals(0, failure.getSuppressed().length);
      game.step(0);
    }
  }

  private static Component throwingComponent(
      RuntimeException failure, List<String> released, String name) {
    return new Component() {
      @Override
      protected void onRelease() {
        released.add(name);
        throw failure;
      }
    };
  }
}
