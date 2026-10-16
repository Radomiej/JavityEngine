package org.javity.ui.flex;

import org.javity.ui.yoga.YogaBackends;
import org.javity.ui.yoga.YogaNode;

final class YogaTestSupport {
  private YogaTestSupport() {}

  static YogaNode node() {
    return YogaBackends.createDefault().createNode();
  }
}
