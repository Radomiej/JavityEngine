package org.javity.animation;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.*;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import org.junit.Test;

public class ExactPoseCacheTest {
  private SharedPoseData data(Object source, long revision) {
    Model model = new Model();
    Node node = new Node();
    node.id = "root";
    model.nodes.add(node);
    return SharedPoseData.capture(
        new ModelInstance(model), source, revision, new float[] {0, 0, 0, 0, 0, 0, 1, 1, 1, 1});
  }

  @Test
  public void immutablePoseViewSharesOnHitsWithoutExposingSupplierOrResultArrays() {
    ExactPoseCache cache = new ExactPoseCache(1);
    ExactPoseKey key = ExactPoseKey.builder(data(new Object(), 0)).build();
    float[] original = {3, 4};
    ExactPoseCache.Pose pose = cache.resolvePose(key, () -> original);
    original[0] = 9;
    assertEquals(3, pose.component(0), 0);
    assertEquals(2, pose.size());
    assertSame(
        pose,
        cache.resolvePose(
            key,
            () -> {
              throw new AssertionError();
            }));
    float[] publicCopy =
        cache.resolve(
            key,
            () -> {
              throw new AssertionError();
            });
    publicCopy[0] = 8;
    assertEquals(3, pose.component(0), 0);
    assertEquals(1, cache.stats().totalSamplerExecutions());
  }

  @Test
  public void collisionDoesNotShare() {
    SharedPoseData data = data(new Object(), 0);
    ExactPoseKey a =
        ExactPoseKey.builder(data).floats(new float[] {0, Float.intBitsToFloat(31)}).build();
    ExactPoseKey b =
        ExactPoseKey.builder(data).floats(new float[] {Float.intBitsToFloat(1), 0}).build();
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, b);
    ExactPoseCache cache = new ExactPoseCache(2);
    assertEquals(1, cache.resolve(a, () -> new float[] {1})[0], 0);
    assertEquals(2, cache.resolve(b, () -> new float[] {2})[0], 0);
  }

  @Test
  public void exactTimeDoesNotQuantize() {
    SharedPoseData data = data(new Object(), 0);
    double time = .5;
    double next = Math.nextUp(time);
    assertEquals((float) time, (float) next, 0);
    assertNotEquals(
        ExactPoseKey.builder(data).number(time).build(),
        ExactPoseKey.builder(data).number(next).build());
    assertNotEquals(
        ExactPoseKey.builder(data).number(0.).build(),
        ExactPoseKey.builder(data).number(-0.).build());
  }

  @Test
  public void rigRevisionAndFrozenFade() {
    Object source = new Object();
    SharedPoseData data = data(source, 0);
    ExactPoseKey key =
        ExactPoseKey.builder(data)
            .mask(new boolean[] {true})
            .number(.5)
            .floats(new float[] {1})
            .number(.25)
            .build();
    assertNotEquals(
        key,
        ExactPoseKey.builder(data(source, 1))
            .mask(new boolean[] {true})
            .number(.5)
            .floats(new float[] {1})
            .number(.25)
            .build());
    assertNotEquals(data, data(new Object(), 0));
    assertNotEquals(
        key,
        ExactPoseKey.builder(data)
            .mask(new boolean[] {true})
            .number(.5)
            .floats(new float[] {1})
            .number(.5)
            .build());
    float[] frozen = {1};
    ExactPoseKey copy = ExactPoseKey.builder(data).floats(frozen).build();
    frozen[0] = 2;
    assertEquals(copy, ExactPoseKey.builder(data).floats(new float[] {1}).build());
  }

  @Test
  public void boundedFrameLifetime() {
    ExactPoseCache cache = new ExactPoseCache(1);
    SharedPoseData data = data(new Object(), 0);
    ExactPoseKey a = ExactPoseKey.builder(data).number(0).build(),
        b = ExactPoseKey.builder(data).number(1).build();
    cache.beginFrame(3);
    float[] source = {4};
    float[] result = cache.resolve(a, () -> source);
    source[0] = 8;
    result[0] = 9;
    assertEquals(
        4,
        cache
            .resolve(
                a,
                () -> {
                  throw new AssertionError();
                })[0],
        0);
    cache.resolve(b, () -> new float[] {2});
    cache.resolve(b, () -> new float[] {2});
    assertEquals(1, cache.stats().retainedEntries());
    assertEquals(3, cache.stats().frameSamplerExecutions());
    cache.beginFrame(3);
    assertEquals(1, cache.stats().frameHits());
    cache.beginFrame(4);
    assertEquals(0, cache.stats().retainedEntries());
    assertEquals(0, cache.stats().frameMisses());
    assertEquals(3, cache.stats().totalSamplerExecutions());
    cache.clear();
    assertEquals(0, cache.stats().retainedEntries());
  }

  @Test
  public void snapshotsActualInstanceAndOnlyRequestedClips() {
    Model model = new Model();
    Node root = new Node();
    root.id = "root";
    model.nodes.add(root);
    Animation valid = new Animation();
    valid.id = "ok";
    valid.duration = 1;
    NodeAnimation track = new NodeAnimation();
    track.node = root;
    track.translation = new Array<>();
    track.translation.add(new NodeKeyframe<>(0, new Vector3(1, 2, 3)));
    valid.nodeAnimations.add(track);
    model.animations.add(valid);
    Animation invalid = new Animation();
    invalid.id = "unused";
    invalid.duration = -1;
    model.animations.add(invalid);
    ModelInstance instance = new ModelInstance(model);
    float[] bind = {0, 0, 0, 0, 0, 0, 1, 1, 1, 1};
    SharedPoseData first = SharedPoseData.capture(instance, model, 0, bind, "ok");
    instance.animations.first().nodeAnimations.first().translation.first().value.x = 7;
    SharedPoseData changed = SharedPoseData.capture(instance, model, 0, bind, "ok");
    assertNotEquals(first, changed);
    assertEquals(1, first.clip(0).track(0).translation().component(0, 0), 0);
    instance.animations.first().nodeAnimations.first().node = root;
    assertThrows(
        IllegalArgumentException.class,
        () -> SharedPoseData.capture(instance, model, 0, bind, "ok"));
  }
}
