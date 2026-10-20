package org.javity.gltf;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.model.*;
import com.badlogic.gdx.math.Matrix4;
import java.util.List;
import org.javity.avatar.*;
import org.javity.core.Game;
import org.javity.render.ModelRenderer;
import org.junit.Test;

public class ImportedAvatarProfileTest {
  @Test
  public void catalogUsesExactPathsPartOrdinalsAndClipTargetsWithoutGuessing() {
    ModelInstance instance = fixture();
    var catalog = ImportedAvatarProfile.inspect(instance);
    assertEquals(List.of("Rig", "Rig/Hand", "Rig/Hand/Tool"), catalog.nodePaths());
    assertEquals(new PartKey("Rig/Hand", 1), catalog.parts().get(1).key());
    assertEquals("metal", catalog.parts().get(1).materialId());
    assertEquals(List.of("Rig/Hand"), catalog.clips().get(0).nodePaths());
    assertEquals("Wave exact", catalog.clips().get(0).id());
    assertThrows(UnsupportedOperationException.class, () -> catalog.nodePaths().clear());
    instance.getNode("Hand").id = "changed";
    assertEquals("Rig/Hand", catalog.parts().get(0).key().nodePath());
  }

  @Test
  public void explicitProfileFailsBeforeBindingAndKeepsOriginalClipIds() {
    var catalog = ImportedAvatarProfile.inspect(fixture());
    assertThrows(
        IllegalArgumentException.class,
        () -> catalog.profile().part(AvatarPart.RIGHT_ARM, new PartKey("Hand", 0)));
    assertThrows(
        IllegalArgumentException.class,
        () -> catalog.profile().socket("grip", "Rig/Missing", new Matrix4()));
    assertThrows(
        IllegalArgumentException.class, () -> catalog.profile().clip("wave", "wave exact"));
    var profile =
        catalog
            .profile()
            .part(AvatarPart.RIGHT_ARM, new PartKey("Rig/Hand", 0), new PartKey("Rig/Hand", 1))
            .slot("tool", new PartKey("Rig/Hand/Tool", 0))
            .socket("grip", "Rig/Hand", new Matrix4())
            .clip("greeting", "Wave exact")
            .build();
    assertEquals("Wave exact", profile.clip("greeting"));
    assertThrows(IllegalArgumentException.class, () -> profile.clip("unknown"));
    try (Game game = new Game()) {
      var renderer = game.createObject("avatar").addComponent(new ModelRenderer(fixture()));
      try (var binding = profile.definition().bind(renderer)) {
        assertNotNull(binding.view());
        assertNotNull(binding.socketTransform("grip", new Matrix4(), new Matrix4()));
      }
    }
  }

  @Test
  public void ambiguousCanonicalPathsAndDetachedAnimationTargetsFailWithDiagnostics() {
    ModelInstance instance = fixture();
    Node duplicate = new Node();
    duplicate.id = "Hand";
    instance.getNode("Rig").addChild(duplicate);
    assertThrows(IllegalArgumentException.class, () -> ImportedAvatarProfile.inspect(instance));
    ModelInstance detached = fixture();
    detached.animations.first().nodeAnimations.first().node = new Node();
    final ModelInstance invalid = detached;
    assertThrows(IllegalArgumentException.class, () -> ImportedAvatarProfile.inspect(invalid));
  }

  @Test
  public void originalHumanoidFixtureIsBoundedToItsAuthoredEightJointRig() {
    var file =
        new com.badlogic.gdx.files.FileHandle(
            "src/main/resources/org/javity/gltf/fixtures/avatar.glb");
    GltfDocument.validate(file, 8);
    assertThrows(IllegalArgumentException.class, () -> GltfDocument.validate(file, 7));
  }

  @Test
  public void paletteDiagnosticsUseCanonicalPathsAndRejectForeignBones() {
    ModelInstance instance = fixture();
    Node hand = instance.getNode("Hand");
    var part = hand.parts.first();
    part.invBoneBindTransforms = new com.badlogic.gdx.utils.ArrayMap<>();
    part.invBoneBindTransforms.put(hand, new Matrix4());
    assertEquals(
        List.of("Rig/Hand"), ImportedAvatarProfile.inspect(instance).parts().get(0).bonePaths());
    part.invBoneBindTransforms.put(new Node(), new Matrix4());
    assertThrows(IllegalArgumentException.class, () -> ImportedAvatarProfile.inspect(instance));
  }

  private static ModelInstance fixture() {
    ModelInstance instance = new ModelInstance(new Model());
    Node rig = new Node();
    rig.id = "Rig";
    Node hand = new Node();
    hand.id = "Hand";
    Node tool = new Node();
    tool.id = "Tool";
    rig.addChild(hand);
    hand.addChild(tool);
    instance.nodes.add(rig);
    hand.parts.add(new NodePart(new MeshPart(), new Material("skin")));
    hand.parts.add(new NodePart(new MeshPart(), new Material("metal")));
    tool.parts.add(new NodePart(new MeshPart(), new Material("grip")));
    Animation clip = new Animation();
    clip.id = "Wave exact";
    clip.duration = 2;
    NodeAnimation channel = new NodeAnimation();
    channel.node = hand;
    clip.nodeAnimations.add(channel);
    instance.animations.add(clip);
    instance.calculateTransforms();
    return instance;
  }
}
