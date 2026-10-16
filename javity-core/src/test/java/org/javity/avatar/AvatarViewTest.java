package org.javity.avatar;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodeAnimation;
import com.badlogic.gdx.graphics.g3d.model.NodeKeyframe;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import java.util.*;
import org.javity.animation.AnimationMarker;
import org.javity.animation.AnimationTransition;
import org.javity.animation.ModelPoseAnimator;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.render.ModelRenderer;
import org.junit.Test;

public class AvatarViewTest {
  @Test
  public void presetsSlotsAndSourceEnabledFilterCachedPartsWithoutPruningChildren() {
    Model model = model();
    try (Game game = new Game()) {
      GameObject firstObject = game.createObject("first");
      ModelRenderer firstRenderer = firstObject.addComponent(new ModelRenderer(model));
      GameObject secondObject = game.createObject("second");
      ModelRenderer secondRenderer = secondObject.addComponent(new ModelRenderer(model));
      AvatarDefinition definition = definition();
      AvatarBinding firstBinding = definition.bind(firstRenderer);
      AvatarBinding secondBinding = definition.bind(secondRenderer);
      game.start();

      AvatarView full = firstBinding.view();
      AvatarView arms = firstBinding.view().preset(AvatarPreset.ARMS_ONLY);
      AvatarView noHead = firstBinding.view().preset(AvatarPreset.BODY_WITHOUT_HEAD);
      AvatarView customEmpty = firstBinding.view().parts(Set.of());
      assertEquals(8, gather(full).size);
      assertEquals(
          2, gather(arms).size); // hidden untagged parent, selected hand child, and right arm
      assertEquals(7, gather(noHead).size);
      assertEquals(0, gather(customEmpty).size);

      // The visible hand child survives the hidden/unselected arm parent traversal.
      assertFalse(containsPath(gather(arms), "root/left"));
      assertTrue(containsPath(gather(arms), "root/left/hand"));
      assertFalse(containsPath(gather(arms), "root/head"));
      assertFalse(containsPath(gather(arms), "root/torso"));
      arms.slotVisible("tool", true);
      assertEquals(3, gather(arms).size);
      assertTrue(containsPath(gather(arms), "root/left/hand/tool"));
      arms.slotVisible("cape", true);
      assertEquals(4, gather(arms).size);
      assertTrue(containsPath(gather(arms), "root/cape"));

      // A slot override may hide selected body clothing, and may never resurrect an excluded body
      // region.
      AvatarView shirtArms = firstBinding.view().preset(AvatarPreset.ARMS_ONLY);
      shirtArms.slotVisible("shirt", false);
      assertFalse(containsPath(gather(shirtArms), "root/torso"));
      shirtArms.slotVisible("shirt", true);
      assertFalse(containsPath(gather(shirtArms), "root/torso"));
      AvatarView shirtFull = firstBinding.view();
      assertTrue(containsPath(gather(shirtFull), "root/torso"));
      shirtFull.slotVisible("shirt", false);
      assertFalse(containsPath(gather(shirtFull), "root/torso"));

      NodePart disabled = node(firstRenderer, "root/left").parts.first();
      disabled.enabled = false;
      assertFalse(
          containsPath(gather(firstBinding.view().preset(AvatarPreset.FULL_BODY)), "root/left"));
      disabled.enabled = true;
      assertTrue(
          containsPath(gather(firstBinding.view().preset(AvatarPreset.FULL_BODY)), "root/left"));
      NodePart maskedArm = node(firstRenderer, "root/left/hand").parts.first();
      maskedArm.enabled = false;
      assertFalse(
          containsPath(
              gather(firstBinding.view().preset(AvatarPreset.ARMS_ONLY)), "root/left/hand"));
      maskedArm.enabled = true;
      assertTrue(
          containsPath(
              gather(firstBinding.view().preset(AvatarPreset.ARMS_ONLY)), "root/left/hand"));

      // Each binding has independent instance materials while its provider borrows canonical
      // instance objects.
      Material firstRootMaterial = node(firstRenderer, "root").parts.first().material;
      Material secondRootMaterial = node(secondRenderer, "root").parts.first().material;
      assertNotSame(firstRootMaterial, secondRootMaterial);
      firstRootMaterial.set(ColorAttribute.createDiffuse(Color.RED));
      assertTrue(firstRootMaterial.has(ColorAttribute.Diffuse));
      assertFalse(secondRootMaterial.has(ColorAttribute.Diffuse));
      Array<Renderable> emitted = gather(firstBinding.view());
      assertEquals(2, gather(firstBinding.view().preset(AvatarPreset.ARMS_ONLY)).size);
      assertFalse(
          containsPath(gather(firstBinding.view().preset(AvatarPreset.ARMS_ONLY)), "root/head"));
      assertFalse(
          containsPath(gather(firstBinding.view().preset(AvatarPreset.ARMS_ONLY)), "root/torso"));
      NodePart canonical = node(firstRenderer, "root").parts.first();
      assertSame(canonical.material, findByPath(emitted, "root").material);
      assertEquals("same-mesh-id", findByPath(emitted, "root").meshPart.id);
      assertSame(
          node(firstRenderer, "root/torso").parts.first().bones,
          findByPath(emitted, "root/torso").bones);

      firstBinding.close();
      assertEquals(0, gather(full).size);
      assertEquals(8, gather(secondBinding.view()).size);
      firstBinding.close();
      secondBinding.close();
    } finally {
      model.dispose();
    }
  }

