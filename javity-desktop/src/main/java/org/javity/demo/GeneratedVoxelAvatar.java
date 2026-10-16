package org.javity.demo;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodeAnimation;
import com.badlogic.gdx.graphics.g3d.model.NodeKeyframe;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ArrayMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.javity.animation.LinearModelMixer;
import org.javity.animation.ModelAnimationLayer;
import org.javity.animation.ModelPoseAnimator;
import org.javity.animation.ModelPosePlayback;
import org.javity.animation.NodeMask;
import org.javity.animation.PlayMode;
import org.javity.avatar.AvatarBinding;
import org.javity.avatar.AvatarDefinition;
import org.javity.avatar.AvatarMeshPreparator;
import org.javity.avatar.AvatarPart;
import org.javity.avatar.PartKey;
import org.javity.avatar.PreparedAvatarGeometry;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.render.ModelRenderer;

/** Original procedural voxel character; authored weighted body triangles share one pose owner. */
final class GeneratedVoxelAvatar implements AutoCloseable {
  final Model model;
  final Game game;
  final Texture texture;
  final PreparedAvatarGeometry geometry;
  final GameObject object;
  final ModelRenderer renderer;
  final AvatarBinding binding;
  final AvatarDefinition definition;
  final ModelAnimationLayer useLayer;
  private final ModelPosePlayback locomotion;
  private final ModelAnimationLayer jumpLayer;
  private boolean jumpWasAirborne;
  private boolean jumpFading;
  private final Mesh shirtMesh, toolMesh;
  private float walkParameter = Float.NaN;
  private boolean useFading;
  private int markerCount;
  private boolean closed;

  static GeneratedVoxelAvatar create(Game game, float scale) {
    return new GeneratedVoxelAvatar(game, scale);
  }

