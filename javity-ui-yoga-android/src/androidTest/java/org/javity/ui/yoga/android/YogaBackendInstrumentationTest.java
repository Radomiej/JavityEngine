package org.javity.ui.yoga.android;

import android.test.AndroidTestCase;
import java.util.List;
import java.util.Map;
import org.javity.ui.declarative.UiDocument;
import org.javity.ui.declarative.UiNode;
import org.javity.ui.yoga.YogaBackend;
import org.javity.ui.yoga.YogaNode;

/** Runs real Yoga JNI layout and explicit node/config release on ART. */
public final class YogaBackendInstrumentationTest extends AndroidTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    AndroidYogaBackend.initialize(getContext());
  }

  public void testNativeLayoutAndExplicitSubtreeRelease() {
    YogaBackend backend = new AndroidYogaBackend();
    YogaNode root = backend.createNode();
    YogaNode measured = backend.createNode();
    root.setFlexDirection(YogaNode.FlexDirection.ROW);
    root.setAlignItems(YogaNode.Align.START);
    root.setWidth(100);
    root.setHeight(40);
    measured.setMeasureFunction(
        (width, widthMode, height, heightMode) -> new YogaNode.Size(24, 12));
    root.insertChild(measured, 0);
    root.calculateLayout(100, 40);

    assertEquals(24, measured.layoutWidth(), .001f);
    assertEquals(12, measured.layoutHeight(), .001f);
    root.freeSubtree();
  }

  public void testOnePortableDocumentBuildsAndClosesOnAndroidYoga() {
    YogaBackend backend = new AndroidYogaBackend();
    try (UiDocument document = new UiDocument(backend, null)) {
      document.render(
          UiNode.element(
              "View",
              "root",
              "flex-row p-2 gap-2",
              Map.of(),
              List.of(UiNode.element("View", "child", "w-10 h-5", Map.of(), List.of()))));
      document.root().setSize(120, 60);
      document.root().validate();
      assertEquals(40, document.actor("child").getWidth(), .001f);
      assertEquals(8, document.actor("child").getX(), .001f);
    }
  }

  public void testMeasureFailureReturnsAcrossJNIOnlyAfterLayout() {
    YogaBackend backend = new AndroidYogaBackend();
    YogaNode root = backend.createNode();
    YogaNode leaf = backend.createNode();
    try {
      leaf.setMeasureFunction(
          (width, widthMode, height, heightMode) -> {
            throw new IllegalArgumentException("Android measure sentinel");
          });
      root.insertChild(leaf, 0);
      try {
        root.calculateLayout(80, 80);
      } catch (IllegalArgumentException expected) {
        assertEquals("Android measure sentinel", expected.getMessage());
        return;
      }
      fail("Expected measure failure after Yoga returned from JNI");
    } finally {
      root.freeSubtree();
    }
  }

  public void testNativeGuardsAndValidMeasuredLeafDirtying() {
    YogaBackend backend = new AndroidYogaBackend();
    YogaNode node = backend.createNode();
    try {
      expectIllegalState(node::markDirty);
      for (YogaNode.Edge edge :
          new YogaNode.Edge[] {
            YogaNode.Edge.ALL, YogaNode.Edge.HORIZONTAL, YogaNode.Edge.VERTICAL
          }) {
        expectIllegalArgument(() -> node.layoutBorder(edge));
        expectIllegalArgument(() -> node.layoutPadding(edge));
        expectIllegalArgument(() -> node.layoutMargin(edge));
      }
      node.setMeasureFunction((width, widthMode, height, heightMode) -> new YogaNode.Size(4, 3));
      node.markDirty();
      node.calculateLayout(Float.NaN, Float.NaN);
      for (YogaNode.Edge edge :
          new YogaNode.Edge[] {
            YogaNode.Edge.TOP, YogaNode.Edge.RIGHT, YogaNode.Edge.BOTTOM, YogaNode.Edge.LEFT
          }) {
        node.layoutBorder(edge);
        node.layoutPadding(edge);
        node.layoutMargin(edge);
      }
    } finally {
      node.freeNode();
    }
  }

  private static void expectIllegalArgument(Runnable operation) {
    try {
      operation.run();
      fail("Expected unsupported layout edge to be rejected");
    } catch (IllegalArgumentException expected) {
      // Java preflight must reject this before JNI.
    }
  }

  private static void expectIllegalState(Runnable operation) {
    try {
      operation.run();
      fail("Expected unmeasured node dirtying to be rejected");
    } catch (IllegalStateException expected) {
      // Java preflight must reject this before JNI.
    }
  }
}
