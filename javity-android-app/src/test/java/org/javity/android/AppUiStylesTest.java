package org.javity.android;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.javity.ui.flex.UtilityStyles;
import org.javity.ui.yoga.YogaNode;
import org.junit.Test;

/** CPU validation of production strings; no Theme, GL context or Yoga native provider is needed. */
public final class AppUiStylesTest {
  private static final String FIXTURE = "/org/javity/android/android-fixture.js";
  private static final int MAX_FIXTURE_BYTES = 64 * 1024;
  private static final Pattern CLASS_PROPERTY = Pattern.compile("\\bclassName\\s*:");
  private static final Pattern LITERAL_VALUE = Pattern.compile("\\s*\"([^\"\\\\\\r\\n]*)\"");

  @Test
  public void productionControlStylesUseSupportedUtilities() {
    for (String classes : List.of(AppUiStyles.JUMP, AppUiStyles.PAUSE, AppUiStyles.MOVEMENT)) {
      validateClasses(classes);
    }
  }

  @Test
  public void shippedFixtureLiteralClassNamesUseSupportedUtilities() throws Exception {
    String fixture;
    try (InputStream input = AppUiStylesTest.class.getResourceAsStream(FIXTURE)) {
      assertNotNull("The actual production React fixture must be on the test classpath", input);
      byte[] bytes = input.readNBytes(MAX_FIXTURE_BYTES + 1);
      assertTrue(
          "Fixture exceeds the intentionally bounded literal-style test",
          bytes.length <= MAX_FIXTURE_BYTES);
      fixture = new String(bytes, StandardCharsets.UTF_8);
    }
    Matcher properties = CLASS_PROPERTY.matcher(fixture);
    int count = 0;
    while (properties.find()) {
      assertTrue(
          "Fixture class inventory grew beyond this small literal-only fixture", ++count <= 16);
      Matcher value = LITERAL_VALUE.matcher(fixture);
      value.region(properties.end(), fixture.length());
      assertTrue(
          "Fixture className values must remain direct unescaped string literals",
          value.lookingAt());
      validateClasses(value.group(1));
    }
    assertTrue("Fixture must contain actual className declarations", count > 0);
  }

  private static void validateClasses(String classes) {
    assertTrue(
        "Production classes must be nonempty and bounded",
        !classes.isBlank() && classes.length() <= 512);
    YogaNode node =
        (YogaNode)
            Proxy.newProxyInstance(
                YogaNode.class.getClassLoader(),
                new Class<?>[] {YogaNode.class},
                (proxy, method, arguments) -> {
                  if (method.getReturnType() != void.class) {
                    throw new AssertionError(
                        "Style validation unexpectedly queried Yoga: " + method.getName());
                  }
                  return null;
                });
    UtilityStyles.apply(node, classes);
  }
}