  @Test
  public void directAndRendererBaseCompositionKeepViewsIsolatedAndSocketsAlive() {
    Model model = model();
    try (Game game = new Game()) {
      GameObject object = game.createObject("socket avatar");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      ModelPoseAnimator animator = object.addComponent(new ModelPoseAnimator());
      AvatarBinding binding = definition().bind(renderer);
      game.start();
      int[] markerCount = {0};
      var playback =
          animator
              .play(
                  AnimationTransition.clip("wave")
                      .loop()
                      .markers(new AnimationMarker("hand-hit", .5)))
              .onMarker("hand-hit", () -> markerCount[0]++)
              .seekNormalized(.4);
      game.step(.2f);
      playback.setSpeed(0);
      double timeBeforeViews = playback.normalizedTime();
      int markersBeforeViews = markerCount[0];

      Matrix4 socketBase = new Matrix4().setToTranslation(10, 0, 0);
      Matrix4 socket = binding.socketTransform("hand", socketBase, new Matrix4());
      assertEquals(10, socket.getTranslation(new Vector3()).x, .0001);
      assertEquals(
          node(renderer, "root/left/hand").globalTransform.getTranslation(new Vector3()).y,
          socket.getTranslation(new Vector3()).y - 1,
          .0001);
      assertEquals(2, socket.getTranslation(new Vector3()).z, .0001);

      AvatarView direct =
          binding
              .view()
              .preset(AvatarPreset.ARMS_ONLY)
              .setBaseTransform(new Matrix4().setToTranslation(100, 0, 0).rotate(Vector3.Z, 90));
      AvatarView other =
          binding
              .view()
              .preset(AvatarPreset.FULL_BODY)
              .setBaseTransform(new Matrix4().setToTranslation(-20, 0, 0));
      Array<Renderable> first = gather(direct);
      Array<Renderable> second = gather(other);
      assertEquals(2, first.size);
      assertEquals(8, second.size);
      Matrix4 expectedDirect =
          new Matrix4()
              .setToTranslation(100, 0, 0)
              .rotate(Vector3.Z, 90)
              .mul(node(renderer, "root/left/hand").globalTransform);
      assertMatrixEquals(expectedDirect, findByPath(first, "root/left/hand").worldTransform);
      assertEquals(
          -20,
          findByPath(second, "root/left/hand").worldTransform.getTranslation(new Vector3()).x,
          .0001);

      Matrix4 objectBase = new Matrix4().setToTranslation(10, 0, 0).rotate(Vector3.Z, -30);
      Array<Renderable> rendererStage = new Array<>();
      Pool<Renderable> stagePool =
          new Pool<>() {
            @Override
            protected Renderable newObject() {
              return new Renderable();
            }
          };
      direct.getRenderablesWithBase(rendererStage, stagePool, objectBase);
      Matrix4 expectedStage =
          new Matrix4(objectBase)
              .mul(new Matrix4().setToTranslation(100, 0, 0).rotate(Vector3.Z, 90))
              .mul(node(renderer, "root/left/hand").globalTransform);
      assertMatrixEquals(expectedStage, findByPath(rendererStage, "root/left/hand").worldTransform);
      Array<Renderable> skinnedStage = new Array<>();
      other.getRenderablesWithBase(skinnedStage, stagePool, objectBase);
      Matrix4 expectedSkinned =
          new Matrix4(objectBase).mul(new Matrix4().setToTranslation(-20, 0, 0));
      assertMatrixEquals(expectedSkinned, findByPath(skinnedStage, "root/torso").worldTransform);

      // Filtering and provider gathering leave sampled node transforms and canonical bone arrays
      // unchanged.
      Matrix4 handGlobal = new Matrix4(node(renderer, "root/left/hand").globalTransform);
      Matrix4[] bones = node(renderer, "root/torso").parts.first().bones;
      Matrix4[] boneValues = new Matrix4[bones.length];
      for (int i = 0; i < bones.length; i++) boneValues[i] = new Matrix4(bones[i]);
      Matrix4 socketBeforeViews = binding.socketTransform("hand", new Matrix4(), new Matrix4());
      gather(direct);
      gather(other);
      assertMatrixEquals(handGlobal, node(renderer, "root/left/hand").globalTransform);
      assertSame(bones, node(renderer, "root/torso").parts.first().bones);
      for (int i = 0; i < bones.length; i++) assertMatrixEquals(boneValues[i], bones[i]);
      assertEquals(timeBeforeViews, playback.normalizedTime(), .0001);
      assertEquals(markersBeforeViews, markerCount[0]);
      assertMatrixEquals(
          socketBeforeViews, binding.socketTransform("hand", new Matrix4(), new Matrix4()));

      object.removeComponent(renderer);
      assertEquals(0, gather(direct).size);
      assertThrows(
          IllegalStateException.class,
          () -> binding.socketTransform("hand", new Matrix4(), new Matrix4()));
      binding.close();
    } finally {
      model.dispose();
    }
  }

