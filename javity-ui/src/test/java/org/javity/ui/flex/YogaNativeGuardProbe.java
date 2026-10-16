package org.javity.ui.flex;

import org.javity.ui.yoga.YogaBackends;
import org.javity.ui.yoga.YogaNode;

/** Child-process entry point so a native abort cannot terminate the JUnit VM. */
public final class YogaNativeGuardProbe {
  private YogaNativeGuardProbe() {}

  public static void main(String[] args) {
    YogaNode node = YogaBackends.createDefault().createNode();
    boolean guarded = false;
    try {
      switch (args[0]) {
        case "unmeasured-dirty" -> node.markDirty();
        case "border-all" -> node.layoutBorder(YogaNode.Edge.ALL);
        case "border-horizontal" -> node.layoutBorder(YogaNode.Edge.HORIZONTAL);
        case "border-vertical" -> node.layoutBorder(YogaNode.Edge.VERTICAL);
        case "padding-all" -> node.layoutPadding(YogaNode.Edge.ALL);
        case "padding-horizontal" -> node.layoutPadding(YogaNode.Edge.HORIZONTAL);
        case "padding-vertical" -> node.layoutPadding(YogaNode.Edge.VERTICAL);
        case "margin-all" -> node.layoutMargin(YogaNode.Edge.ALL);
        case "margin-horizontal" -> node.layoutMargin(YogaNode.Edge.HORIZONTAL);
        case "margin-vertical" -> node.layoutMargin(YogaNode.Edge.VERTICAL);
        case "measured-dirty" -> {
          node.setMeasureFunction(
              (width, widthMode, height, heightMode) -> new YogaNode.Size(4, 3));
          node.markDirty();
        }
        case "physical-layout-edges" -> {
          node.setWidth(24);
          node.setHeight(12);
          node.calculateLayout(24, 12);
          for (YogaNode.Edge edge :
              new YogaNode.Edge[] {
                YogaNode.Edge.TOP, YogaNode.Edge.RIGHT, YogaNode.Edge.BOTTOM, YogaNode.Edge.LEFT
              }) {
            node.layoutBorder(edge);
            node.layoutPadding(edge);
            node.layoutMargin(edge);
          }
        }
        default -> throw new IllegalArgumentException("Unknown probe: " + args[0]);
      }
    } catch (IllegalArgumentException | IllegalStateException expected) {
      guarded = true;
    } finally {
      node.freeNode();
    }
    if (guarded) {
      System.out.println("guarded");
    } else if (args[0].equals("measured-dirty") || args[0].equals("physical-layout-edges")) {
      System.out.println("valid");
    } else {
      System.exit(42);
    }
  }
}
