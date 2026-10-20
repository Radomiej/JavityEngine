package org.javity.input;

import static org.junit.Assert.*;

import org.junit.Test;

public class InputActionsTest {
  @Test
  public void alternateKeysKeepActionDownUntilLastKeyReleases() {
    InputActions input = new InputActions();
    input.bind("jump", 32, 13);
    input.keyDown(32);
    input.finishFrame();
    input.keyDown(13);
    input.keyUp(32);
    assertTrue(input.isDown("jump"));
    assertFalse(input.justReleased("jump"));
    input.keyUp(13);
    assertTrue(input.justReleased("jump"));
  }

  @Test
  public void contextPriorityAndActivationReleaseAndRestoreActions() {
    InputActions input = new InputActions();
    input.bind("jump", 32);
    InputContext menu = input.addContext("menu", 10).bindKey("confirm", 32).consume("confirm");
    menu.deactivate();
    input.keyDown(32);
    assertTrue(input.isDown("jump"));
    input.finishFrame();
    menu.activate();
    assertTrue(input.justReleased("jump"));
    assertTrue(input.isDown("confirm"));
    menu.deactivate();
    assertTrue(input.justPressed("jump"));
  }

  @Test
  public void analogAndDigitalBindingsCombineWithDeadzoneAndValidation() {
    InputActions input = new InputActions();
    InputContext gameplay = input.addContext("gameplay", 1);
    gameplay.bindAxis("move", "pad0", "leftX", 1, .2f).bindKey("move", 39);
    input.axisValue("pad0", "leftX", .1f);
    assertEquals(0f, input.value("move"), 0f);
    input.axisValue("pad0", "leftX", .6f);
    assertEquals(.5f, input.value("move"), .0001f);
    input.keyDown(39);
    assertEquals(1f, input.value("move"), 0f);
    assertThrows(IllegalArgumentException.class, () -> input.axisValue("pad0", "leftX", Float.NaN));
    assertThrows(
        IllegalArgumentException.class, () -> gameplay.bindAxis("bad", "pad0", "x", 0, .1f));
  }

  @Test
  public void touchCancellationAndFocusLossReleaseActions() {
    InputActions input = new InputActions();
    input.addContext("touch", 1).bindButton("tap", "touch", "4");
    input.touchDown(4);
    assertTrue(input.justPressed("tap"));
    input.finishFrame();
    input.touchCancelled(4);
    assertTrue(input.justReleased("tap"));
    input.buttonDown("pad0", "south");
    input.addContext("pad", 2).bindButton("fire", "pad0", "south");
    assertTrue(input.isDown("fire"));
    input.focusLost();
    assertFalse(input.isDown("fire"));
    assertTrue(input.justReleased("fire"));
  }

  @Test
  public void rebindingWhileHeldTransitionsPredictably() {
    InputActions input = new InputActions();
    input.bind("jump", 32);
    input.keyDown(32);
    input.finishFrame();
    input.bind("jump", 32);
    assertTrue(input.isDown("jump"));
    assertFalse(input.justReleased("jump"));
    assertFalse(input.justPressed("jump"));
    input.bind("jump", 13);
    assertFalse(input.isDown("jump"));
    assertTrue(input.justReleased("jump"));
    input.finishFrame();
    input.keyDown(13);
    assertTrue(input.justPressed("jump"));
  }

  @Test
  public void touchProcessorFeedsBindingsAndCancellationReleasesThem() {
    InputActions input = new InputActions();
    input.addContext("touch", 1).bindButton("tap", "touch", "2");
    assertTrue(input.touchDown(10, 20, 2, 0));
    assertTrue(input.isDown("tap"));
    input.finishFrame();
    assertTrue(input.touchCancelled(10, 20, 2, 0));
    assertFalse(input.isDown("tap"));
    assertTrue(input.justReleased("tap"));
  }

  @Test
  public void unbindingAndRemovingContextReleaseHeldActions() {
    InputActions input = new InputActions();
    InputContext context = input.addContext("dialog", 5).bindKey("accept", 13);
    input.keyDown(13);
    assertTrue(input.isDown("accept"));
    context.unbind("accept");
    assertFalse(input.isDown("accept"));
    input.finishFrame();
    context.bindKey("accept", 13);
    assertTrue(input.isDown("accept"));
    assertTrue(input.removeContext("dialog"));
    assertFalse(input.isDown("accept"));
    assertFalse(input.removeContext("dialog"));
    assertThrows(IllegalArgumentException.class, () -> input.removeContext("default"));
  }

  @Test
  public void sourceIdentitiesRejectAmbiguousSeparators() {
    InputActions input = new InputActions();
    InputContext context = input.addContext("game", 1);
    assertThrows(
        IllegalArgumentException.class, () -> context.bindButton("fire", "pad\u00000", "south"));
    assertThrows(IllegalArgumentException.class, () -> input.buttonDown("pad", "south\u0000extra"));
  }
}
