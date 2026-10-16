package org.javity.ui.inspector;

import static org.junit.Assert.*;
import org.javity.core.*;
import org.javity.effects.Clock;
import org.javity.presentation.*;
import org.junit.Test;

public class FeelPreviewSessionTest {
  private final GameObject[] selected = new GameObject[1];

  private GameObject target(Game game) {
    GameObject o = game.createObject("preview");
    o.addComponent(new Transform3D());
    o.addComponent(new Presentation3D());
    o.addComponent(new PresentationEffects3D(Clock.WORLD));
    o.addComponent(new PresentationEffects3D(Clock.UI));
    selected[0] = o;
    return o;
  }

  private PresentationEffects3D.Preset preset() {
    return PresentationEffects3D.punchPosition(20, 0, 0, 2, .7, 1);
  }

  private double x(GameObject o) {
    var pose = new Presentation3D.Pose();
    o.getComponent(Presentation3D.class).snapshot(pose);
    return pose.translationX();
  }

  @Test public void closeCancelsOnlyOwnedPlayback() {
    try (Game game = new Game()) {
      GameObject o = target(game); game.start();
      var session = new FeelPreviewSession(() -> selected[0]);
      assertTrue(session.play(preset()));
      var newer = o.getComponent(PresentationEffects3D.class).play(preset());
      session.close(); session.close(); session.stop();
      assertTrue(newer.isActive());
      assertEquals(1, o.getComponent(PresentationEffects3D.class).activeCount());
      assertFalse(session.play(preset()));
    }
  }

  @Test public void selectionChangeStopsPreview() {
    try (Game game = new Game()) {
      GameObject first = target(game); game.start();
      var session = new FeelPreviewSession(() -> selected[0]);
      session.play(preset());
      selected[0] = game.createObject("other"); session.update();
      assertEquals(0, first.getComponent(PresentationEffects3D.class).activeCount());
      assertFalse(session.isPlaying());
    }
  }

  @Test public void worldPauseDoesNotAdvanceWorldPreset() {
    try (Game game = new Game()) {
      GameObject o = target(game); game.start();
      var session = new FeelPreviewSession(() -> selected[0]);
      assertTrue(session.play(preset())); game.step(.05f);
      double before = x(o); game.pause(); game.step(.1f); session.update();
      assertEquals(before, x(o), 0); assertTrue(session.isPlaying());
    }
  }

  @Test public void uiPresetContinuesWhileWorldPaused() {
    try (Game game = new Game()) {
      GameObject o = target(game); game.start(); game.pause();
      var session = new FeelPreviewSession(() -> selected[0]);
      session.setClock(Clock.UI); assertTrue(session.play(preset()));
      game.step(.1f); session.update(); assertTrue(Math.abs(x(o)) > .1);
      assertEquals(0, o.getComponent(Transform3D.class).position.x, 0);
    }
  }

  @Test public void wrongSurfaceRefusesClearly() {
    try (Game game = new Game()) {
      GameObject o = target(game); game.start();
      var session = new FeelPreviewSession(() -> selected[0]);
      assertFalse(session.play(PresentationEffects3D.fovKick(
          PresentationEffects3D.Surface.WORLD_CAMERA, 8, 2, .7, 1)));
      assertTrue(session.status().contains("CameraPresentation3D"));
      assertEquals(0, o.getComponent(PresentationEffects3D.class).activeCount());
    }
  }

  @Test public void missingClockDoesNotInstallComponentsAndClockChangeStopsOwnedOnly() {
    try (Game game = new Game()) {
      GameObject o = game.createObject("world only"); selected[0] = o;
      o.addComponent(new Transform3D());
      o.addComponent(new Presentation3D());
      var manager = o.addComponent(new PresentationEffects3D(Clock.WORLD)); game.start();
      var session = new FeelPreviewSession(() -> selected[0]);
      session.play(preset()); var external = manager.play(preset());
      session.setClock(Clock.UI); assertTrue(external.isActive());
      assertFalse(session.play(preset()));
      assertTrue(session.status().contains("UI")); assertEquals(3, o.getComponents().size());
    }
  }

  @Test public void pauseResumeAndProviderRemovalRemainOwned() {
    try (Game game = new Game()) {
      GameObject o = target(game); game.start();
      var session = new FeelPreviewSession(() -> selected[0]); session.play(preset());
      game.step(.05f); session.pause(); double before = x(o);
      game.step(.1f); assertEquals(before, x(o), 0);
      session.resume(); game.step(.05f); assertNotEquals(before, x(o), 1e-5);
      o.removeComponent(o.getComponent(Presentation3D.class)); session.update();
      assertFalse(session.isPlaying()); session.pause(); session.resume(); session.close();
    }
  }
}
