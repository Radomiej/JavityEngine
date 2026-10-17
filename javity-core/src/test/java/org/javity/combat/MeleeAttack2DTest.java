package org.javity.combat;

import static org.junit.Assert.*;

import org.javity.core.Game;
import org.junit.Test;

public class MeleeAttack2DTest {
  @Test
  public void largeFiniteDirectionNormalizesAndDisabledStrikeCannotHit() {
    try (var game = new Game()) {
      var attack = game.createObject("hero").addComponent(new MeleeAttack2D(100, .4f, .2f, 90));
      game.start();
      assertTrue(attack.tryBegin(Float.MAX_VALUE, Float.MAX_VALUE));
      assertTrue(attack.intersects(50, 50, 0));
      attack.setEnabled(false);
      assertFalse(attack.intersects(50, 50, 0));
    }
  }

  @Test
  public void attackUsesSceneTimeAndRejectsRepeatingBeforeCooldown() {
    try (var game = new Game()) {
      var attack = game.createObject("hero").addComponent(new MeleeAttack2D(100, .4f, .2f, 100));
      game.start();
      assertTrue(attack.tryBegin(1, 0));
      assertFalse(attack.tryBegin(1, 0));
      game.pause();
      for (int i = 0; i < 10; i++) game.step(.1f);
      assertFalse(attack.tryBegin(1, 0));
      game.resume();
      for (int i = 0; i < 5; i++) game.step(.1f);
      assertTrue(attack.tryBegin(0, 1));
    }
  }

  @Test
  public void strikeRejectsBehindOutOfReachAndInvalidDirection() {
    try (var game = new Game()) {
      var attack =
          game.createObject("hero")
              .setPosition(20, 30)
              .addComponent(new MeleeAttack2D(100, .4f, .2f, 90));
      game.start();
      assertFalse(attack.tryBegin(0, 0));
      assertTrue(attack.tryBegin(1, 0));
      assertTrue(attack.intersects(90, 30, 0));
      assertFalse(attack.intersects(-40, 30, 0));
      assertFalse(attack.intersects(130, 30, 0));
      assertTrue(attack.intersects(125, 30, 10));
      for (int i = 0; i < 3; i++) game.step(.1f);
      assertFalse(attack.intersects(90, 30, 0));
    }
  }
}
