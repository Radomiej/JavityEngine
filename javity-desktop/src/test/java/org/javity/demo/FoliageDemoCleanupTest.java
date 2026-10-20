package org.javity.demo;

import static org.junit.Assert.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.Model;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.javity.core.Component;
import org.javity.core.Game;
import org.junit.Test;

/** Fault injection needs no GL context: cleanup must continue past a failing scene callback. */
public class FoliageDemoCleanupTest {
  private static final class CountedModel extends Model {
    int disposals;
    final RuntimeException failure;

    CountedModel(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public void dispose() {
      disposals++;
      if (failure != null) throw failure;
    }
  }

  private static final class CountedTexture extends Texture {
    int disposals;

    CountedTexture() {
      super();
    }

    @Override
    public void dispose() {
      disposals++;
    }
  }

  private static final class CountedFont extends BitmapFont {
    int disposals;

    CountedFont() {
      super(new BitmapFontData(), new TextureRegion(), false);
    }

    @Override
    public void dispose() {
      disposals++;
    }
  }

  @Test
  public void cleanupContinuesOnceAndAggregatesDistinctFailures() throws Exception {
    FoliageDemo demo = new FoliageDemo(false);
    RuntimeException sceneFailure = new IllegalStateException("scene cleanup");
    RuntimeException modelFailure = new IllegalArgumentException("model cleanup");
    attachFailingGame(demo, sceneFailure);
    CountedModel first = new CountedModel(modelFailure);
    CountedModel sameFailure = new CountedModel(modelFailure);
    CountedModel last = new CountedModel(null);
    models(demo).addAll(List.of(first, sameFailure, last));
    CountedTexture texture = new CountedTexture();
    CountedFont font = new CountedFont();
    set(demo, "atlasTexture", texture);
    set(demo, "font", font);

    assertSame(sceneFailure, assertThrows(IllegalStateException.class, demo::dispose));
    assertArrayEquals(new Throwable[] {modelFailure}, sceneFailure.getSuppressed());
    assertEquals(1, first.disposals);
    assertEquals(1, sameFailure.disposals);
    assertEquals(1, last.disposals);
    assertEquals(1, texture.disposals);
    assertEquals(1, font.disposals);
    assertTrue(models(demo).isEmpty());
    demo.dispose();
    assertEquals(1, first.disposals);
    assertEquals(1, sameFailure.disposals);
    assertEquals(1, last.disposals);
    assertEquals(1, texture.disposals);
    assertEquals(1, font.disposals);
  }

  @Test
  public void failedCreateKeepsOriginalErrorAndExitsAfterCleanupFailure() throws Exception {
    FoliageDemo demo = new FoliageDemo(false);
    RuntimeException original = new IllegalArgumentException("original create/render failure");
    RuntimeException cleanup = new IllegalStateException("cleanup failure");
    attachFailingGame(demo, cleanup);
    CountedModel model = new CountedModel(null);
    models(demo).add(model);
    AtomicInteger exits = new AtomicInteger();
    Application previous = Gdx.app;
    Gdx.app =
        (Application)
            Proxy.newProxyInstance(
                Application.class.getClassLoader(),
                new Class<?>[] {Application.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("exit")) exits.incrementAndGet();
                  return null;
                });
    try {
      Method fail = FoliageDemo.class.getDeclaredMethod("fail", Throwable.class);
      fail.setAccessible(true);
      fail.invoke(demo, original);
      assertSame(original, get(demo, "failure"));
      assertArrayEquals(new Throwable[] {cleanup}, original.getSuppressed());
      assertEquals(1, model.disposals);
      assertEquals(1, exits.get());
      demo.dispose();
      assertEquals(1, model.disposals);
    } finally {
      Gdx.app = previous;
    }
  }

  @Test
  public void identicalOriginalAndCleanupFailureNeverSelfSuppresses() throws Exception {
    FoliageDemo demo = new FoliageDemo(false);
    RuntimeException original = new IllegalArgumentException("shared failure");
    attachFailingGame(demo, original);
    AtomicInteger exits = new AtomicInteger();
    Application previous = Gdx.app;
    Gdx.app =
        (Application)
            Proxy.newProxyInstance(
                Application.class.getClassLoader(),
                new Class<?>[] {Application.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("exit")) exits.incrementAndGet();
                  return null;
                });
    try {
      Method fail = FoliageDemo.class.getDeclaredMethod("fail", Throwable.class);
      fail.setAccessible(true);
      fail.invoke(demo, original);
      assertSame(original, get(demo, "failure"));
      assertEquals(0, original.getSuppressed().length);
      assertEquals(1, exits.get());
    } finally {
      Gdx.app = previous;
    }
  }

  private static void attachFailingGame(FoliageDemo demo, RuntimeException failure)
      throws Exception {
    Game game = new Game();
    game.createObject("failing scene callback")
        .addComponent(
            new Component() {
              @Override
              public void onDestroy() {
                throw failure;
              }
            });
    game.start();
    set(demo, "game", game);
  }

  @SuppressWarnings("unchecked")
  private static List<Model> models(FoliageDemo demo) throws Exception {
    return (List<Model>) get(demo, "models");
  }

  private static Object get(FoliageDemo demo, String name) throws Exception {
    Field field = FoliageDemo.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(demo);
  }

  private static void set(FoliageDemo demo, String name, Object value) throws Exception {
    Field field = FoliageDemo.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(demo, value);
  }
}
