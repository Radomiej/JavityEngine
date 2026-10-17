package org.javity.ui.flex;

import static org.junit.Assert.*;

import com.badlogic.gdx.scenes.scene2d.Actor;
import org.javity.ui.yoga.YogaBackends;
import org.javity.ui.yoga.YogaNode;
import org.junit.Test;

public class FlexTest {
  @Test
  public void rowUsesPaddingGapAndGrow() {
    try (Flex root = new Flex("flex-row p-4 gap-2 items-stretch")) {
      root.setSize(400, 100);
      Actor a = root.add(new Actor(), "w-20");
      Actor b = root.add(new Actor(), "flex-1");
      root.validate();
      assertEquals(16, a.getX(), .01);
      assertEquals(80, a.getWidth(), .01);
      assertEquals(104, b.getX(), .01);
      assertEquals(280, b.getWidth(), .01);
      assertEquals(68, b.getHeight(), .01);
    }
  }

  @Test
  public void nestedColumnReflowsWhenResized() {
    try (Flex root = new Flex("flex-col p-2 gap-2")) {
      Flex child = root.add(new Flex("flex-row gap-2"), "h-10 w-full");
      Actor a = child.add(new Actor(), "flex-1");
      Actor b = child.add(new Actor(), "flex-1");
      root.setSize(200, 120);
      root.validate();
      assertEquals(88, a.getWidth(), .01);
      assertEquals(72, child.getY(), .01);
      root.setSize(400, 120);
      root.validate();
      assertEquals(188, b.getWidth(), .01);
    }
  }

  @Test
  public void removingAndMovingChildKeepsNativeTreeConsistent() {
    try (Flex first = new Flex("flex-row");
        Flex second = new Flex("flex-row")) {
      Actor actor = first.add(new Actor(), "w-10 h-10");
      second.add(actor, "w-20 h-10");
      first.setSize(100, 100);
      first.validate();
      second.setSize(200, 100);
      second.validate();
      assertEquals(0, first.getChildren().size);
      assertEquals(80, actor.getWidth(), .01);
      assertTrue(actor.remove());
      second.validate();
      assertEquals(0, second.getChildren().size);
    }
  }

  @Test
  public void wrapCreatesSecondRow() {
    try (Flex root = new Flex("flex-row flex-wrap gap-2")) {
      root.setSize(100, 100);
      Actor first = root.add(new Actor(), "w-15 h-5");
      Actor second = root.add(new Actor(), "w-15 h-5");
      root.validate();
      assertTrue(second.getY() < first.getY());
    }
  }

  @Test
  public void unknownUtilityFailsClearly() {
    assertThrows(IllegalArgumentException.class, () -> new Flex("gap-banana"));
  }

  @Test
  public void disposedLayoutCannotBeReused() {
    Flex root = new Flex("flex-col");
    root.close();
    root.close();
    assertThrows(IllegalStateException.class, () -> root.add(new Actor(), ""));
  }

  @Test
  public void emptyOverlayDoesNotCaptureWorldPointer() {
    try (Flex root = new Flex("flex-col")) {
      root.setSize(800, 600);
      assertNull(root.hit(600, 300, true));
      Actor button = root.add(new Actor(), "w-20 h-10");
      root.validate();
      assertSame(button, root.hit(button.getX() + 10, button.getY() + 10, true));
    }
  }

  @Test
  public void yogaMeasuredLeafRecomputesFromEachConstrainedWidthAndCopiedTree() {
    try (Flex root = new Flex("flex-col")) {
      WidthSensitiveActor text = root.add(new WidthSensitiveActor(), "w-full h-auto");
      root.setSize(48, 120);
      root.validate();
      assertEquals(48, text.getWidth(), .01f);
      assertEquals(48, text.getHeight(), .01f);
      root.getPrefHeight(); // Detached intrinsic-copy callbacks must be valid and temporary.
      root.setSize(120, 120);
      root.validate();
      assertEquals(120, text.getWidth(), .01f);
      assertEquals(24, text.getHeight(), .01f);
      text.remove();
      root.validate(); // Removal unregisters and frees the callback before freeing its node.
    }
  }

  @Test
  public void yogaMeasuredLeafInvalidatesCachedHeightAfterSameWidthContentChange() {
    try (Flex root = new Flex("flex-col")) {
      MutableMeasuredActor text = root.add(new MutableMeasuredActor(), "w-full h-auto");
      root.setSize(120, 120);
      root.validate();
      assertEquals(24, text.getHeight(), .01f);
      assertEquals(1, text.measureCalls);

      // invalidateHierarchy propagates through the normal Scene2D path; Yoga constraints do not
      // change, so only dirtying the measured leaf can refresh its cached height.
      text.height = 48;
      root.invalidateHierarchy();
      root.validate();
      assertEquals(48, text.getHeight(), .01f);
      assertEquals(2, text.measureCalls);

      text.height = 24;
      root.invalidateHierarchy();
      root.validate();
      assertEquals(24, text.getHeight(), .01f);
      assertEquals(3, text.measureCalls);
    }
  }

