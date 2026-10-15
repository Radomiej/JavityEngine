package org.javity.core;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.Vector2;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ComponentControllerTest {
  static class Base extends Component {}

  static class Derived extends Base {}

  @Test
  public void componentQueriesAndRemovalDelegateToOwner() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("components");
      Derived first = object.addComponent(new Derived());
      Derived second = object.addComponent(new Derived());
      List<ComponentChange> changes = new ArrayList<>();
      Component observer = object.addComponent(new Component() {
        @Override
        public void onComponentChange(ComponentChange change) {
          changes.add(change);
        }
      });
      game.start();

      assertEquals(List.of(first, second), first.getComponents(Base.class));
      assertEquals(3, first.getComponents().size());
      assertSame(first, observer.requireComponent(Base.class));
      assertTrue(observer.hasComponent(Derived.class));
      assertTrue(observer.removeComponent(first));
      assertEquals(1, observer.removeComponents(Base.class));
      assertEquals(List.of(ComponentChange.Type.REMOVED, ComponentChange.Type.REMOVED),
          changes.stream().map(ComponentChange::type).toList());
      assertFalse(observer.hasComponent(Base.class));
      assertFalse(observer.removeComponent(Base.class));
      assertThrows(IllegalStateException.class, () -> observer.requireComponent(Base.class));
    }
  }

  @Test
  public void movementNormalizesDirectionAndLeavesInputVectorUntouched() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("player");
      CharacterController2D controller = object.addComponent(new CharacterController2D());
      controller.move(1, 1);
      assertEquals(120, controller.velocity.len(), .0001f);
      Vector2 direction = new Vector2(3, 4);
      controller.move(direction, 10);
      assertEquals(new Vector2(3, 4), direction);
      assertEquals(6, controller.velocity.x, .0001f);
      assertEquals(8, controller.velocity.y, .0001f);
      game.start();
      game.step(.1f);
      assertEquals(.6f, object.transform.position.x, .0001f);
      assertEquals(.8f, object.transform.position.y, .0001f);
    }
  }

  @Test
  public void boundsConstrainCenterByConfiguredHalfExtents() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("player").setPosition(8.9f, 7.9f);
      CharacterController2D controller = object.addComponent(new CharacterController2D());
      controller.setWorldBounds(0, 0, 10, 8).setHalfExtents(1, 2);
      controller.move(1, 1, 10);
      game.start();
      game.step(.1f);
      assertEquals(9, object.transform.position.x, .0001f);
      assertEquals(6, object.transform.position.y, .0001f);

      controller.constrainToBounds = false;
      game.step(.1f);
      assertTrue(object.transform.position.x > 9);
      assertTrue(object.transform.position.y > 6);
    }
  }

  @Test
  public void invalidMovementAndDeltaAreRejectedAndZeroDeltaIsSafe() {
    CharacterController2D controller = new CharacterController2D();
    assertThrows(IllegalArgumentException.class, () -> controller.move(Float.NaN, 0));
    assertThrows(IllegalArgumentException.class, () -> controller.move(1, 0, -1));
    assertThrows(IllegalArgumentException.class, () -> controller.move(1, 0, Float.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> controller.setSpeed(Float.NaN));
    assertThrows(IllegalArgumentException.class, () -> controller.setWorldBounds(0, 0, 0, 4));
    assertThrows(IllegalArgumentException.class, () -> controller.setHalfExtents(-1, 0));
    assertThrows(IllegalArgumentException.class, () -> controller.onUpdate(Float.NaN));
    assertThrows(IllegalArgumentException.class, () -> controller.onUpdate(-1));
    controller.onUpdate(0);
  }

  @Test
  public void inactiveDisabledAndDestroyedOwnersDoNotMove() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("player");
      CharacterController2D controller = object.addComponent(new CharacterController2D());
      controller.move(1, 0, 10);
      game.start();

      object.setActive(false);
      game.step(.1f);
      assertEquals(0, object.transform.position.x, 0);
      object.setActive(true);
      controller.setEnabled(false);
      game.step(.1f);
      assertEquals(0, object.transform.position.x, 0);
      controller.setEnabled(true);
      game.step(.1f);
      assertEquals(1, object.transform.position.x, .0001f);

      object.destroy();
      controller.move(1, 0, 10);
      game.step(.1f);
      assertEquals(1, object.transform.position.x, .0001f);
    }
  }
}
