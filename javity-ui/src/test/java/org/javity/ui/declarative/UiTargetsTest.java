package org.javity.ui.declarative;

import static org.junit.Assert.*;

import com.badlogic.gdx.scenes.scene2d.Actor;
import java.util.List;
import java.util.Map;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.effects.FloatTarget;
import org.javity.ui.effects.UiTargets;
import org.junit.Test;

public class UiTargetsTest {
  @Test
  public void alphaTargetTracksOnlyTheOriginalKeyedActorAndValidatesWrites() {
    try (Game game = new Game();
        UiDocument document = new UiDocument(null)) {
      GameObject owner = game.createObject("ui-effects");
      document.render(view("screen", view("fader")));
      Actor original = document.actor("fader");
      original.setColor(1, 1, 1, .8f);
      FloatTarget alpha = UiTargets.alpha(owner, document, "fader");

      assertSame(owner, alpha.gameObject());
      assertSame(original, alpha.identity());
      assertEquals("ui.alpha", alpha.propertyKey());
      assertTrue(alpha.isValid());
      assertEquals(.8f, alpha.get(), .001f);
      alpha.set(.25f);
      assertEquals(.25f, original.getColor().a, .001f);
      assertThrows(IllegalArgumentException.class, () -> alpha.set(Float.NaN));
      assertThrows(IllegalArgumentException.class, () -> alpha.set(-.01f));
      assertThrows(IllegalArgumentException.class, () -> alpha.set(1.01f));

      document.render(view("screen", view("replacement")));
      assertFalse(alpha.isValid());
      assertThrows(IllegalStateException.class, () -> alpha.set(.5f));
      assertThrows(
          IllegalArgumentException.class, () -> UiTargets.alpha(owner, document, "missing"));
    }
  }

  @Test
  public void closeAndOwnerDestructionInvalidateTarget() {
    Game game = new Game();
    GameObject destroyedOwner = game.createObject("destroyed-owner");
    GameObject liveOwner = game.createObject("live-owner");
    UiDocument document = new UiDocument(null);
    document.render(view("screen", view("fade")));
    FloatTarget ownerTarget = UiTargets.alpha(destroyedOwner, document, "fade");
    FloatTarget closeTarget = UiTargets.alpha(liveOwner, document, "fade");
    destroyedOwner.destroy();
    assertFalse(ownerTarget.isValid());
    assertThrows(IllegalStateException.class, ownerTarget::get);
    assertTrue(closeTarget.isValid());
    document.close();
    assertFalse(closeTarget.isValid());
    assertThrows(IllegalStateException.class, closeTarget::get);
    game.close();
  }

  private static UiNode view(String key, UiNode... children) {
    return UiNode.element("View", key, "", Map.of(), List.of(children));
  }
}
