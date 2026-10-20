package org.javity.core;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.Vector3;
import org.junit.Test;

public class Transform3DOwnershipTest {
  @Test
  public void attachedTransformsRejectForeignParentWithoutChangingHierarchy() {
    try (Game first = new Game();
        Game second = new Game()) {
      Transform3D child = add(first), original = add(first), foreign = add(second);
      child.setParent(original);
      assertThrows(IllegalArgumentException.class, () -> child.setParent(foreign));
      assertSame(original, child.getParent());
    }
  }

  @Test
  public void attachingChildRejectsForeignParentAndLeavesComponentReusable() {
    try (Game first = new Game();
        Game second = new Game()) {
      Transform3D parent = add(first), child = new Transform3D();
      child.setParent(parent);
      GameObject rejectedOwner = second.createObject("rejected");
      assertThrows(IllegalArgumentException.class, () -> rejectedOwner.addComponent(child));
      assertNull(child.getGameObject());
      assertFalse(rejectedOwner.containsComponent(child));
      first.createObject("child").addComponent(child);
      assertSame(parent, child.getParent());
    }
  }

  @Test
  public void attachingParentRejectsForeignChildAndLeavesComponentReusable() {
    try (Game first = new Game();
        Game second = new Game()) {
      Transform3D parent = new Transform3D(), child = add(first);
      child.setParent(parent);
      assertThrows(
          IllegalArgumentException.class,
          () -> second.createObject("rejected").addComponent(parent));
      assertNull(parent.getGameObject());
      first.createObject("parent").addComponent(parent);
      assertSame(parent, child.getParent());
    }
  }

  @Test
  public void prebuiltHierarchyAttachesInEitherOrderWithinOneScene() {
    for (boolean parentFirst : new boolean[] {false, true}) {
      try (Game game = new Game()) {
        Transform3D parent = new Transform3D(), child = new Transform3D();
        parent.position.x = 10;
        child.position.x = 2;
        child.setParent(parent);
        game.createObject("first").addComponent(parentFirst ? parent : child);
        game.createObject("second").addComponent(parentFirst ? child : parent);
        assertSame(parent, child.getParent());
        assertEquals(12, x(child), 0);
      }
    }
  }

  @Test
  public void unownedBridgeCannotJoinDifferentScenesByReparenting() {
    try (Game first = new Game();
        Game second = new Game()) {
      Transform3D firstRoot = add(first), secondRoot = add(second);
      Transform3D middle = new Transform3D(), child = new Transform3D();
      middle.setParent(firstRoot);
      child.setParent(middle);
      assertThrows(IllegalArgumentException.class, () -> secondRoot.setParent(child));
      assertNull(secondRoot.getParent());
    }
  }

  @Test
  public void attachingThroughUnownedBridgeChecksAncestorsAndDescendants() {
    try (Game first = new Game();
        Game second = new Game()) {
      Transform3D root = add(first), middle = new Transform3D(), leaf = new Transform3D();
      middle.setParent(root);
      leaf.setParent(middle);
      assertThrows(
          IllegalArgumentException.class, () -> second.createObject("leaf").addComponent(leaf));
      assertNull(leaf.getGameObject());
      first.createObject("leaf").addComponent(leaf);
      assertThrows(
          IllegalArgumentException.class, () -> second.createObject("middle").addComponent(middle));
      assertNull(middle.getGameObject());
    }
  }

  @Test
  public void detachingPreservesLocalPoseAndAllowsAnotherSceneForUnownedSubtree() {
    try (Game first = new Game();
        Game second = new Game()) {
      Transform3D parent = add(first), child = new Transform3D();
      parent.position.x = 10;
      child.position.x = 2;
      child.setParent(parent);
      child.setParent(null);
      second.createObject("child").addComponent(child);
      assertNull(child.getParent());
      assertEquals(2, x(child), 0);
    }
  }

