package org.javity.gltf;

import static org.junit.Assert.*;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.*;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import net.mgsx.gltf.scene3d.scene.*;
import org.javity.assets.AssetLease;
import org.javity.serialization.AssetId;
import org.junit.Test;

public class GltfAssetTest {
  @Test
  public void instancesCopyPoseAndDeferIdempotentAssetDisposal() {
    AtomicInteger disposed = new AtomicInteger();
    Model model = new Model();
    model.manageDisposable(disposed::incrementAndGet);
    Node joint = new Node();
    joint.id = "joint";
    model.nodes.add(joint);
    Animation animation = new Animation();
    animation.id = "move";
    animation.duration = 1;
    NodeAnimation nodeAnimation = new NodeAnimation();
    nodeAnimation.node = joint;
    nodeAnimation.translation =
        Array.with(
            new NodeKeyframe<>(0, new Vector3()), new NodeKeyframe<>(1, new Vector3(0, 2, 0)));
    animation.nodeAnimations.add(nodeAnimation);
    model.animations.add(animation);
    SceneModel scene = new SceneModel();
    scene.model = model;
    SceneAsset imported = new SceneAsset();
    imported.scene = scene;
    imported.scenes = Array.with(scene);
    GltfAsset asset = new GltfAsset(imported);
    GltfInstance a = asset.instantiate(), b = asset.instantiate();
    AnimationController controller = new AnimationController(a.modelInstance());
    controller.setAnimation("move", -1);
    controller.update(.5f);
    assertEquals(
        1,
        a.modelInstance().getNode("joint").localTransform.getTranslation(new Vector3()).y,
        .001f);
    assertEquals(
        0,
        b.modelInstance().getNode("joint").localTransform.getTranslation(new Vector3()).y,
        .001f);
    asset.close();
    asset.close();
    assertEquals(0, disposed.get());
    assertThrows(IllegalStateException.class, asset::instantiate);
    a.close();
    a.close();
    assertEquals(0, disposed.get());
    b.close();
    assertEquals(1, disposed.get());
    assertTrue(asset.isDisposed());
    assertThrows(IllegalStateException.class, b::modelInstance);
  }

  @Test
  public void assetCleanupContinuesAcrossScenesAfterFailure() {
    AtomicInteger released = new AtomicInteger();
    RuntimeException expected = new IllegalStateException("scene cleanup failed");
    SceneModel first =
        new SceneModel() {
          @Override
          public void dispose() {
            throw expected;
          }
        };
    first.model = new Model();
    SceneModel second =
        new SceneModel() {
          @Override
          public void dispose() {
            released.incrementAndGet();
          }
        };
    SceneAsset imported = new SceneAsset();
    imported.scene = first;
    imported.scenes = Array.with(first, second);
    GltfAsset asset = new GltfAsset(imported);
    assertSame(expected, assertThrows(RuntimeException.class, asset::close));
    assertEquals(1, released.get());
    asset.close();
    assertEquals(1, released.get());
  }

  @Test
  public void failedLeaseIsTypedStableAndRepeatableAfterRelease() throws Exception {
    Path file = Files.createTempFile("invalid-gltf", ".glb");
    try (GltfAssets assets = new GltfAssets(12)) {
      AssetId<GltfAsset> id = new AssetId<>("character/player");
      assets.register(id, new FileHandle(file.toFile()));
      assertThrows(
          IllegalArgumentException.class, () -> assets.register(id, new FileHandle(file.toFile())));
      AssetLease<GltfAsset> lease = assets.request(id);
      assertEquals(AssetLease.State.FAILED, lease.state());
      assertNotNull(lease.failure());
      assertThrows(IllegalStateException.class, lease::get);
      lease.close();
      lease.close();
      assertEquals(AssetLease.State.CLOSED, lease.state());
      try (AssetLease<GltfAsset> retry = assets.request(id)) {
        assertEquals(AssetLease.State.FAILED, retry.state());
      }
    } finally {
      Files.delete(file);
    }
  }

