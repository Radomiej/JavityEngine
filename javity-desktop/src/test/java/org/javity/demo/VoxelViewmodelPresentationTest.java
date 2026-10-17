package org.javity.demo;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import org.junit.Test;

public class VoxelViewmodelPresentationTest {
  @org.junit.BeforeClass
  public static void loadNatives() {
    com.badlogic.gdx.utils.GdxNativesLoader.load();
  }

  @Test
  public void baseIsFiniteAndStable() {
    VoxelViewmodelPresentation presentation = new VoxelViewmodelPresentation();
    Matrix4 target = new Matrix4();
    assertSame(target, presentation.writeBase(target));
    float[] first = target.val.clone();
    for (float value : first) assertTrue(Float.isFinite(value));
    assertTrue(Math.abs(target.det()) > .01f);
    target.translate(100, -200, 300).scale(2, 3, 4);
    presentation.writeBase(target);
    assertArrayEquals(first, target.val, 0);
  }

  @Test
  public void doesNotMutateCallerPose() {
    Matrix4 canonicalPose = new Matrix4().translate(8, 12, -4).rotate(Vector3.Y, 73).scale(.9f, .9f, .9f);
    float[] before = canonicalPose.val.clone();
    Matrix4 viewBase = new Matrix4(canonicalPose);
    VoxelViewmodelPresentation presentation = new VoxelViewmodelPresentation();
    presentation.writeBase(viewBase);
    // Rendering the same canonical bone in another view may compose this base, never edit it.
    new Matrix4(viewBase).mul(canonicalPose);
    assertArrayEquals(before, canonicalPose.val, 0);
    assertFalse(viewBase.equals(canonicalPose));
  }

  @Test
  public void rightHandAndPickaxeFitProjectedLowerRight() {
    for (int width : new int[] {1280, 960}) {
      PerspectiveCamera camera = new PerspectiveCamera(72, width, 720);
      camera.near = .03f;
      camera.far = 240;
      camera.update();
      Matrix4 base = new VoxelViewmodelPresentation().writeBase(new Matrix4());
      assertBoxFits(camera, base, GeneratedVoxelAvatar.RIGHT_HAND);
      for (var box : GeneratedVoxelAvatar.PICKAXE) assertBoxFits(camera, base, box);
    }
  }

  private static void assertBoxFits(PerspectiveCamera camera, Matrix4 base,
      GeneratedVoxelAvatar.AuthoredBox box) {
    for (int corner = 0; corner < 8; corner++) {
      Vector3 local = new Vector3(
          box.x() + ((corner & 1) == 0 ? 0 : box.w()),
          box.y() + ((corner & 2) == 0 ? 0 : box.h()),
          box.z() + ((corner & 4) == 0 ? 0 : box.d()));
      Vector3 point = local.mul(base);
      assertTrue("Near-plane intersection: " + point, -point.z > camera.near + .1f);
      camera.project(point, 0, 0, camera.viewportWidth, camera.viewportHeight);
      float x = point.x / camera.viewportWidth, y = point.y / camera.viewportHeight;
      assertTrue("Projected x=" + x + " at width " + camera.viewportWidth, x >= .55f && x <= .95f);
      assertTrue("Projected y=" + y + " at width " + camera.viewportWidth, y >= .05f && y <= .65f);
    }
  }
}
