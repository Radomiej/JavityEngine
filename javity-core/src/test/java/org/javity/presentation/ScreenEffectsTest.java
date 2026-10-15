package org.javity.presentation;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.Color;
import org.javity.core.Game;
import org.junit.Test;

public class ScreenEffectsTest {
  @Test
  public void flashIsAnOwnedFullscreenScreenShapeWithUiClockFadeAndResize() {
    try (Game game = new Game()) {
      ScreenEffects effects = game.addSystem(new ScreenEffects(800, 600));
      ScreenFlash first = effects.flash(Color.RED, .8f, .4);
      assertTrue(first.isActive());
      game.start();
      var flashObject = game.getObjectsWith(org.javity.render.ShapeRenderer2D.class).getFirst();
      var shape = flashObject.getComponent(org.javity.render.ShapeRenderer2D.class);
      assertEquals(org.javity.render.ShapeRenderer2D.Space.SCREEN, shape.space);
      assertEquals(.8f, shape.color.a, 0);
      assertEquals(400, flashObject.transform.position.x, 0);
      assertEquals(600, flashObject.transform.scale.y, 0);
      game.pause();
      game.step(.1f);
      assertEquals(.6f, shape.color.a, 1e-6);
      game.resume();
      game.step(.1f);
      assertEquals(.4f, shape.color.a, 1e-6);
      effects.resize(1024, 768);
      assertEquals(512, flashObject.transform.position.x, 0);
      assertEquals(1024, flashObject.transform.scale.x, 0);
      first.cancel();
      assertFalse(first.isActive());
      assertFalse(flashObject.isActive());
      ScreenFlash zero = effects.flash(Color.WHITE, 1, 0);
      assertFalse(zero.isActive());
    }
  }

  @Test
  public void flashesAreBoundedOverlapAndCloseOnlyTheirOwnOverlay() {
    try (Game game = new Game()) {
      ScreenEffects effects = game.addSystem(new ScreenEffects(320, 200));
      game.start();
      ScreenFlash[] handles = new ScreenFlash[ScreenEffects.MAX_ACTIVE_FLASHES + 1];
      for (int i = 0; i < handles.length; i++) handles[i] = effects.flash(Color.WHITE, .5f, 1);
      assertEquals(64, effects.getActiveFlashCount());
      assertFalse(handles[0].isActive());
      assertTrue(handles[1].isActive());
      assertTrue(handles[64].isActive());
      handles[64].close();
      assertEquals(63, effects.getActiveFlashCount());
      assertTrue(handles[1].isActive());
      game.step(.1f);
      assertEquals(63, effects.getActiveFlashCount());
      handles[1].cancel();
      assertFalse(handles[1].isActive());
    }
  }

  @Test
  public void constructorResizeAndColorBoundsAreValidated() {
    assertThrows(IllegalArgumentException.class, () -> new ScreenEffects(0, 1));
    try (Game game = new Game()) {
      ScreenEffects effects = game.addSystem(new ScreenEffects(1, 1));
      assertThrows(IllegalArgumentException.class, () -> effects.resize(1, 0));
      assertThrows(IllegalArgumentException.class, () -> effects.flash(Color.WHITE, 1.1f, 1));
      Color invalid = new Color(Float.NaN, 0, 0, 1);
      assertThrows(IllegalArgumentException.class, () -> effects.flash(invalid, .5f, 1));
    }
  }
}
