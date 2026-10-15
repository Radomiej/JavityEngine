package org.javity.services;

import static org.junit.Assert.*;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import java.lang.reflect.Proxy;
import org.javity.audio.AudioSource;
import org.javity.core.*;
import org.javity.input.*;
import org.javity.render.*;
import org.junit.Test;

public class AudioAndPickingTest {
  @Test
  public void audioStopsOnlyItsVoicesAndClosesOwnedBeforeStart() {
    int[] stopped = {0}, disposed = {0};
    long[] ids = {0};
    Sound s =
        (Sound)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] {Sound.class},
                (o, m, a) -> {
                  if (m.getName().equals("play") || m.getName().equals("loop")) return ++ids[0];
                  if (m.getName().equals("stop")) {
                    assertEquals(1, a.length);
                    stopped[0]++;
                  }
                  if (m.getName().equals("dispose")) disposed[0]++;
                  return null;
                });
    Game g = new Game();
    AudioSource audio = g.createObject("sound").addComponent(new AudioSource(s, true));
    audio.setMaxVoices(2);
    audio.play();
    audio.play();
    audio.play();
    assertEquals(1, stopped[0]);
    g.dispose();
    assertEquals(3, stopped[0]);
    assertEquals(1, disposed[0]);
    g.dispose();
    assertEquals(1, disposed[0]);
  }

  @Test
  public void cleanupContinuesAfterComponentThrows() {
    int[] closed = {0};
    Game g = new Game();
    var o = g.createObject("fault");
    o.addComponent(
        new Component() {
          public void onDestroy() {
            throw new IllegalStateException("bad");
          }
        });
    o.addComponent(
        new Component() {
          public void onDestroy() {
            closed[0]++;
          }
        });
    g.start();
    assertThrows(IllegalStateException.class, g::dispose);
    assertEquals(1, closed[0]);
  }

  @Test
  public void pickerHonorsTopmostRotationAndDestroyedCapture() {
    try (Game g = new Game()) {
      Camera2D c = g.createObject("camera").setPosition(100, 100).addComponent(new Camera2D());
      c.resize(200, 200);
      int[] hits = {0};
      g.createObject("under")
          .setPosition(100, 100)
          .addComponent(new Pickable2D(80, 80, p -> hits[0] += 10));
      var top = g.createObject("top").setPosition(100, 100);
      var hit = top.addComponent(new Pickable2D(40, 20, p -> hits[0]++));
      hit.layer = 1;
      top.transform.rotation = (float) Math.PI / 2;
      g.start();
      PointerPicker picker = new PointerPicker(g, c);
      assertSame(hit, picker.pick(new Vector2(100, 115)));
      assertTrue(picker.touchDown(100, 100, 0, 0));
      picker.touchUp(100, 100, 0, 0);
      assertEquals(1, hits[0]);
      picker.touchDown(100, 100, 0, 0);
      top.destroy();
      picker.touchUp(100, 100, 0, 0);
      assertEquals(1, hits[0]);
    }
  }

  @Test
  public void spriteAnimationUsesWorldTime() {
    try (Game g = new Game()) {
      var o = g.createObject("sprite");
      TextureRegion a = new TextureRegion(), b = new TextureRegion();
      var sprite = o.addComponent(new SpriteRenderer2D(a));
      o.addComponent(new SpriteAnimator(java.util.List.of(a, b), .1f, true));
      g.start();
      g.step(.1f);
      assertSame(b, sprite.region);
      g.pause();
      g.step(.1f);
      assertSame(b, sprite.region);
      g.resume();
      g.step(.1f);
      assertSame(a, sprite.region);
    }
  }
}
