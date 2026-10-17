package org.javity.ui.flex;

import static org.junit.Assert.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import java.lang.reflect.Proxy;
import org.junit.Test;

public class ScrollContentTest {
  @Test
  public void flexScrollContentMeasuresIntrinsicHeightAtViewportWidth() {
    Application original = Gdx.app;
    Gdx.app = applicationStub();
    try (Flex content = new Flex("flex-col shrink-0")) {
      Flex body = content.add(new Flex("h-100 w-full shrink-0"), "shrink-0");
      ScrollPane pane = new ScrollPane(content, new ScrollPane.ScrollPaneStyle());
      pane.setSize(160, 120);
      pane.validate();

      assertEquals(400, content.getPrefHeight(), .01f);
      assertEquals(400, content.getHeight(), .01f);
      assertTrue(pane.getMaxY() >= 280);
      float startY = content.getY();
      pane.setScrollY(80);
      pane.updateVisualScroll();
      pane.layout();
      assertEquals(80, pane.getScrollY(), .01f);
      assertNotEquals(startY, content.getY(), .01f);
      assertEquals(160, body.getWidth(), .01f);
    } finally {
      Gdx.app = original;
    }
  }

  private static Application applicationStub() {
    return (Application)
        Proxy.newProxyInstance(
            ScrollContentTest.class.getClassLoader(),
            new Class<?>[] {Application.class},
            (proxy, method, args) ->
                method.getName().equals("getType") ? Application.ApplicationType.Desktop : null);
  }
}