  @Test
  public void yogaMeasureFailuresAreRethrownOnlyAfterNativeLayoutReturns() {
    try (Flex root = new Flex("flex-col")) {
      root.add(new FailingActor(), "w-full h-auto");
      root.setSize(80, 80);
      IllegalArgumentException failure =
          assertThrows(IllegalArgumentException.class, root::validate);
      assertEquals("measure sentinel", failure.getMessage());
    }
  }

  @Test
  public void measureCallbackHonorsYogaWidthAndHeightModes() {
    WidthSensitiveActor actor = new WidthSensitiveActor();
    var callback = Flex.measureFunction(actor, failure -> fail("Unexpected callback failure"));
    var atMost = callback.measure(30, YogaNode.MEASURE_AT_MOST, 25, YogaNode.MEASURE_AT_MOST);
    assertEquals(30, atMost.width(), .001f);
    assertEquals(25, atMost.height(), .001f);
    var exact = callback.measure(30, YogaNode.MEASURE_EXACTLY, 25, YogaNode.MEASURE_EXACTLY);
    assertEquals(30, exact.width(), .001f);
    assertEquals(25, exact.height(), .001f);
    var wider = callback.measure(100, YogaNode.MEASURE_AT_MOST, 60, YogaNode.MEASURE_AT_MOST);
    assertEquals(80, wider.width(), .001f);
    assertEquals(24, wider.height(), .001f);
  }

  @Test
  public void backendRethrowsMeasureFailureAfterNativeLayoutReturns() {
    var backend = YogaBackends.createDefault();
    YogaNode root = backend.createNode();
    YogaNode leaf = backend.createNode();
    try {
      leaf.setMeasureFunction(
          (width, widthMode, height, heightMode) -> {
            throw new IllegalArgumentException("backend measure sentinel");
          });
      root.insertChild(leaf, 0);
      try {
        root.calculateLayout(80, 80);
        fail("Expected deferred measure failure");
      } catch (IllegalArgumentException expected) {
        assertEquals("backend measure sentinel", expected.getMessage());
      }
    } finally {
      root.freeSubtree();
    }
  }

  @Test
  public void backendChecksOwnershipBeforeNativeReparentAndFree() {
    var backend = YogaBackends.createDefault();
    YogaNode first = backend.createNode();
    YogaNode second = backend.createNode();
    YogaNode child = backend.createNode();
    try {
      first.insertChild(child, 0);
      assertThrows(IllegalStateException.class, () -> second.insertChild(child, 0));
      assertThrows(IllegalStateException.class, child::freeNode);
      first.removeChild(child);
      second.insertChild(child, 0);
      assertThrows(IllegalArgumentException.class, () -> child.insertChild(second, 0));
      second.removeChild(child);
    } finally {
      first.freeSubtree();
      second.freeSubtree();
      child.freeNode();
    }
  }

  @Test
  public void yogaOwnsTextPaddingAndBorderInsetsOnce() {
    try (Flex root = new Flex("flex-col items-start")) {
      Actor text = root.add(new WidthSensitiveActor(), "w-[100px] h-auto p-2 border");
      root.setSize(200, 100);
      root.validate();
      assertEquals(100, text.getWidth(), .01f);
      assertEquals(42, text.getHeight(), .01f); // 24 content + 16 padding + 2 border.
    }
  }

  private static final class WidthSensitiveActor extends Actor implements YogaMeasuredActor {
    @Override
    public Size measureForYoga(float width, int widthMode) {
      return new Size(80, width <= 60 ? 48 : 24);
    }
  }

  private static final class MutableMeasuredActor extends Actor implements YogaMeasuredActor {
    float height = 24;
    int measureCalls;

    @Override
    public Size measureForYoga(float width, int widthMode) {
      measureCalls++;
      return new Size(80, height);
    }
  }

  private static final class FailingActor extends Actor implements YogaMeasuredActor {
    @Override
    public Size measureForYoga(float width, int widthMode) {
      throw new IllegalArgumentException("measure sentinel");
    }
  }

  @Test
  public void nestedFlexKeepsOwnVisualAndParentStyleOnlyAffectsYoga() throws Exception {
    try (Flex parent = new Flex("");
        Flex child = new Flex("bg-[#ff0000] p-1")) {
      parent.add(child, "bg-[#0000ff] w-10");
      assertEquals(UtilityStyles.color("#ff0000"), visualOf(child).background());

      child.setStyleClasses("bg-[#00ff00] p-2");
      assertEquals(UtilityStyles.color("#00ff00"), visualOf(child).background());
      parent.setSize(100, 100);
      parent.validate();
      assertEquals(40, child.getWidth(), .01f);

      child.remove();
      assertEquals(UtilityStyles.color("#00ff00"), visualOf(child).background());
    }
  }

  private static UtilityStyles.Visual visualOf(Flex flex) throws Exception {
    var visual = Flex.class.getDeclaredField("visual");
    visual.setAccessible(true);
    return (UtilityStyles.Visual) visual.get(flex);
  }
}
