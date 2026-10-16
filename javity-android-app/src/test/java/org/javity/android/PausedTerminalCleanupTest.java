package org.javity.android;

import static org.junit.Assert.*;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import org.javity.core.Game;
import org.junit.Test;

public final class PausedTerminalCleanupTest {
  @Test
  public void pausedTerminalCleanupClosesOnlyRetainedCpuGameOnOwnerThread() throws Exception {
    AndroidGameFixture fixture = new AndroidGameFixture();
    Game game = new Game();
    set(fixture, "ownerThread", Thread.currentThread());
    set(fixture, "lifecycleSuspendedSnapshot", true);
    set(fixture, "game", game);

    fixture.requestTerminalDisposal();
    fixture.disposeRetainedCpuAfterPause();

    assertTrue("retained Game closed", game.isDisposed());
    assertTrue("terminal completion published after close", fixture.isTerminalDisposalComplete());
    assertTrue("fixture marked disposed", fixture.isDisposedForInstrumentation());
  }

  @Test
  public void pausedTerminalCleanupRefusesAnyRemainingLiveGeneration() throws Exception {
    AndroidGameFixture fixture = new AndroidGameFixture();
    Game game = new Game();
    set(fixture, "ownerThread", Thread.currentThread());
    set(fixture, "lifecycleSuspendedSnapshot", true);
    set(fixture, "resourcesReady", true);
    set(fixture, "game", game);

    try {
      fixture.disposeRetainedCpuAfterPause();
      fail("remaining live generation must block CPU-only disposal");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("still owns GL resources"));
    }
    assertFalse("guard failure must leave the Game intact", game.isDisposed());
    assertFalse(fixture.isTerminalDisposalComplete());
  }

  @Test
  public void failedPauseRemainsPrimaryAndBlocksTerminalSuccessAfterPausedCpuCleanup()
      throws Exception {
    AndroidGameFixture fixture = new AndroidGameFixture();
    IllegalStateException gameCloseFailure =
        new IllegalStateException("injected Game close failure");
    FailingCloseGame game = new FailingCloseGame(gameCloseFailure);
    IllegalStateException pauseFailure = new IllegalStateException("injected pause input cleanup");
    Input previous = Gdx.input;
    InputProcessor processor = new InputAdapter();
    Input ownedInput = failingInput(processor, pauseFailure);
    set(fixture, "ownerThread", Thread.currentThread());
    set(fixture, "game", game);
    set(fixture, "ownedInput", ownedInput);
    set(fixture, "ownedInputProcessor", processor);
    Gdx.input = ownedInput;
    try {
      try {
        fixture.pause();
        fail("ordinary pause should report the injected cleanup failure");
      } catch (IllegalStateException observed) {
        assertSame(pauseFailure, observed);
      }

      fixture.requestTerminalDisposal();
      try {
        fixture.disposeRetainedCpuAfterPause();
        fail("terminal acknowledgment must retain the earlier pause failure");
      } catch (IllegalStateException observed) {
        assertSame(pauseFailure, observed);
      }
      assertTrue("Game close still runs after pause failure", game.isDisposed());
      assertTrue("Game close was attempted", game.closeAttempted);
      assertArrayEquals(new Throwable[] {gameCloseFailure}, pauseFailure.getSuppressed());
      assertFalse(
          "failed pause must never publish terminal success", fixture.isTerminalDisposalComplete());
    } finally {
      Gdx.input = previous;
    }
  }

  @Test
  public void failedPauseRemainsPrimaryAcrossRegularDispose() throws Exception {
    AndroidGameFixture fixture = new AndroidGameFixture();
    IllegalStateException gameCloseFailure =
        new IllegalStateException("injected Game close failure");
    FailingCloseGame game = new FailingCloseGame(gameCloseFailure);
    IllegalStateException pauseFailure = new IllegalStateException("injected pause input cleanup");
    Input previous = Gdx.input;
    InputProcessor processor = new InputAdapter();
    Input ownedInput = failingInput(processor, pauseFailure);
    set(fixture, "ownerThread", Thread.currentThread());
    set(fixture, "game", game);
    set(fixture, "ownedInput", ownedInput);
    set(fixture, "ownedInputProcessor", processor);
    Gdx.input = ownedInput;
    try {
      try {
        fixture.pause();
        fail("ordinary pause should report the injected cleanup failure");
      } catch (IllegalStateException observed) {
        assertSame(pauseFailure, observed);
      }

      try {
        fixture.dispose();
        fail("regular disposal must preserve the earlier pause failure");
      } catch (IllegalStateException observed) {
        assertSame(pauseFailure, observed);
      }
      assertTrue("Game close still runs after pause failure", game.isDisposed());
      assertTrue("Game close was attempted", game.closeAttempted);
      assertArrayEquals(new Throwable[] {gameCloseFailure}, pauseFailure.getSuppressed());
      assertFalse(
          "failed pause must never publish terminal success", fixture.isTerminalDisposalComplete());
    } finally {
      Gdx.input = previous;
    }
  }

  private static Input failingInput(InputProcessor processor, RuntimeException failure) {
    return (Input)
        Proxy.newProxyInstance(
            Input.class.getClassLoader(),
            new Class<?>[] {Input.class},
            (proxy, method, arguments) -> {
              if (method.getName().equals("getInputProcessor")) return processor;
              if (method.getName().equals("setInputProcessor")) throw failure;
              return null;
            });
  }

  private static final class FailingCloseGame extends Game {
    private final RuntimeException closeFailure;
    private boolean closeAttempted;

    private FailingCloseGame(RuntimeException closeFailure) {
      this.closeFailure = closeFailure;
    }

    @Override
    public void close() {
      closeAttempted = true;
      super.close();
      throw closeFailure;
    }
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
