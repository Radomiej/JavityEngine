package org.javity.ui.flex;

import static org.junit.Assert.assertTrue;
import static org.lwjgl.util.yoga.Yoga.YGNodeFree;
import static org.lwjgl.util.yoga.Yoga.YGNodeNew;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

public class UiDevelopmentSampleStyleTest {
  @Test
  public void everyDevelopmentHudClassIsAcceptedByNativeUtilityCatalog() throws Exception {
    String source = Files.readString(Path.of("../web/javity-ui/src/dev.tsx"));
    Matcher classes = Pattern.compile("className=\"([^\"]+)\"").matcher(source);
    int checked = 0;
    while (classes.find()) {
      long node = YGNodeNew();
      try {
        UtilityStyles.apply(node, classes.group(1));
        checked++;
      } finally {
        YGNodeFree(node);
      }
    }
    assertTrue("the development HUD must contain validated class strings", checked > 0);
  }
}