  private GeneratedVoxelAvatar(Game game, float scale) {
    Model source = null;
    Texture sourceTexture = null;
    PreparedAvatarGeometry prepared = null;
    GameObject createdObject = null;
    AvatarBinding createdBinding = null;
    Mesh createdShirt = null, createdTool = null;
    try {
      sourceTexture = createTexture();
      source = new Model();
      Material skinMaterial = new Material(TextureAttribute.createDiffuse(sourceTexture));
      source.materials.add(skinMaterial);

      List<Float> vertices = new ArrayList<>();
      List<Integer> indices = new ArrayList<>();
      Map<String, List<Integer>> authored = new LinkedHashMap<>();
      addBodyCube(vertices, indices, authored, "head", -.25f, 1.45f, -.25f, .5f, .5f, .5f, 0, 0);
      addBodyCube(vertices, indices, authored, "torso", -.3f, .75f, -.16f, .6f, .68f, .32f, 1, 0);
      addBodyCube(
          vertices, indices, authored, "left-arm", -.57f, .57f, -.14f, .28f, .7f, .28f, 1, 0);
      addBodyCube(
          vertices, indices, authored, "left-arm", -.57f, .25f, -.14f, .28f, .34f, .28f, 0, 0);
      addBodyCube(
          vertices, indices, authored, "right-arm", .29f, .57f, -.14f, .28f, .7f, .28f, 1, 0);
      addBodyCube(
          vertices, indices, authored, "right-arm", .29f, .25f, -.14f, .28f, .34f, .28f, 0, 0);
      addBodyCube(
          vertices, indices, authored, "left-leg", -.28f, .06f, -.14f, .27f, .68f, .28f, 4, 0);
      addBodyCube(
          vertices, indices, authored, "right-leg", .01f, .06f, -.14f, .27f, .68f, .28f, 4, 0);
      float[] bodyVertices = toFloats(vertices);
      short[] bodyIndices = toShorts(indices);
      Mesh body =
          new Mesh(
              true,
              bodyVertices.length / 10,
              bodyIndices.length,
              VertexAttribute.Position(),
              VertexAttribute.Normal(),
              VertexAttribute.TexCoords(0),
              VertexAttribute.BoneWeight(0));
      source.manageDisposable(body);
      body.setVertices(bodyVertices);
      body.setIndices(bodyIndices);
      MeshPart skinPart =
          new MeshPart("voxel-avatar-body", body, 0, bodyIndices.length, GL20.GL_TRIANGLES);
      skinPart.update();

      Node root = node("root", 0, 0, 0);
      Node head = node("head", 0, 1.7f, 0), torso = node("torso", 0, 1.09f, 0);
      Node leftArm = node("left-arm", -.43f, 1.27f, 0),
          rightArm = node("right-arm", .43f, 1.27f, 0);
      Node leftLeg = node("left-leg", -.145f, .74f, 0),
          rightLeg = node("right-leg", .145f, .74f, 0);
      root.addChild(head);
      root.addChild(torso);
      root.addChild(leftArm);
      root.addChild(rightArm);
      root.addChild(leftLeg);
      root.addChild(rightLeg);

      // Rigid authored clothing and tool are ordinary node parts attached to canonical bones.
      createdShirt =
          rigidCube(
              source,
              skinMaterial,
              "shirt",
              -.31f,
              .73f,
              -.17f,
              .62f,
              .7f,
              .34f,
              1,
              0,
              0,
              1.09f,
              0);
      NodePart shirtPart = rigidPart(createdShirt, skinMaterial, "shirt");
      torso.parts.add(shirtPart);
      createdTool =
          rigidCube(
              source,
              skinMaterial,
              "tool",
              .36f,
              .3f,
              .17f,
              .13f,
              .42f,
              .16f,
              0,
              1,
              .43f,
              1.27f,
              0);
      rightArm.parts.add(rigidPart(createdTool, skinMaterial, "tool"));

      NodePart weighted = new NodePart(skinPart, skinMaterial);
      weighted.invBoneBindTransforms = new ArrayMap<>(true, 7, Node.class, Matrix4.class);
      root.calculateTransforms(true);
      for (Node bone : new Node[] {root, head, torso, leftArm, rightArm, leftLeg, rightLeg})
        weighted.invBoneBindTransforms.put(bone, new Matrix4(bone.globalTransform).inv());
      root.parts.add(weighted);
      root.calculateTransforms(true);
      source.nodes.add(root);
      source.calculateTransforms();
      addAnimations(source, head, leftArm, rightArm, leftLeg, rightLeg);

      Map<String, int[]> regions = new LinkedHashMap<>();
      authored.forEach(
          (name, triangles) ->
              regions.put(name, triangles.stream().mapToInt(Integer::intValue).toArray()));
      prepared = AvatarMeshPreparator.prepare(source, new PartKey("root", 0), regions);
      AvatarDefinition builtDefinition =
          AvatarDefinition.builder()
              .region(AvatarPart.HEAD, "head")
              .region(AvatarPart.TORSO, "torso")
              .region(AvatarPart.LEFT_ARM, "left-arm")
              .region(AvatarPart.RIGHT_ARM, "right-arm")
              .region(AvatarPart.LEGS, "left-leg")
              .region(AvatarPart.LEGS, "right-leg")
              .slot("shirt", new PartKey("root/torso", 0))
              .slot("tool", new PartKey("root/right-arm", 0))
              .socket("right-hand", "root/right-arm", new Matrix4().translate(-.005f, -.97f, .25f))
              .build();

      createdObject = game.createObject("voxel avatar");
      createdObject.addComponent(new Transform3D()).scale.set(scale, scale, scale);
      ModelRenderer modelRenderer = createdObject.addComponent(new ModelRenderer(source));
      ModelPoseAnimator animator = createdObject.addComponent(new ModelPoseAnimator());
      AvatarBinding avatarBinding = builtDefinition.bind(modelRenderer, prepared);
      createdBinding = avatarBinding;
      ModelPosePlayback base =
          animator.play(
              LinearModelMixer.builder(1.0)
                  .add("avatar-idle", 0)
                  .add("avatar-walk", .55)
                  .add("avatar-run", 1)
                  .build(),
              PlayMode.LOOP,
              0);
      base.onMarker("locomotion-midpoint", .5, () -> markerCount++);
      ModelAnimationLayer use =
          animator.addLayer("tool-use", NodeMask.subtreePaths("root/right-arm"));
      ModelAnimationLayer jump =
          animator.addLayer("jump", NodeMask.subtreePaths("root/left-leg", "root/right-leg"));

      this.model = source;
      this.texture = sourceTexture;
      this.geometry = prepared;
      this.game = game;
      this.object = createdObject;
      this.renderer = modelRenderer;
      this.binding = avatarBinding;
      this.definition = builtDefinition;
      this.locomotion = base;
      this.useLayer = use;
      this.jumpLayer = jump;
      this.shirtMesh = createdShirt;
      this.toolMesh = createdTool;
    } catch (RuntimeException | Error failure) {
      if (createdBinding != null)
        try {
          createdBinding.close();
        } catch (RuntimeException | Error e) {
          failure.addSuppressed(e);
        }
      if (createdObject != null)
        try {
          createdObject.destroy();
          game.step(0);
        } catch (RuntimeException | Error e) {
          failure.addSuppressed(e);
        }
      if (prepared != null)
        try {
          prepared.close();
        } catch (RuntimeException | Error e) {
          failure.addSuppressed(e);
        }
      if (source != null)
        try {
          source.dispose();
        } catch (RuntimeException | Error e) {
          failure.addSuppressed(e);
        }
      if (sourceTexture != null)
        try {
          sourceTexture.dispose();
        } catch (RuntimeException | Error e) {
          failure.addSuppressed(e);
        }
      throw failure;
    }
  }

