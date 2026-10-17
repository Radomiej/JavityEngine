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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ArrayMap;

/** Procedural owned fixture geometry, rig, and texture for avatar partition native evidence. */
final class GeneratedAvatarModel implements AutoCloseable {
  final Model model;
  final Texture texture;
  final Mesh mesh;

  private GeneratedAvatarModel(Model model, Texture texture, Mesh mesh) {
    this.model = model;
    this.texture = texture;
    this.mesh = mesh;
  }

  static GeneratedAvatarModel create() {
    Pixmap pixels = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
    for (int y = 0; y < 4; y++)
      for (int x = 0; x < 4; x++) {
        pixels.setColor(((x + y) & 1) == 0 ? 0xe94739ff : 0xf4bd45ff);
        pixels.drawPixel(x, y);
      }
    Texture texture = new Texture(pixels);
    pixels.dispose();

    // Position, normal, UV, and two ordered bone influences. Region ordinals 0-1 are body and
    // 2-3 are arm; both share this single canonical skinned NodePart and palette.
    VertexAttribute[] attributes = {
      VertexAttribute.Position(),
      VertexAttribute.Normal(),
      VertexAttribute.TexCoords(0),
      VertexAttribute.BoneWeight(0),
      VertexAttribute.BoneWeight(1)
    };
    Mesh mesh = new Mesh(true, 8, 12, attributes);
    mesh.setVertices(
        new float[] {
          -.55f, -.85f, 0, 0, 0, 1, 0, 0, 0, .7f, 1, .3f,
          .55f, -.85f, 0, 0, 0, 1, 1, 0, 0, .7f, 1, .3f,
          .55f, -.05f, 0, 0, 0, 1, 1, 1, 0, .7f, 1, .3f,
          -.55f, -.05f, 0, 0, 0, 1, 0, 1, 0, .7f, 1, .3f,
          -.55f, .05f, 0, 0, 0, 1, 0, 0, 0, .7f, 1, .3f,
          .55f, .05f, 0, 0, 0, 1, 1, 0, 0, .7f, 1, .3f,
          .55f, .85f, 0, 0, 0, 1, 1, 1, 0, .7f, 1, .3f,
          -.55f, .85f, 0, 0, 0, 1, 0, 1, 0, .7f, 1, .3f
        });
    mesh.setIndices(new short[] {0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4});

    Model model = new Model();
    model.manageDisposable(mesh);
    Material material = new Material(TextureAttribute.createDiffuse(texture));
    model.materials.add(material);
    MeshPart meshPart = new MeshPart("authored-avatar-skin", mesh, 0, 12, GL20.GL_TRIANGLES);
    meshPart.update();
    Node root = new Node();
    root.id = "root";
    root.translation.set(.18f, .04f, 0);
    Node arm = new Node();
    arm.id = "left-arm";
    arm.translation.set(0, .1f, 0);
    root.addChild(arm);
    NodePart nodePart = new NodePart(meshPart, material);
    nodePart.invBoneBindTransforms = new ArrayMap<>(true, 2, Node.class, Matrix4.class);
    root.calculateTransforms(true);
    nodePart.invBoneBindTransforms.put(root, new Matrix4(root.globalTransform).inv());
    nodePart.invBoneBindTransforms.put(arm, new Matrix4(arm.globalTransform).inv());
    root.parts.add(nodePart);
    root.calculateTransforms(true);
    model.nodes.add(root);
    model.calculateTransforms();
    Animation clip = new Animation();
    clip.id = "arm-sweep";
    clip.duration = .6f;
    NodeAnimation armAnimation = new NodeAnimation();
    armAnimation.node = arm;
    armAnimation.translation = new Array<>();
    armAnimation.translation.add(new NodeKeyframe<>(0, new Vector3(0, .1f, 0)));
    armAnimation.translation.add(new NodeKeyframe<>(.6f, new Vector3(.22f, .1f, 0)));
    clip.nodeAnimations.add(armAnimation);
    model.animations.add(clip);
    return new GeneratedAvatarModel(model, texture, mesh);
  }

  @Override
  public void close() {
    model.dispose();
    texture.dispose();
  }
}
