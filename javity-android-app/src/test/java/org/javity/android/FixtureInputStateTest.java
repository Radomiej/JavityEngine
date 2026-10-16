package org.javity.android;

import static org.junit.Assert.*;

import com.badlogic.gdx.Input;
import org.junit.Test;

public final class FixtureInputStateTest {
  @Test
  public void movementFirstLookSecondAndLookFirstMovementSecondHaveIndependentOwners() {
    LookPointerClaim claim = new LookPointerClaim();
    FixtureInputState input = new FixtureInputState();

    input.touch(Input.Keys.W, 0, true); // first finger owns movement
    assertTrue(claim.claim(1)); // second finger can independently claim look
    assertTrue(claim.owns(1));
    claim.release(1);
    assertTrue(claim.claim(0)); // reversed touch order is also valid
    input.touch(Input.Keys.D, 1, true);
    assertTrue(input.held(Input.Keys.W));
    assertTrue(input.held(Input.Keys.D));
    assertTrue(claim.owns(0));
  }

  @Test
  public void oneReleaseDoesNotClearAnotherTouchOrKeyboardOwner() {
    FixtureInputState input = new FixtureInputState();
    input.keyboard(Input.Keys.W, true);
    input.touch(Input.Keys.W, 0, true);
    input.touch(Input.Keys.W, 1, true);
    input.touch(Input.Keys.W, 0, false);
    input.keyboard(Input.Keys.W, false);
    assertTrue("second touch still owns W", input.held(Input.Keys.W));
    input.touch(Input.Keys.W, 1, false);
    assertFalse(input.held(Input.Keys.W));
  }

  @Test
  public void nonOwnerReleaseAndCancellationCannotStealLookPointer() {
    LookPointerClaim claim = new LookPointerClaim();
    assertFalse(claim.claim(-1));
    assertTrue(claim.claim(1));
    assertFalse(claim.claim(2));
    assertFalse(claim.release(2));
    assertTrue(claim.owns(1));
    claim.clear(); // pause/focus cancellation
    assertFalse(claim.owns(1));
    assertTrue(claim.claim(2));
  }

  @Test
  public void jumpAndMovementAreTrackedPerSourceUntilAllOwnersRelease() {
    FixtureInputState input = new FixtureInputState();
    input.keyboard(Input.Keys.SPACE, true);
    input.jumpPointer(4, true);
    input.keyboard(Input.Keys.SPACE, false);
    assertTrue(input.jumpHeld());
    input.jumpPointer(4, false);
    assertFalse(input.jumpHeld());
    input.touch(Input.Keys.A, 3, true);
    input.clear();
    assertFalse(input.held(Input.Keys.A));
  }
}