  void setWalking(float value) {
    float p = Math.max(0, Math.min(1, value));
    if (Float.isNaN(walkParameter) || Math.abs(p - walkParameter) > .015f) {
      locomotion.setParameter(p);
      walkParameter = p;
    }
  }

  void beginUse() {
    useLayer.setWeight(1).play("avatar-use", PlayMode.ONCE);
    useFading = false;
  }

  void updateUse() {
    ModelPosePlayback playback = useLayer.current();
    if (playback != null && playback.isCompleted() && !useFading) {
      useLayer.fadeTo(0, .16);
      useFading = true;
    }
    if (useFading && useLayer.weight() == 0 && !playbackActive()) {
      useLayer.stop();
      useFading = false;
    }
  }

  private boolean playbackActive() {
    ModelPosePlayback p = useLayer.current();
    return p != null && p.isPlaying();
  }

  void updateJump(boolean airborne) {
    ModelPosePlayback p = jumpLayer.current();
    if (airborne && !jumpWasAirborne) {
      jumpFading = false;
      jumpLayer.setWeight(1).play("avatar-jump", PlayMode.ONCE);
      p = jumpLayer.current();
    }
    if (!airborne && p != null && p.isCompleted() && !jumpFading) {
      jumpLayer.fadeTo(0, .12);
      jumpFading = true;
    }
    if (jumpFading && jumpLayer.weight() == 0) {
      jumpLayer.stop();
      jumpFading = false;
    }
    jumpWasAirborne = airborne;
  }

  double locomotionTime() {
    return locomotion.time();
  }

  double locomotionParameter() {
    return locomotion.parameter();
  }

  int markerCount() {
    return markerCount;
  }

  Matrix4 rightArmPose() {
    // The local bone pose excludes the avatar's world yaw, which may change with camera input.
    return new Matrix4(renderer.instance.getNode("right-arm").localTransform);
  }

  Matrix4 leftLegPose() {
    return new Matrix4(renderer.instance.getNode("left-leg").localTransform);
  }

  double useTime() {
    ModelPosePlayback p = useLayer.current();
    return p == null ? 0 : p.time();
  }

  double jumpTime() {
    ModelPosePlayback p = jumpLayer.current();
    return p == null ? 0 : p.time();
  }

  double jumpWeight() {
    return jumpLayer.weight();
  }

  boolean jumpPlaybackActive() {
    return jumpLayer.current() != null;
  }

