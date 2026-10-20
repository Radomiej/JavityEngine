package org.javity.gltf;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute;
import org.javity.core.*;
import org.javity.render.ShadowPolicy3D;
import org.javity.render.lighting.*;
import org.junit.Test;

public class DirectionalShadowTest {
  @Test
  public void settingsRejectInvalidVolumeAndBoundAttachmentPeak() {
    assertEquals(6L * 256 * 256, settings(256).attachmentBytes());
    assertThrows(IllegalArgumentException.class, () -> settings(0));
    assertThrows(IllegalArgumentException.class, () -> settings(4096));
    assertThrows(
        IllegalArgumentException.class,
        () -> new DirectionalShadowPass.Settings(256, 0, 8, 0, 20, 0, 0, 0, .001f));
    assertThrows(
        IllegalArgumentException.class,
        () -> new DirectionalShadowPass.Settings(256, 8, 8, 20, 20, 0, 0, 0, .001f));
    assertThrows(
        IllegalArgumentException.class,
        () -> new DirectionalShadowPass.Settings(256, 8, 8, 0, 20, Float.NaN, 0, 0, .001f));
    assertThrows(
        IllegalArgumentException.class,
        () -> new DirectionalShadowPass.Settings(256, 8, 8, 0, 20, 0, 0, 0, -.1f));
  }

  @Test
  public void policiesAreOptInAndRespectAttachmentLifecycle() {
    try (Game game = new Game()) {
      GameObject object = game.createObject("policy");
      assertNull(DirectionalShadowPass.policy(object));
      ShadowPolicy3D policy = object.addComponent(new ShadowPolicy3D(true, false));
      assertNull(DirectionalShadowPass.policy(object));
      game.start();
      game.step(0);
      assertSame(policy, DirectionalShadowPass.policy(object));
      policy.setEnabled(false);
      assertNull(DirectionalShadowPass.policy(object));
      policy.setEnabled(true);
      object.removeComponent(policy);
      assertNull(DirectionalShadowPass.policy(object));
    }
  }

  @Test
  public void selectedSunMustBeTheFirstPublishedEligibleDirectional() {
    try (Game game = new Game()) {
      RenderEnvironment3D source = game.addSystem(new RenderEnvironment3D());
      DirectionalLight3D a = sun(game, "a"), b = sun(game, "b");
      game.start();
      game.step(0);
      Environment snapshot = source.snapshot(2, 0, 0);
      DirectionalShadowPass.validateSun(a, snapshot);
      assertThrows(
          IllegalArgumentException.class, () -> DirectionalShadowPass.validateSun(b, snapshot));
      a.setEnabled(false);
      DirectionalShadowPass.validateSun(b, source.snapshot(2, 0, 0));
      snapshot.remove(DirectionalLightsAttribute.Type);
      assertThrows(
          IllegalArgumentException.class, () -> DirectionalShadowPass.validateSun(b, snapshot));
    }
  }

  @Test
  public void unavailableGl30IsAnExplicitErrorBeforeTargetAllocation() {
    var previous = com.badlogic.gdx.Gdx.gl30;
    PbrShaderProvider provider = new PbrShaderProvider(12, 2, 0, 0);
    try {
      com.badlogic.gdx.Gdx.gl30 = null;
      assertThrows(
          UnsupportedOperationException.class,
          () ->
              new DirectionalShadowPass(provider, new DirectionalLight3D(), settings(256), 12, 4));
    } finally {
      com.badlogic.gdx.Gdx.gl30 = previous;
      provider.dispose();
    }
  }

  private static DirectionalLight3D sun(Game game, String name) {
    GameObject object = game.createObject(name);
    object.addComponent(new Transform3D());
    return object.addComponent(new DirectionalLight3D());
  }

  static DirectionalShadowPass.Settings settings(int resolution) {
    return new DirectionalShadowPass.Settings(resolution, 8, 8, 0, 20, 0, 0, 0, .001f);
  }
}