  @Test
  public void duplicateIdsUsedByAnimationRemappingFailAtBind() {
    Model model = model();
    try (Game game = new Game()) {
      Node other = node("other");
      Node duplicate = node("head");
      other.addChild(duplicate);
      model.nodes.first().addChild(other);
      Animation remapped = new Animation();
      remapped.id = "duplicate-track";
      remapped.duration = 1;
      remapped.nodeAnimations = new Array<>();
      NodeAnimation track = new NodeAnimation();
      track.node = duplicate;
      track.translation = new Array<>();
      track.translation.add(new NodeKeyframe<>(0, new Vector3()));
      track.translation.add(new NodeKeyframe<>(1, new Vector3(1, 0, 0)));
      remapped.nodeAnimations.add(track);
      model.animations.add(remapped);
      GameObject object = game.createObject("duplicate bind");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      assertThrows(IllegalArgumentException.class, () -> definition().bind(renderer));
    } finally {
      model.dispose();
    }
  }

  @Test
  public void duplicateIdsUsedByBoneBindMapsFailAtBind() {
    Model model = model();
    try (Game game = new Game()) {
      Node other = node("other");
      Node duplicateHead = node("head");
      other.addChild(duplicateHead);
      model.nodes.first().addChild(other);
      NodePart skinnedPart = model.nodes.first().getChild("torso", false, false).parts.first();
      skinnedPart.invBoneBindTransforms = new com.badlogic.gdx.utils.ArrayMap<>(false, 1);
      skinnedPart.invBoneBindTransforms.put(duplicateHead, new Matrix4());
      GameObject object = game.createObject("duplicate bone bind");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      assertThrows(IllegalArgumentException.class, () -> definition().bind(renderer));
    } finally {
      model.dispose();
    }
  }

