package org.javity.core;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class GameSystemTest {
  @Test
  public void systemsRunAroundWorldUpdateBeforeLateUpdate() {
    List<String> calls = new ArrayList<>();
    try (Game game = new Game()) {
      game.addSystem(
          new GameSystem() {
            public void onCreate() {
              calls.add("system:create");
            }

            public void onBeforeUpdate(float dt) {
              calls.add("before");
            }

            public void onAfterUpdate(float dt) {
              calls.add("physics");
            }

            public void onDestroy() {
              calls.add("system:destroy");
            }
          });
      game.createObject("actor")
          .addComponent(
              new Component() {
                public void onCreate() {
                  calls.add("component:create");
                }

                public void onUpdate(float dt) {
                  calls.add("update");
                }

                public void onLateUpdate(float dt) {
                  calls.add("late");
                }

                public void onDestroy() {
                  calls.add("component:destroy");
                }
              });
      game.start();
      game.step(.02f);
      assertEquals(
          List.of("system:create", "component:create", "before", "update", "physics", "late"),
          calls);
    }
    assertEquals(
        List.of("component:destroy", "system:destroy"),
        calls.subList(calls.size() - 2, calls.size()));
  }

  @Test
  public void systemsBelongToOneGameAndStopDuringPause() {
    int[] steps = {0};
    GameSystem system =
        new GameSystem() {
          public void onAfterUpdate(float dt) {
            steps[0]++;
          }
        };
    try (Game a = new Game();
        Game b = new Game()) {
      a.addSystem(system);
      assertThrows(IllegalArgumentException.class, () -> b.addSystem(system));
      a.start();
      a.pause();
      a.step(.05f);
      assertEquals(0, steps[0]);
      a.resume();
      a.step(.05f);
      assertEquals(1, steps[0]);
      assertSame(system, a.getSystem(system.getClass()));
      assertThrows(IllegalStateException.class, () -> a.addSystem(new GameSystem() {}));
    }
  }

  @Test
  public void callbackFailureStillDisposesAllSystemsOnce() {
    int[] closed = {0};
    Game game = new Game();
    game.addSystem(
        new GameSystem() {
          public void onDestroy() {
            closed[0]++;
            throw new IllegalStateException("system");
          }
        });
    game.addSystem(
        new GameSystem() {
          public void onDestroy() {
            closed[0]++;
          }
        });
    game.createObject("fault")
        .addComponent(
            new Component() {
              public void onDestroy() {
                throw new IllegalStateException("component");
              }
            });
    game.start();
    var failure = assertThrows(IllegalStateException.class, game::dispose);
    assertEquals("component", failure.getMessage());
    assertEquals(1, failure.getSuppressed().length);
    assertEquals(2, closed[0]);
    game.dispose();
    assertEquals(2, closed[0]);
  }

  @Test
  public void unscaledSystemHookRunsBeforeUiAndSurvivesWorldFreeze() {
    List<String> calls = new ArrayList<>();
    try (Game game = new Game()) {
      game.addSystem(
          new GameSystem() {
            public void onUiUpdate(float dt) {
              calls.add("ui:" + dt);
            }

            public void onAfterUpdate(float dt) {
              calls.add("world");
            }
          });
      game.createObject("ui")
          .addComponent(
              new Component() {
                {
                  updateMode = UpdateMode.UI;
                }

                public void onUpdate(float dt) {
                  calls.add("component");
                }
              });
      game.addSystem(new org.javity.services.TimeManager()).freeze(.2);
      game.start();
      game.step(.05f);
      assertEquals(List.of("ui:0.05", "component"), calls);
      game.pause();
      game.step(.03f);
      assertEquals(List.of("ui:0.05", "component", "ui:0.03", "component"), calls);
    }
  }

  @Test
  public void closingFromUiSystemSkipsLaterSystemsAndUiComponents() {
    List<String> calls = new ArrayList<>();
    Game game = new Game();
    game.addSystem(
        new GameSystem() {
          public void onUiUpdate(float dt) {
            calls.add("close");
            game.close();
          }
        });
    game.addSystem(
        new GameSystem() {
          public void onUiUpdate(float dt) {
            calls.add("late-system");
          }
        });
    game.createObject("ui")
        .addComponent(
            new Component() {
              {
                updateMode = UpdateMode.UI;
              }

              public void onUpdate(float dt) {
                calls.add("component");
              }
            });
    game.start();
    game.step(.01f);
    assertEquals(List.of("close"), calls);
  }

  @Test
  public void providersReleaseAfterConsumersEvenWhenConsumerThrows() {
    List<String> calls = new ArrayList<>();
    Game game = new Game();
    game.addSystem(
        new GameSystem() {
          protected void onRelease() {
            calls.add("provider");
          }
        });
    game.addSystem(
        new GameSystem() {
          protected void onRelease() {
            calls.add("consumer");
            throw new IllegalStateException("consumer cleanup");
          }
        });
    game.start();
    IllegalStateException error = assertThrows(IllegalStateException.class, game::close);
    assertEquals("consumer cleanup", error.getMessage());
    assertEquals(List.of("consumer", "provider"), calls);
  }
}
