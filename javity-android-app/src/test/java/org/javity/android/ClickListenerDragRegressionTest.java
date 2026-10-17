package org.javity.android;

import static org.junit.Assert.*;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import org.junit.Test;

/** Locks down Scene2D's actual click semantics used by the declarative Android HUD Button. */
public final class ClickListenerDragRegressionTest {
  @Test
  public void insideButtonDragStillClicksButMeasuredOutsideReleaseDoesNot() {
    Actor button = new Actor();
    button.setBounds(0f, 0f, 200f, 44f);
    InputEvent event = new InputEvent();
    event.setListenerActor(button);
    int[] clicks = {0};
    ClickListener listener =
        new ClickListener() {
          @Override
          public void clicked(InputEvent event, float x, float y) {
            clicks[0]++;
          }
        };

    assertTrue(listener.touchDown(event, 100f, 22f, 0, 0));
    listener.touchDragged(event, 124f, 30f, 0); // current center-to-(+24,+8) gesture is inside.
    listener.touchUp(event, 124f, 30f, 0, 0);
    assertEquals("inside-button drag invokes ClickListener", 1, clicks[0]);

    assertTrue(listener.touchDown(event, 100f, 22f, 0, 0));
    listener.touchDragged(event, 202f, 22f, 0); // release outside measured actor bounds.
    listener.touchUp(event, 202f, 22f, 0, 0);
    assertEquals("outside release must not invoke the action", 1, clicks[0]);
  }
}