  @Test
  public void builderAndSocketInputsAreSnapshottedAndSelectorsFailDuringBind() {
    Model model = model();
    try (Game game = new Game()) {
      GameObject object = game.createObject("snapshot avatar");
      ModelRenderer renderer = object.addComponent(new ModelRenderer(model));
      Matrix4 sourceOffset = new Matrix4().setToTranslation(0, 9, 3);
      AvatarDefinition.Builder builder =
          AvatarDefinition.builder()
              .part(AvatarPart.HEAD, new PartKey("root/head", 0))
              .socket("hand", "root/left/hand", sourceOffset);
      AvatarDefinition snapshot = builder.build();
      sourceOffset.setToTranslation(0, 99, 99);
      builder.socket("added-after-build", "root/left/hand", new Matrix4());
      AvatarBinding binding = snapshot.bind(renderer);
      game.start();
      Matrix4 handGlobal = node(renderer, "root/left/hand").globalTransform;
      Matrix4 expected = new Matrix4(handGlobal).mul(new Matrix4().setToTranslation(0, 9, 3));
      assertMatrixEquals(expected, binding.socketTransform("hand", new Matrix4(), new Matrix4()));
      assertThrows(
          IllegalArgumentException.class,
          () -> binding.socketTransform("added-after-build", new Matrix4(), new Matrix4()));

      assertThrows(
          IllegalArgumentException.class,
          () ->
              AvatarDefinition.builder()
                  .part(AvatarPart.HEAD, new PartKey("root/missing", 0))
                  .build()
                  .bind(renderer));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              AvatarDefinition.builder()
                  .socket("missing", "root/missing", new Matrix4())
                  .build()
                  .bind(renderer));
      binding.close();
    } finally {
      model.dispose();
    }