  boolean rightHandSocketMatchesTool() {
    Transform3D transform = object.getComponent(Transform3D.class);
    com.badlogic.gdx.graphics.g3d.model.Node hand = renderer.instance.getNode("right-arm");
    Matrix4 base = new Matrix4(transform.worldMatrix());
    Matrix4 socket = binding.socketTransform("right-hand", base, new Matrix4());
    Matrix4 toolGrip =
        new Matrix4(base).mul(hand.globalTransform).translate(.425f - .43f, .3f - 1.27f, .25f);
    for (int i = 0; i < 16; i++)
      if (Math.abs(socket.val[i] - toolGrip.val[i]) > .0001f) return false;
    return true;
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    Throwable failure = null;
    try {
      binding.close();
    } catch (RuntimeException | Error e) {
      failure = e;
    }
    try {
      object.destroy();
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    try {
      game.step(0);
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    try {
      geometry.close();
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    try {
      model.dispose();
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    try {
      texture.dispose();
    } catch (RuntimeException | Error e) {
      failure = append(failure, e);
    }
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }

  private static Throwable append(Throwable failure, Throwable next) {
    if (failure == null) return next;
    failure.addSuppressed(next);
    return failure;
  }

  private static Node node(String id, float x, float y, float z) {
    Node n = new Node();
    n.id = id;
    n.translation.set(x, y, z);
    return n;
  }

  private static NodePart rigidPart(Mesh mesh, Material material, String id) {
    MeshPart p = new MeshPart(id, mesh, 0, mesh.getNumIndices(), GL20.GL_TRIANGLES);
    p.update();
    return new NodePart(p, material);
  }

  private static Mesh rigidCube(
      Model owner,
      Material mat,
      String name,
      float x,
      float y,
      float z,
      float w,
      float h,
      float d,
      int tileX,
      int tileY,
      float originX,
      float originY,
      float originZ) {
    List<Float> v = new ArrayList<>();
    List<Integer> tri = new ArrayList<>();
    Map<String, List<Integer>> dummy = new LinkedHashMap<>();
    addBodyCube(v, tri, dummy, name, x, y, z, w, h, d, tileX, tileY);
    float[] packed = new float[v.size()];
    for (int i = 0; i < packed.length; i++) packed[i] = v.get(i);
    // Strip the two bone fields to produce conventional rigid position/normal/UV geometry.
    float[] rigid = new float[packed.length / 10 * 8];
    for (int i = 0, j = 0; i < packed.length; i += 10) {
      rigid[j++] = packed[i] - originX;
      rigid[j++] = packed[i + 1] - originY;
      rigid[j++] = packed[i + 2] - originZ;
      for (int k = 3; k < 8; k++) rigid[j++] = packed[i + k];
    }
    short[] ix = new short[tri.size()];
    for (int i = 0; i < ix.length; i++) ix[i] = tri.get(i).shortValue();
    Mesh m =
        new Mesh(
            true,
            rigid.length / 8,
            ix.length,
            VertexAttribute.Position(),
            VertexAttribute.Normal(),
            VertexAttribute.TexCoords(0));
    owner.manageDisposable(m);
    m.setVertices(rigid);
    m.setIndices(ix);
    return m;
  }

  private static void addBodyCube(
      List<Float> v,
      List<Integer> ix,
      Map<String, List<Integer>> regions,
      String region,
      float x,
      float y,
      float z,
      float w,
      float h,
      float d,
      int tileX,
      int tileY) {
    float[][] face = {
      {x, y, z + d, x + w, y, z + d, x + w, y + h, z + d, x, y + h, z + d, 0, 0, 1},
      {x + w, y, z, x, y, z, x, y + h, z, x + w, y + h, z, -0f, 0, -1},
      {x, y, z, x, y, z + d, x, y + h, z + d, x, y + h, z, -1, 0, 0},
      {x + w, y, z + d, x + w, y, z, x + w, y + h, z, x + w, y + h, z + d, 1, 0, 0},
      {x, y + h, z + d, x + w, y + h, z + d, x + w, y + h, z, x, y + h, z, 0, 1, 0},
      {x, y, z, x + w, y, z, x + w, y, z + d, x, y, z + d, 0, -1, 0}
    };
    int startTri = ix.size() / 3;
    List<Integer> own = regions.computeIfAbsent(region, k -> new ArrayList<>());
    float bone =
        switch (region) {
          case "head" -> 1f;
          case "torso" -> 2f;
          case "left-arm" -> 3f;
          case "right-arm" -> 4f;
          case "left-leg" -> 5f;
          case "right-leg" -> 6f;
          default -> 0f;
        };
    for (float[] f : face) {
      int base = v.size() / 10;
      for (int c = 0; c < 4; c++) {
        int off = c * 3;
        v.add(f[off]);
        v.add(f[off + 1]);
        v.add(f[off + 2]);
        v.add(f[12]);
        v.add(f[13]);
        v.add(f[14]);
        float u = ((c == 1 || c == 2) ? 1f : 0f), vv = (c >= 2 ? 1f : 0f);
        v.add((tileX + u) / 4f);
        v.add((tileY + vv) / 2f);
        v.add(bone);
        v.add(1f);
      }
      int[] q = {base, base + 1, base + 2, base + 2, base + 3, base};
      for (int a : q) ix.add(a);
      own.add(startTri++);
      own.add(startTri++);
    }
  }

  private static float[] toFloats(List<Float> values) {
    float[] a = new float[values.size()];
    for (int i = 0; i < a.length; i++) a[i] = values.get(i);
    return a;
  }

  private static short[] toShorts(List<Integer> values) {
    short[] a = new short[values.size()];
    for (int i = 0; i < a.length; i++) a[i] = values.get(i).shortValue();
    return a;
  }

  private static Texture createTexture() {
    Pixmap p = new Pixmap(64, 32, Pixmap.Format.RGBA8888);
    Texture texture = null;
    try {
      int[] colors = {
        0xf1bc8aff, 0x397cd8ff, 0xe8ac75ff, 0x354c80ff,
        0x65402aff, 0xe8d6b4ff, 0x58a765ff, 0x20252fff
      };
      for (int i = 0; i < colors.length; i++) {
        int ox = (i % 4) * 16, oy = (i / 4) * 16;
        p.setColor(colors[i]);
        p.fillRectangle(ox, oy, 16, 16);
        p.setColor(0xffffff30);
        for (int y = oy; y < oy + 16; y += 4) p.drawLine(ox, y, ox + 15, y);
      }
      texture = new Texture(p, false);
      texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
      return texture;
    } catch (RuntimeException | Error failure) {
      if (texture != null)
        try {
          texture.dispose();
        } catch (RuntimeException | Error cleanupFailure) {
          failure.addSuppressed(cleanupFailure);
        }
      throw failure;
    } finally {
      p.dispose();
    }
  }

  private static void addAnimations(
      Model model, Node head, Node leftArm, Node rightArm, Node leftLeg, Node rightLeg) {
    addPair(model, "avatar-idle", head, leftArm, rightArm, leftLeg, rightLeg, 2f);
    addPair(model, "avatar-walk", head, leftArm, rightArm, leftLeg, rightLeg, 22f);
    addPair(model, "avatar-run", head, leftArm, rightArm, leftLeg, rightLeg, 38f);
    addSingle(model, "avatar-use", rightArm, .42f, -68f);
    addSingle(model, "avatar-jump", leftLeg, .38f, 22f);
    addSingle(model, "avatar-jump", rightLeg, .38f, -22f);
  }

  private static void addPair(
      Model model, String id, Node head, Node la, Node ra, Node ll, Node rl, float swingDegrees) {
    Animation a = new Animation();
    a.id = id;
    a.duration = 1;
    track(a, head, .018f);
    trackSwing(a, la, swingDegrees);
    trackSwing(a, ra, -swingDegrees);
    trackSwing(a, ll, -swingDegrees);
    trackSwing(a, rl, swingDegrees);
    model.animations.add(a);
  }

  private static void addSingle(Model model, String id, Node node, float duration, float amount) {
    Animation a = null;
    for (Animation it : model.animations) if (it.id.equals(id)) a = it;
    if (a == null) {
      a = new Animation();
      a.id = id;
      a.duration = duration;
    }
    trackRotation(a, node, amount);
    a.duration = Math.max(a.duration, duration);
    if (!model.animations.contains(a, true)) model.animations.add(a);
  }

  private static void track(Animation a, Node n, float amount) {
    NodeAnimation na = new NodeAnimation();
    na.node = n;
    na.translation = new Array<>();
    na.translation.add(
        new NodeKeyframe<>(0, new Vector3(n.translation.x, n.translation.y, n.translation.z)));
    na.translation.add(
        new NodeKeyframe<>(
            a.duration * .5f,
            new Vector3(n.translation.x, n.translation.y + amount, n.translation.z)));
    na.translation.add(
        new NodeKeyframe<>(
            a.duration, new Vector3(n.translation.x, n.translation.y, n.translation.z)));
    a.nodeAnimations.add(na);
  }

  private static void trackSwing(Animation animation, Node node, float degrees) {
    NodeAnimation nodeAnimation = new NodeAnimation();
    nodeAnimation.node = node;
    nodeAnimation.rotation = new Array<>();
    float[] phases = {0, degrees, 0, -degrees, 0};
    for (int i = 0; i < phases.length; i++) {
      Quaternion rotation = new Quaternion().setFromAxis(Vector3.X, phases[i]);
      nodeAnimation.rotation.add(new NodeKeyframe<>(animation.duration * i / 4f, rotation));
    }
    animation.nodeAnimations.add(nodeAnimation);
  }

  private static void trackRotation(Animation animation, Node node, float degrees) {
    NodeAnimation nodeAnimation = new NodeAnimation();
    nodeAnimation.node = node;
    nodeAnimation.rotation = new Array<>();
    nodeAnimation.rotation.add(new NodeKeyframe<>(0, new Quaternion()));
    nodeAnimation.rotation.add(
        new NodeKeyframe<>(
            animation.duration * .45f, new Quaternion().setFromAxis(Vector3.X, degrees)));
    nodeAnimation.rotation.add(new NodeKeyframe<>(animation.duration, new Quaternion()));
    animation.nodeAnimations.add(nodeAnimation);
  }
}
