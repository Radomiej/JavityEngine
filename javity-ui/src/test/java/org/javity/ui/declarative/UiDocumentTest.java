package org.javity.ui.declarative;

import static org.junit.Assert.*;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import java.util.List;
import java.util.Map;
import org.javity.ui.flex.Flex;
import org.javity.ui.flex.UtilityStyles;
import org.javity.ui.yoga.YogaBackends;
import org.javity.ui.yoga.YogaNode;
import org.javity.ui.yoga.YogaNode.Edge;
import org.javity.ui.yoga.YogaNode.Gutter;
import org.junit.Test;

public class UiDocumentTest {
  @Test
  public void viewOnlyDocumentUsesStableActorsAndLeavesTreeIntactOnValidationError() {
    try (UiDocument document = new UiDocument(null)) {
      UiNode first =
          view("root", "flex-row", view("panel", "p-[12px]", element("leaf", "w-[24px] h-[24px]")));
      document.render(first);
      Actor panel = document.actor("panel");
      Actor leaf = document.actor("leaf");
      document.root().setSize(100, 100);
      document.root().validate();
      assertSame(document.actor("root"), document.root().getChildren().first());
      assertSame(leaf, ((Flex) panel).getChildren().first());
      assertEquals(12, leaf.getX(), .01f);

      UiNode duplicate = view("root", "flex-row", view("same", "", element("same", "")));
      assertThrows(IllegalArgumentException.class, () -> document.render(duplicate));
      assertSame(panel, document.actor("panel"));
      assertSame(leaf, document.actor("leaf"));
      assertSame(document.actor("root"), document.root().getChildren().first());

      document.render(
          view("root", "flex-row", view("panel", "p-[4px]", element("leaf", "w-[24px] h-[24px]"))));
      assertSame(panel, document.actor("panel"));
      assertSame(leaf, document.actor("leaf"));
      document.root().validate();
      assertEquals(4, leaf.getX(), .01f);
    }
  }

  @Test
  public void imageSchemaRequiresStaticSourceAndRejectsUnknownFitBeforeMutation() {
    try (UiDocument document = new UiDocument(null)) {
      document.render(view("root", "", element("old", "")));
      Actor root = document.actor("root");
      Actor old = document.actor("old");
      UiNode imageWithoutSource = element("new-image", "");
      imageWithoutSource = new UiNode("Image", "new-image", "", Map.of("fit", "cover"), List.of());
      UiNode missingSource = imageWithoutSource;
      assertThrows(
          IllegalArgumentException.class, () -> document.render(view("root", "", missingSource)));
      UiNode badFit =
          new UiNode(
              "Image",
              "new-image",
              "",
              Map.of("source", "avatar", "fit", "contain cover"),
              List.of());
      assertThrows(IllegalArgumentException.class, () -> document.render(view("root", "", badFit)));
      assertSame(root, document.actor("root"));
      assertSame(old, document.actor("old"));
    }
  }

  @Test
  public void imageRejectsDirectUiNodeChildrenBeforeReplacingMountedActors() {
    try (UiDocument document = new UiDocument(null)) {
      document.render(view("root", "", element("retained", "")));
      Actor root = document.actor("root");
      Actor retained = document.actor("retained");
      UiNode imageWithChild =
          new UiNode(
              "Image",
              "invalid-image",
              "",
              Map.of("source", "portrait"),
              List.of(
                  UiNode.element("Text", "image-text", "", Map.of("text", "child"), List.of())));

      IllegalArgumentException error =
          assertThrows(
              IllegalArgumentException.class,
              () -> document.render(view("root", "", element("retained", ""), imageWithChild)));

      assertTrue(error.getMessage().contains("Image cannot have children"));
      assertSame(root, document.actor("root"));
      assertSame(retained, document.actor("retained"));
      assertNull(document.actor("invalid-image"));
    }
  }

  @Test
  public void nestedViewStylesContributeToOneYogaNode() {
    try (UiDocument document = new UiDocument(null)) {
      UiNode leaf = element("leaf", "w-10 h-10");
      UiNode panel = view("panel", "w-80 p-4", leaf);
      document.render(view("screen", "flex-col", panel));
      document.root().setSize(400, 200);
      document.root().validate();

      Actor nativePanel = document.actor("panel");
      Actor nativeLeaf = document.actor("leaf");
      assertEquals(320, nativePanel.getWidth(), .01f);
      assertEquals(72, nativePanel.getHeight(), .01f);
      assertEquals(16, nativeLeaf.getX(), .01f);
      assertEquals(16, nativeLeaf.getY(), .01f);
      assertEquals(40, nativeLeaf.getWidth(), .01f);
      assertEquals(40, nativeLeaf.getHeight(), .01f);
    }
  }