    Model ambiguousModel = model();
    try (Game game = new Game()) {
      ambiguousModel.nodes.first().addChild(node("head"));
      ModelRenderer renderer =
          game.createObject("ambiguous selector").addComponent(new ModelRenderer(ambiguousModel));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              AvatarDefinition.builder()
                  .part(AvatarPart.HEAD, new PartKey("root/head", 0))
                  .build()
                  .bind(renderer));
    } finally {
      ambiguousModel.dispose();
    }
  }

  @Test
  public void shaderCapacityValidationMatchesOnlyLiveSelectedProviderDraws() {
    Model model = modelWithBoneCount(13);
    try (Game game = new Game()) {
      GameObject liveObject = game.createObject("live capacity");
      ModelRenderer liveRenderer = liveObject.addComponent(new ModelRenderer(model));
      AvatarBinding liveBinding = definition().bind(liveRenderer);
      AvatarView full = liveBinding.view();
      AvatarView torsoOnly = liveBinding.view().parts(Set.of(AvatarPart.TORSO));

      GameObject disabledObject = game.createObject("disabled capacity");
      ModelRenderer disabledRenderer = disabledObject.addComponent(new ModelRenderer(model));
      AvatarBinding disabledBinding = definition().bind(disabledRenderer);
      AvatarView disabledView = disabledBinding.view();

      GameObject removedObject = game.createObject("removed capacity");
      ModelRenderer removedRenderer = removedObject.addComponent(new ModelRenderer(model));
      AvatarBinding removedBinding = definition().bind(removedRenderer);
      AvatarView removedView = removedBinding.view();
      game.start();

      assertEquals(13, node(liveRenderer, "root/head").parts.first().bones.length);
      assertThrows(IllegalArgumentException.class, () -> full.validateShaderCapacity(12, 4));
      torsoOnly.validateShaderCapacity(12, 4); // The oversized head is filtered out.

      liveBinding.close();
      assertEquals(0, gather(full).size);
      full.validateShaderCapacity(12, 4);
      assertThrows(IllegalArgumentException.class, () -> full.validateShaderCapacity(0, 4));

      disabledRenderer.setEnabled(false);
      assertEquals(0, gather(disabledView).size);
      disabledView.validateShaderCapacity(12, 4);

      removedObject.removeComponent(removedRenderer);
      assertEquals(0, gather(removedView).size);
      removedView.validateShaderCapacity(12, 4);

      disabledBinding.close();
      removedBinding.close();
    } finally {
      model.dispose();
    }
  }

  private static AvatarDefinition definition() {
    return AvatarDefinition.builder()
        .part(AvatarPart.HEAD, new PartKey("root/head", 0))
        .part(AvatarPart.TORSO, new PartKey("root/torso", 0))
        .slot("shirt", new PartKey("root/torso", 0))
        .part(AvatarPart.LEFT_ARM, new PartKey("root/left/hand", 0))
        .part(AvatarPart.RIGHT_ARM, new PartKey("root/right", 0))
        .slot("tool", new PartKey("root/left/hand", 1))
        .slot("cape", new PartKey("root/cape", 0))
        .socket("hand", "root/left/hand", new Matrix4().setToTranslation(0, 1, 2))
        .build();
  }

  private static Model model() {
    Model model = new Model();
    Node root = node("root");
    Node head = node("head");
    Node torso = node("torso");
    torso.translation.set(0, 4, 0);
    Node left = node("left");
    Node hand = node("hand");
    hand.translation.set(0, 3, 0);
    Node right = node("right");
    Node cape = node("cape");
    root.addChild(head);
    root.addChild(torso);
    root.addChild(left);
    left.addChild(hand);
    root.addChild(right);
    root.addChild(cape);
    root.translation.set(0, 2, 0);
    model.nodes.add(root);
    int partIndex = 0;
    for (Node node : List.of(root, head, torso, left, hand, right, cape)) {
      NodePart part = part("same-mesh-id", ++partIndex);
      if (node == torso) {
        part.invBoneBindTransforms = new com.badlogic.gdx.utils.ArrayMap<>(false, 1);
        part.invBoneBindTransforms.put(left, new Matrix4());
      }
      node.parts.add(part);
    }
    hand.parts.add(part("same-mesh-id", 8));
    Animation wave = new Animation();
    wave.id = "wave";
    wave.duration = 1;
    wave.nodeAnimations = new Array<>();
    NodeAnimation handTrack = new NodeAnimation();
    handTrack.node = hand;
    handTrack.translation = new Array<>();
    handTrack.translation.add(new NodeKeyframe<>(0, new Vector3(0, 3, 0)));
    handTrack.translation.add(new NodeKeyframe<>(1, new Vector3(0, 7, 0)));
    wave.nodeAnimations.add(handTrack);
    model.animations.add(wave);
    root.calculateTransforms(true);
    return model;
  }

  private static Model modelWithBoneCount(int count) {
    Model model = model();
    Node root = model.nodes.first();
    NodePart head = root.getChild("head", false, false).parts.first();
    head.invBoneBindTransforms = new com.badlogic.gdx.utils.ArrayMap<>(false, count);
    for (int i = 0; i < count; i++) {
      Node bone = node("bone" + i);
      root.addChild(bone);
      head.invBoneBindTransforms.put(bone, new Matrix4());
    }
    root.calculateTransforms(true);
    return model;
  }

  private static Node node(String id) {
    Node node = new Node();
    node.id = id;
    return node;
  }

  private static Node node(ModelRenderer renderer, String path) {
    String[] parts = path.split("/");
    Node result = renderer.instance.getNode(parts[0], false);
    for (int i = 1; i < parts.length; i++) result = result.getChild(parts[i], false, false);
    return result;
  }

  private static NodePart part(String id, int marker) {
    MeshPart meshPart = new MeshPart(id, null, 0, 0, GL20.GL_TRIANGLES);
    meshPart.center.set(marker, 0, 0);
    return new NodePart(meshPart, new Material(id));
  }

  private static Array<Renderable> gather(AvatarView view) {
    Array<Renderable> result = new Array<>();
    Pool<Renderable> pool =
        new Pool<>() {
          @Override
          protected Renderable newObject() {
            return new Renderable();
          }
        };
    view.getRenderables(result, pool);
    return result;
  }

  private static boolean containsPath(Array<Renderable> renderables, String path) {
    return findByPath(renderables, path) != null;
  }

  private static Renderable findByPath(Array<Renderable> renderables, String path) {
    int marker =
        switch (path) {
          case "root" -> 1;
          case "root/head" -> 2;
          case "root/torso" -> 3;
          case "root/left" -> 4;
          case "root/left/hand" -> 5;
          case "root/right" -> 6;
          case "root/cape" -> 7;
          case "root/left/hand/tool" -> 8;
          default -> -1;
        };
    for (Renderable renderable : renderables) {
      if (renderable.meshPart.center.x == marker) return renderable;
    }
    return null;
  }

  private static void assertMatrixEquals(Matrix4 expected, Matrix4 actual) {
    assertArrayEquals(expected.val, actual.val, .0001f);
  }
}