  @Test
  public void validFixturesPassCpuPreflightAndBoneCapacityIsExplicit() {
    FileHandle staticFile =
        new FileHandle("src/main/resources/org/javity/gltf/fixtures/static.glb");
    FileHandle skinnedFile =
        new FileHandle("src/main/resources/org/javity/gltf/fixtures/skinned.glb");
    GltfDocument.validate(staticFile, 12);
    GltfDocument.validate(skinnedFile, 2);
    assertThrows(IllegalArgumentException.class, () -> GltfDocument.validate(skinnedFile, 1));
  }

  @Test
  public void animationNamesIncludeUpstreamGeneratedIdsInBothCollisionOrders() throws Exception {
    Path file = Files.createTempFile("clip-names-gltf", ".gltf");
    try {
      for (String animations :
          new String[] {"[{}, {\"name\":\"animation0\"}]", "[{\"name\":\"animation1\"}, {}]"}) {
        Files.writeString(
            file, "{\"asset\":{\"version\":\"2.0\"},\"animations\":" + animations + "}");
        assertThrows(
            IllegalArgumentException.class,
            () -> GltfDocument.validate(new FileHandle(file.toFile()), 12));
      }
      Files.writeString(file, "{\"asset\":{\"version\":\"2.0\"},\"animations\":[{},{}]}");
      GltfDocument.validate(new FileHandle(file.toFile()), 12);
    } finally {
      Files.delete(file);
    }
  }

  @Test
  public void textureChannelsSharingUvRequireMatchingEffectiveTransforms() throws Exception {
    Path file = Files.createTempFile("uv-transform-gltf", ".gltf");
    try {
      String shifted =
          "{\"index\":0,\"extensions\":{\"KHR_texture_transform\":{\"offset\":[0.2,0.3],\"scale\":[0.8,0.9],\"rotation\":0.4}}}";
      String defaultTexture = "{\"index\":0}";
      String separateUv =
          "{\"index\":0,\"extensions\":{\"KHR_texture_transform\":{\"texCoord\":1}}}";
      for (String second : new String[] {defaultTexture, shifted, separateUv}) {
        String material =
            "{\"normalTexture\":"
                + second
                + ",\"pbrMetallicRoughness\":{\"baseColorTexture\":"
                + shifted
                + "}}";
        Files.writeString(
            file, "{\"asset\":{\"version\":\"2.0\"},\"materials\":[" + material + "]}");
        if (second.equals(defaultTexture))
          assertThrows(
              IllegalArgumentException.class,
              () -> GltfDocument.validate(new FileHandle(file.toFile()), 12));
        else GltfDocument.validate(new FileHandle(file.toFile()), 12);
      }
    } finally {
      Files.delete(file);
    }
  }

  @Test
  public void invalidEnvelopeAndUnsupportedFeaturesFailBeforeGpuAllocation() throws Exception {
    for (String json :
        new String[] {
          "{}",
          "{\"asset\":{\"version\":\"1.0\"}}",
          "{\"asset\":{\"version\":\"2.0\"},\"extensionsUsed\":[\"KHR_materials_transmission\"]}",
          "{\"asset\":{\"version\":\"2.0\"},\"animations\":[{\"samplers\":[{\"interpolation\":\"STEP\"}]}]}",
          "{\"asset\":{\"version\":\"2.0\"},\"nodes\":[{\"name\":\"same\"},{\"name\":\"same\"}]}"
        }) {
      Path file = Files.createTempFile("unsupported-gltf", ".gltf");
      try {
        Files.writeString(file, json);
        assertThrows(
            RuntimeException.class, () -> GltfDocument.validate(new FileHandle(file.toFile()), 12));
      } finally {
        Files.delete(file);
      }
    }
    Path file = Files.createTempFile("broken-gltf", ".glb");
    try {
      Files.write(file, new byte[20]);
      assertThrows(
          IllegalArgumentException.class,
          () -> GltfDocument.validate(new FileHandle(file.toFile()), 12));
    } finally {
      Files.delete(file);
    }
  }
}
