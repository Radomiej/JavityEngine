package org.javity.core;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class ComponentActivationTest {
  @Test
  public void activationHookWaitsUntilOnCreateHasCompleted() {
    try (Game game = new Game()) {
      GameObject owner = game.createObject("activation");
      AtomicInteger hooks = new AtomicInteger();
      Component component =
          new Component() {
            @Override
            public void onCreate() {
              setEnabled(false);
              owner.setActive(false);
              assertEquals(0, hooks.get());
              setEnabled(true);
              assertEquals(0, hooks.get());
            }

            @Override
            protected void onActivationChanged() {
              hooks.incrementAndGet();
            }
          };
      owner.addComponent(component);
      component.initialize();
      assertEquals(1, hooks.get());

      component.setEnabled(false);
      assertEquals(2, hooks.get());
      component.setEnabled(false);
      assertEquals(2, hooks.get());
      component.removed = true;
      component.setEnabled(true);
      assertEquals(2, hooks.get());
    }
  }
}
