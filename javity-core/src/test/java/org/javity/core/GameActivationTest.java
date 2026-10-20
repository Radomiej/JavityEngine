package org.javity.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class GameActivationTest {
  @Test
  public void activeChangesReachDisabledComponentsWithoutUpdatingThem() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("panel");
      List<Boolean> changes = new ArrayList<>();
      Component component =
          object.addComponent(
              new Component() {
                @Override
                protected void onActivationChanged() {
                  changes.add(isEnabled() && getGameObject().isActive());
                }

                @Override
                public void onUpdate(float delta) {
                  fail("Disabled component updated");
                }
              });
      component.setEnabled(false);
      game.start();
      object.setActive(false);
      object.setActive(true);
      game.step(0);
      assertEquals(List.of(false, false, false), changes);
      object.removeComponent(component);
      object.setActive(false);
      assertEquals(3, changes.size());
    }
  }

  @Test
  public void activationFailureDoesNotSkipLaterComponentsOrRollbackState() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("panels");
      RuntimeException failure = new RuntimeException("shared activation failure");
      int[] notified = {0};
      for (int i = 0; i < 3; i++)
        object.addComponent(
            new Component() {
              @Override
              protected void onActivationChanged() {
                if (!getGameObject().isActive()) {
                  notified[0]++;
                  throw failure;
                }
              }
            });
      game.start();
      assertSame(failure, assertThrows(RuntimeException.class, () -> object.setActive(false)));
      assertFalse(object.isActive());
      assertEquals(3, notified[0]);
      object.setActive(false);
      assertEquals(3, notified[0]);
    }
  }
}
