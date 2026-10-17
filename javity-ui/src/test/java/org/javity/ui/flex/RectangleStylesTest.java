package org.javity.ui.flex;

import static org.junit.Assert.*;

import org.javity.ui.yoga.YogaNode;
import org.javity.ui.yoga.YogaNode.Edge;
import org.javity.ui.yoga.YogaNode.Gutter;
import org.junit.Test;

public class RectangleStylesTest {
  @Test
  public void childSizingPreservesContainerBorder() {
    try (Flex root = new Flex("flex-col")) {
      Flex panel = root.add(new Flex("flex-col border-2 p-2"), "w-20 h-20");
      var item = panel.add(new com.badlogic.gdx.scenes.scene2d.Actor(), "w-5 h-5");
      root.setSize(100, 100);
      root.validate();
      assertEquals(10, item.getX(), .001);
    }
  }

  @Test
  public void roundedBorderReservesSpaceInsideLayout() {
    YogaNode node = YogaTestSupport.node();
    try {
      var style =
          UtilityStyles.apply(node, "rounded-lg border-2 border-cyan-400 bg-slate-800 w-20 h-10");
      node.calculateLayout(Float.NaN, Float.NaN);
      assertEquals(8, style.radiusFor(80, 40), .001);
      assertEquals(2, style.borderFor(80, 40), .001);
      assertEquals(2, node.layoutBorder(Edge.LEFT), .001);
      assertEquals(UtilityStyles.color("cyan-400"), style.borderColor());
    } finally {
      node.freeNode();
    }
  }

  @Test
  public void pillRadiusAndLargeBorderClampToActualSize() {
    YogaNode node = YogaTestSupport.node();
    try {
      var style = UtilityStyles.apply(node, "rounded-full border-8");
      assertEquals(20, style.radiusFor(200, 40), .001);
      assertEquals(5, style.borderFor(12, 10), .001);
    } finally {
      node.freeNode();
    }
  }

  @Test
  public void numericRadiusAndPlainBorderAreSupported() {
    YogaNode node = YogaTestSupport.node();
    try {
      var style = UtilityStyles.apply(node, "rounded-3 border text-cyan-400");
      assertEquals(12, style.radiusFor(80, 40), .001);
      assertEquals(1, style.borderFor(80, 40), .001);
      assertEquals(style.text(), style.borderColor());
    } finally {
      node.freeNode();
    }
  }

  @Test
  public void browserMeasuredTypographyPaletteAndSpacingMatchTailwind433() {
    YogaNode node = YogaTestSupport.node();
    try {
      var small = UtilityStyles.apply(node, "text-sm pt-1.5 pr-[12.5px] mb-2 gap-x-2.5");
      node.calculateLayout(Float.NaN, Float.NaN);
      assertEquals(14, small.fontSize(), .001);
      assertEquals(20, small.lineHeight(), .001);
      assertEquals(.875f, small.textScale(), .001);
      assertEquals(6, node.layoutPadding(Edge.TOP), .001);
      assertEquals(12.5f, node.layoutPadding(Edge.RIGHT), .001);
      assertEquals(8, node.layoutMargin(Edge.BOTTOM), .001);
      assertEquals(10, node.styleGap(Gutter.COLUMN), .001);
      assertEquals(UtilityStyles.color("#00d3f3"), UtilityStyles.color("cyan-400"));
      assertEquals(UtilityStyles.color("#155dfc"), UtilityStyles.color("blue-600"));
      assertEquals(UtilityStyles.color("#ffb900"), UtilityStyles.color("amber-400"));
      assertThrows(IllegalArgumentException.class, () -> UtilityStyles.apply(node, "hover:gap-2"));
      assertThrows(
          IllegalArgumentException.class, () -> UtilityStyles.apply(node, "md:bg-blue-600"));
      assertThrows(
          IllegalArgumentException.class, () -> UtilityStyles.apply(node, "hover:border-2"));
    } finally {
      node.freeNode();
    }
  }

  @Test
  public void roundedSmMatchesTheMeasuredTailwindRadius() {
    YogaNode node = YogaTestSupport.node();
    try {
      assertEquals(4, UtilityStyles.apply(node, "rounded-sm").radiusFor(20, 20), .001);
    } finally {
      node.freeNode();
    }
  }

  @Test
  public void unclassifiedTextUsesTheBrowserDefaultForegroundAndNoFill() {
    YogaNode node = YogaTestSupport.node();
    try {
      var visual = UtilityStyles.apply(node, "");
      assertNull(visual.background());
      assertEquals(UtilityStyles.color("#000000"), visual.text());
    } finally {
      node.freeNode();
    }
  }
}
