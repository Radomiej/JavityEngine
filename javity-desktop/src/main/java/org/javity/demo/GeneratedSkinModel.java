package org.javity.demo;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
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
import com.badlogic.gdx.utils.ArrayMap;

/** Small procedural two-bone strip, generated in memory for the animation gallery. */
final class GeneratedSkinModel {
  private GeneratedSkinModel() {}

  static Model create() {
    Model model = new Model();
    Mesh mesh =
        new Mesh(
            true,
            8,
            12,
            VertexAttribute.Position(),
            VertexAttribute.Normal(),
            VertexAttribute.BoneWeight(0));
    // Two disjoint quads follow separate bones. This keeps the root-owned body pixels independent
    // from the tip-owned arm pixels for a strict masked-override comparison.
    mesh.setVertices(
        new float[] {
          -.55f, -.9f, 0, 0, 0, 1, 0, 1,
          .55f, -.9f, 0, 0, 0, 1, 0, 1,
          .55f, -.05f, 0, 0, 0, 1, 0, 1,
          -.55f, -.05f, 0, 0, 0, 1, 0, 1,
          -.55f, .05f, 0, 0, 0, 1, 1, 1,
          .55f, .05f, 0, 0, 0, 1, 1, 1,
          .55f, .9f, 0, 0, 0, 1, 1, 1,
          -.55f, .9f, 0, 0, 0, 1, 1, 1
        });
    mesh.setIndices(new short[] {0, 1, 2, 2, 3, 0, 4, 5, 6, 6, 7, 4});
    model.manageDisposable(mesh);

    Material material =
        new Material(
            ColorAttribute.createDiffuse(com.badlogic.gdx.graphics.Color.valueOf("#55d6be")));
    model.materials.add(material);
    MeshPart meshPart = new MeshPart("skinned-body-parts", mesh, 0, 12, GL20.GL_TRIANGLES);
    meshPart.update();
    Node root = new Node();
    root.id = "root";
    Node tip = new Node();
    tip.id = "tip";
    root.addChild(tip);
    NodePart part = new NodePart(meshPart, material);
    part.invBoneBindTransforms = new ArrayMap<>(true, 2, Node.class, Matrix4.class);
    part.invBoneBindTransforms.put(root, new Matrix4());
    part.invBoneBindTransforms.put(tip, new Matrix4());
    part.bones = new Matrix4[] {new Matrix4(), new Matrix4()};
    root.parts.add(part);
    root.calculateTransforms(true);
    model.nodes.add(root);

    model.animations.add(animation("bend-left", tip, -.52f));
    model.animations.add(animation("bend-right", tip, .52f));
    model.animations.add(actionAnimation(tip));
    model.calculateTransforms();
    return model;
  }

  private static Animation animation(String name, Node tip, float direction) {
    NodeAnimation nodeAnimation = new NodeAnimation();
    nodeAnimation.node = tip;
    nodeAnimation.translation = new Array<>();
    nodeAnimation.translation.add(new NodeKeyframe<>(0, new Vector3(0, 0, 0)));
    nodeAnimation.translation.add(new NodeKeyframe<>(.6f, new Vector3(direction, .32f, 0)));
    nodeAnimation.translation.add(new NodeKeyframe<>(1.2f, new Vector3(0, 0, 0)));
    Animation animation = new Animation();
    animation.id = name;
    animation.duration = 1.2f;
    animation.nodeAnimations = new Array<>();
    animation.nodeAnimations.add(nodeAnimation);
    return animation;
  }

  private static Animation actionAnimation(Node tip) {
    NodeAnimation nodeAnimation = new NodeAnimation();
    nodeAnimation.node = tip;
    nodeAnimation.translation = new Array<>();
    nodeAnimation.translation.add(new NodeKeyframe<>(0, new Vector3(0, 0, 0)));
    nodeAnimation.translation.add(new NodeKeyframe<>(.6f, new Vector3(0, .65f, 0)));
    nodeAnimation.translation.add(new NodeKeyframe<>(1.2f, new Vector3(0, 0, 0)));
    Animation animation = new Animation();
    animation.id = "upper-reach";
    animation.duration = 1.2f;
    animation.nodeAnimations = new Array<>();
    animation.nodeAnimations.add(nodeAnimation);
    return animation;
  }
}