  @Test
  public void supportedBracketSizesAndHexColorsAreStrict() {
    YogaNode node = YogaBackends.createDefault().createNode();
    try {
      UtilityStyles.Visual visual =
          UtilityStyles.apply(
              node,
              "w-[120px] h-[24px] p-[12px] gap-[8px] bg-[#abc] text-[#11223344] border-[#aabbcc]");
      node.calculateLayout(Float.NaN, Float.NaN);
      assertEquals(120, node.layoutWidth(), .001f);
      assertEquals(24, node.layoutHeight(), .001f);
      assertEquals(12, node.layoutPadding(Edge.LEFT), .001f);
      assertEquals(8, node.styleGap(Gutter.ALL), .001f);
      assertEquals(0xaa / 255f, visual.background().r, .01f);
      assertThrows(IllegalArgumentException.class, () -> UtilityStyles.apply(node, "w-[-2px]"));
      assertThrows(IllegalArgumentException.class, () -> UtilityStyles.color("#12"));
    } finally {
      node.freeNode();
    }
  }

  @Test
  public void progressValueUsesNumericTypeBeforeThemeAllocation() {
    try (UiDocument document = new UiDocument(null)) {
      UiNode numeric = UiNode.element("Progress", "progress", "", Map.of("value", 0.5f), List.of());
      IllegalStateException noTheme =
          assertThrows(IllegalStateException.class, () -> document.render(numeric));
      assertTrue(noTheme.getMessage().contains("UiTheme is required"));
      UiNode text = UiNode.element("Progress", "progress", "", Map.of("value", "half"), List.of());
      assertThrows(IllegalArgumentException.class, () -> document.render(text));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              document.render(
                  UiNode.element(
                      "Progress", "bad-duration", "", Map.of("duration", -1), List.of())));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              document.render(
                  UiNode.element(
                      "Progress", "bad-delay", "", Map.of("delay", Double.NaN), List.of())));
    }
  }

  @Test
  public void textWrapIsATypedTextOnlyProp() {
    try (UiDocument document = new UiDocument(null)) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              document.render(
                  UiNode.element("Text", "bad-wrap", "", Map.of("wrap", "true"), List.of())));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              document.render(
                  UiNode.element("View", "bad-wrap", "", Map.of("wrap", true), List.of())));
    }
  }

  @Test
  public void sliderRejectsFloatUnrepresentableNumbersBeforeMutatingTheTree() {
    try (UiDocument document = new UiDocument(null)) {
      document.render(view("root", "", element("retained", "")));
      Actor retained = document.actor("retained");
      UiNode invalidSlider =
          UiNode.element(
              "Slider",
              "invalid-slider",
              "",
              Map.of("min", 0, "max", 1, "value", Double.MAX_VALUE, "step", .1),
              List.of());
      IllegalArgumentException overflow =
          assertThrows(
              IllegalArgumentException.class,
              () -> document.render(view("root", "", element("retained", ""), invalidSlider)));
      assertTrue(overflow.getMessage().contains("finite as a float"));
      assertSame(retained, document.actor("retained"));
      assertNull(document.actor("invalid-slider"));

      UiNode underflowSlider =
          UiNode.element(
              "Slider",
              "invalid-slider",
              "",
              Map.of("min", 0, "max", 1, "value", Double.MIN_VALUE, "step", .1),
              List.of());
      assertThrows(
          IllegalArgumentException.class,
          () -> document.render(view("root", "", element("retained", ""), underflowSlider)));
      assertSame(retained, document.actor("retained"));
      assertNull(document.actor("invalid-slider"));
    }
  }

  @Test
  public void stateVariantsAreLimitedToImplementedInteractiveNodeTypesBeforeMutation() {
    try (UiDocument document = new UiDocument(null)) {
      UiNode original = view("root", "", element("panel", ""));
      document.render(original);
      Actor panel = document.actor("panel");
      UiNode unsupported = view("root", "hover:bg-blue-600", element("panel", ""));
      IllegalArgumentException error =
          assertThrows(IllegalArgumentException.class, () -> document.render(unsupported));
      assertTrue(error.getMessage().contains("not supported on View"));
      assertSame(panel, document.actor("panel"));
      assertSame(panel.getParent(), document.actor("root"));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              document.render(
                  UiNode.element(
                      "Text", "label", "active:text-red-500", Map.of("text", "x"), List.of())));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              document.render(
                  UiNode.element(
                      "TextField", "field", "active:border-blue-600", Map.of(), List.of())));
    }
  }

  @Test
  public void closeUnmountsOnlyTheDocumentRootFromItsBorrowedHost() {
    UiDocument document = new UiDocument(null);
    document.render(view("screen", "", element("panel", "")));
    Flex root = document.root();
    WidgetGroup host = new WidgetGroup();
    host.addActor(root);

    document.close();
    document.close();

    assertNull(root.getParent());
    assertEquals(0, host.getChildren().size);
    assertTrue(document.isClosed());
  }

  private static UiNode view(String key, String classes, UiNode... children) {
    return UiNode.element("View", key, classes, Map.of(), List.of(children));
  }

  private static UiNode element(String key, String classes) {
    return UiNode.element("View", key, classes, Map.of(), List.of());
  }
}
