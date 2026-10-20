package org.javity.ui.components;

import static org.junit.Assert.*;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.ui.declarative.UiDocument;
import org.javity.ui.declarative.UiNode;
import org.javity.ui.yoga.YogaBackends;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UiDocumentComponentTest {
  private Graphics previousGraphics;

  @Before
  public void installHeadlessGraphics() {
    previousGraphics = Gdx.graphics;
    Gdx.graphics =
        (Graphics)
            Proxy.newProxyInstance(
                Graphics.class.getClassLoader(),
                new Class<?>[] {Graphics.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("getWidth")) return 640;
                  if (method.getName().equals("getHeight")) return 480;
                  Class<?> result = method.getReturnType();
                  if (result == boolean.class) return false;
                  if (result == int.class) return 0;
                  if (result == float.class) return 0f;
                  if (result == long.class) return 0L;
                  if (result == double.class) return 0d;
                  return null;
                });
  }

  @After
  public void restoreGraphics() {
    Gdx.graphics = previousGraphics;
  }

  @Test
  public void validatesEveryConstructorArgumentBeforeTakingDocumentOwnership() {
    UiDocument document = document();
    Stage stage = headlessStage();
    try {
      assertThrows(
          NullPointerException.class, () -> new UiDocumentComponent(stage, document, null));
      assertFalse(document.isClosed());
    } finally {
      document.close();
      stage.dispose();
    }
  }

  @Test
  public void mountsAndRefreshesPanelThenFollowsActiveAndEnabledTransitions() {
    Stage stage = headlessStage();
    UiDocument document = document();
    AtomicInteger generation = new AtomicInteger();
    UiDocumentComponent component =
        new UiDocumentComponent(stage, document, () -> panel("panel-" + generation.get()));
    try (Game game = new Game()) {
      GameObject owner = game.createObject("screen");
      owner.addComponent(component);
      game.start();
      assertSame(stage, document.root().getStage());
      assertNotNull(document.actor("panel-0"));
      assertTrue(document.root().isVisible());

      generation.incrementAndGet();
      game.step(0);
      assertNotNull(document.actor("panel-1"));
      assertNull(document.actor("panel-0"));

      stage.setKeyboardFocus(document.actor("panel-1"));
      stage.setScrollFocus(document.actor("panel-1"));
      owner.setActive(false);
      assertFalse(document.root().isVisible());
      assertNull(stage.getKeyboardFocus());
      assertNull(stage.getScrollFocus());
      owner.setActive(true);
      assertTrue(document.root().isVisible());
      component.setEnabled(false);
      assertFalse(document.root().isVisible());
      component.setEnabled(true);
      assertTrue(document.root().isVisible());
    } finally {
      stage.dispose();
    }
    assertTrue(document.isClosed());
  }

  @Test
  public void removalBeforeInitializationStillClosesOwnedDocument() {
    Stage stage = headlessStage();
    UiDocument document = document();
    UiDocumentComponent component = new UiDocumentComponent(stage, document, () -> panel("panel"));
    try (Game game = new Game()) {
      GameObject owner = game.createObject("screen");
      owner.addComponent(component);
      assertTrue(owner.removeComponent(component));
      game.start();
      assertTrue(document.isClosed());
      assertEquals(0, stage.getActors().size);
    } finally {
      stage.dispose();
    }
  }

  @Test
  public void supplierFailureStillClosesDocumentDuringSceneTeardown() {
    Stage stage = headlessStage();
    UiDocument document = document();
    RuntimeException failure = new RuntimeException("panel render failed");
    UiDocumentComponent component =
        new UiDocumentComponent(
            stage,
            document,
            () -> {
              throw failure;
            });
    Game game = new Game();
    game.createObject("screen").addComponent(component);
    assertSame(failure, assertThrows(RuntimeException.class, game::start));
    assertFalse(document.isClosed());
    game.close();
    assertTrue(document.isClosed());
    assertEquals(0, stage.getActors().size);
    stage.dispose();
  }

  private static UiDocument document() {
    return new UiDocument(YogaBackends.createDefault(), null);
  }

  private static UiNode panel(String key) {
    return UiNode.element(
        "View",
        "root",
        "",
        Map.of(),
        List.of(UiNode.element("View", key, "", Map.of(), List.of())));
  }

  private static Stage headlessStage() {
    Batch batch =
        (Batch)
            Proxy.newProxyInstance(
                Batch.class.getClassLoader(),
                new Class<?>[] {Batch.class},
                (proxy, method, args) -> {
                  Class<?> result = method.getReturnType();
                  if (result == boolean.class) return false;
                  if (result == int.class) return 0;
                  if (result == float.class) return 0f;
                  if (result == long.class) return 0L;
                  if (result == double.class) return 0d;
                  if (result == short.class) return (short) 0;
                  if (result == byte.class) return (byte) 0;
                  if (result == char.class) return (char) 0;
                  return null;
                });
    Viewport viewport =
        new Viewport() {
          {
            setCamera(new OrthographicCamera());
          }

          @Override
          public void update(int width, int height, boolean centerCamera) {}

          @Override
          public void apply(boolean centerCamera) {}
        };
    return new Stage(viewport, batch);
  }
}