  @Test
  public void removedParentStopsAffectingChildBeforeDeferredCleanup() {
    try (Game game = new Game()) {
      Transform3D parent = add(game), child = add(game);
      parent.position.x = 10;
      child.position.x = 2;
      child.setParent(parent);
      parent.getGameObject().removeComponent(parent);
      assertEquals(2, x(child), 0);
      assertNull(child.getParent());
      assertThrows(IllegalArgumentException.class, () -> child.setParent(parent));
    }
  }

  @Test
  public void destroyedOwnerStopsAffectingChildBeforeDeferredCleanup() {
    try (Game game = new Game()) {
      Transform3D parent = add(game), child = add(game);
      parent.position.x = 10;
      child.position.x = 2;
      child.setParent(parent);
      parent.getGameObject().destroy();
      assertEquals(2, x(child), 0);
      assertNull(child.getParent());
      assertThrows(IllegalArgumentException.class, () -> child.setParent(parent));
    }
  }

  @Test
  public void parentReleaseDetachesChildrenEvenBeforeInitialization() {
    Game game = new Game();
    Transform3D parent = add(game), child = new Transform3D();
    child.setParent(parent);
    game.close();
    assertNull(child.getParent());
    try (Game next = new Game()) {
      next.createObject("child").addComponent(child);
    }
  }

  @Test
  public void releasedChildDoesNotRemainInFormerParentsHierarchy() {
    try (Game first = new Game();
        Game second = new Game()) {
      Transform3D parent = new Transform3D(), child = add(first);
      child.setParent(parent);
      first.close();
      assertNull(child.getParent());
      second.createObject("parent").addComponent(parent);
    }
  }

  @Test
  public void removedChildDoesNotConstrainUnownedAncestorsBeforeCleanup() {
    try (Game first = new Game();
        Game second = new Game()) {
      Transform3D root = new Transform3D(), middle = new Transform3D(), child = add(first);
      middle.setParent(root);
      child.setParent(middle);
      child.getGameObject().removeComponent(child);
      second.createObject("root").addComponent(root);
      assertNull(child.getParent());
      assertSame(root, middle.getParent());
    }
  }

  @Test
  public void destroyedAncestorIsExcludedThroughUnownedIntermediate() {
    try (Game game = new Game()) {
      Transform3D root = add(game), middle = new Transform3D(), child = add(game);
      root.position.x = 10;
      middle.position.x = 3;
      child.position.x = 2;
      middle.setParent(root);
      child.setParent(middle);
      root.getGameObject().destroy();
      assertEquals(5, x(child), 0);
      assertSame(middle, child.getParent());
      assertNull(middle.getParent());
    }
  }

  @Test
  public void attachingAfterParentRemovalDetachesStaleParent() {
    try (Game first = new Game();
        Game second = new Game()) {
      Transform3D parent = add(first), child = new Transform3D();
      parent.position.x = 10;
      child.position.x = 2;
      child.setParent(parent);
      parent.getGameObject().removeComponent(parent);
      second.createObject("child").addComponent(child);
      assertNull(child.getParent());
      assertEquals(2, x(child), 0);
    }
  }

  @Test
  public void cycleRejectionPreservesExistingHierarchy() {
    Transform3D root = new Transform3D(), middle = new Transform3D(), leaf = new Transform3D();
    middle.setParent(root);
    leaf.setParent(middle);
    assertThrows(IllegalArgumentException.class, () -> root.setParent(leaf));
    assertThrows(IllegalArgumentException.class, () -> leaf.setParent(leaf));
    assertNull(root.getParent());
    assertSame(root, middle.getParent());
    assertSame(middle, leaf.getParent());
  }

  @Test
  public void alreadyAttachedComponentCannotBeAddedToAnotherObject() {
    try (Game game = new Game()) {
      Transform3D transform = add(game);
      GameObject original = transform.getGameObject();
      GameObject other = game.createObject("other");
      assertThrows(IllegalArgumentException.class, () -> other.addComponent(transform));
      assertSame(original, transform.getGameObject());
      assertFalse(other.containsComponent(transform));
    }
  }

  private static Transform3D add(Game game) {
    return game.createObject("transform").addComponent(new Transform3D());
  }

  private static float x(Transform3D transform) {
    return transform.worldMatrix().getTranslation(new Vector3()).x;
  }
}
