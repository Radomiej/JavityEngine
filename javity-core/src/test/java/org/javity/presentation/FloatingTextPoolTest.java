package org.javity.presentation;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.javity.core.Game;
import org.javity.core.GameSystem;
import org.javity.effects.Clock;
import org.javity.render.TextRenderer2D;
import org.junit.Test;

public class FloatingTextPoolTest {
  private static BitmapFont borrowedFont() {
    return new BitmapFont(new BitmapFont.BitmapFontData(), new TextureRegion(), false);
  }

  @Test
  public void poolReusesBoundedObjectsAndOldGenerationCannotCancelReplacement() {
    BitmapFont font = borrowedFont();
    try (Game game = new Game()) {
      FloatingTextPool pool = game.addSystem(new FloatingTextPool(font, Clock.WORLD, 2, 20));
      game.start();
      FloatingTextHandle old = pool.spawn("old", 1, 2, .2f, Color.WHITE);
      FloatingTextHandle second = pool.spawn("second", 3, 4, .3f, Color.WHITE);
      assertEquals(2, pool.getActiveCount());
      var oldObject = game.getObjects().getFirst();
      oldObject.transform.scale.set(5, 6);
      oldObject.transform.rotation = 1.2f;
      oldObject.transform.visualOffset.set(7, 8);
      oldObject.getComponent(TextRenderer2D.class).layer = 9;
      oldObject.getComponent(TextRenderer2D.class).order = 10;
      FloatingTextHandle replacement = pool.spawn("new", 5, 6, .2f, Color.RED);
      assertFalse(old.isActive());
      old.cancel();
      assertTrue(replacement.isActive());
      assertEquals(2, pool.getCapacity());
      assertEquals(2, game.getObjects().size());
      GameObjectAssertions.assertRenderer(game.getObjects().getFirst(), font);
      assertArrayEquals(
          new float[] {1, 1},
          new float[] {oldObject.transform.scale.x, oldObject.transform.scale.y},
          0);
      assertEquals(0, oldObject.transform.rotation, 0);
      assertEquals(0, oldObject.transform.visualOffset.len(), 0);
      assertEquals(0, oldObject.getComponent(TextRenderer2D.class).layer);
      assertEquals(0, oldObject.getComponent(TextRenderer2D.class).order);
      game.pause();
      game.step(.5f);
      assertTrue(second.isActive());
      game.resume();
      game.step(.5f);
      assertEquals(2, pool.getActiveCount());
      game.step(.5f);
      assertEquals(1, pool.getActiveCount());
      game.step(.5f);
      assertEquals(0, pool.getActiveCount());
      assertEquals("", game.getObjects().getFirst().getComponent(TextRenderer2D.class).text);
      assertFalse(game.getObjects().getFirst().getComponent(TextRenderer2D.class).isEnabled());
    } finally {
      font.dispose();
    }
  }

  @Test
  public void uiClockAdvancesThroughPauseAndValidationRejectsInvalidInputs() {
    BitmapFont font = borrowedFont();
    try (Game game = new Game()) {
      FloatingTextPool pool = game.addSystem(new FloatingTextPool(font, Clock.UI, 1, 10));
      game.start();
      assertThrows(IllegalArgumentException.class, () -> pool.spawn("", 0, 0, 1, Color.WHITE));
      assertThrows(
          IllegalArgumentException.class, () -> pool.spawn("bad", Float.NaN, 0, 1, Color.WHITE));
      assertThrows(IllegalArgumentException.class, () -> pool.spawn("bad", 0, 0, 0, Color.WHITE));
      Color invalid = new Color(Float.NaN, 1, 1, 1);
      assertThrows(IllegalArgumentException.class, () -> pool.spawn("bad", 0, 0, 1, invalid));
      FloatingTextHandle handle = pool.spawn("pause", 2, 3, .2f, new Color(1, .5f, .25f, .8f));
      game.pause();
      game.step(.1f);
      assertTrue(handle.isActive());
      game.step(.1f);
      assertFalse(handle.isActive());
      assertEquals(0, pool.getActiveCount());
    } finally {
      font.dispose();
    }
  }

  @Test
  public void spawnDuringUiSystemPhaseCannotAgeInThatSameFrame() {
    BitmapFont font = borrowedFont();
    try (Game game = new Game()) {
      FloatingTextPool[] pool = new FloatingTextPool[1];
      FloatingTextHandle[] handle = new FloatingTextHandle[1];
      game.addSystem(
          new GameSystem() {
            public void onUiUpdate(float delta) {
              if (handle[0] == null)
                handle[0] = pool[0].spawn("same frame", 2, 3, .2f, Color.WHITE);
            }
          });
      pool[0] = game.addSystem(new FloatingTextPool(font, Clock.UI, 1, 10));
      game.start();
      game.step(.1f);
      assertTrue(handle[0].isActive());
      assertEquals(1, game.getObjects().getFirst().getComponent(TextRenderer2D.class).color.a, 0);
      game.step(.1f);
      assertTrue(handle[0].isActive());
      assertEquals(
          .5f, game.getObjects().getFirst().getComponent(TextRenderer2D.class).color.a, .001f);
      game.step(.1f);
      assertFalse(handle[0].isActive());
    } finally {
      font.dispose();
    }
  }

  private static final class GameObjectAssertions {
    static void assertRenderer(org.javity.core.GameObject object, BitmapFont font) {
      assertSame(font, object.getComponent(TextRenderer2D.class).font);
    }
  }
}
