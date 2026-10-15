package org.javity.ui.declarative;

import static org.junit.Assert.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class UiMarkupTest {
  @Test
  public void parsesSupportedTagsEventsAndInterpolatedBindingsWithoutReadingThem() {
    AtomicInteger reads = new AtomicInteger();
    UiNode root =
        UiMarkup.parse(
            "<div id='root' class='flex-col'><span>Hello ${name}!</span><button"
                + " onClick='save'>Go</button></div>",
            Map.of("save", () -> {}),
            Map.of(
                "name",
                () -> {
                  reads.incrementAndGet();
                  return "Ada";
                }));

    assertEquals("View", root.type());
    assertEquals("root", root.key());
    assertEquals("Text", root.children().getFirst().type());
    Object text = root.children().getFirst().props().get("text");
    assertTrue(text instanceof java.util.function.Supplier<?>);
    assertEquals("Button", root.children().get(1).type());
    assertEquals(0, reads.get());
    assertEquals("Hello Ada!", ((java.util.function.Supplier<?>) text).get());
  }

  @Test
  public void parsesRegisteredImageAndLowercaseImgAlias() {
    UiNode image =
        UiMarkup.parse(
            "<img id='portrait' source='k4-fixture' fit='cover' class='rounded-full' />",
            Map.of(),
            Map.of());
    assertEquals("Image", image.type());
    assertEquals("portrait", image.key());
    assertEquals("k4-fixture", image.props().get("source"));
    assertEquals("cover", image.props().get("fit"));
    assertThrows(
        IllegalArgumentException.class,
        () -> UiMarkup.parse("<Image source='asset' onClick='x' />", Map.of(), Map.of()));
  }

  @Test
  public void imageMarkupRejectsElementAndTextChildrenForBothAliases() {
    assertThrows(
        IllegalArgumentException.class,
        () -> UiMarkup.parse("<Image source='portrait'><Text>child</Text></Image>", Map.of(), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> UiMarkup.parse("<Image source='portrait'>label</Image>", Map.of(), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> UiMarkup.parse("<img source='portrait'><span>child</span></img>", Map.of(), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> UiMarkup.parse("<img source='portrait'>label</img>", Map.of(), Map.of()));
  }

  @Test
  public void generatesStableTreePathKeysAndRejectsUnknownSurface() {
    UiNode root = UiMarkup.parse("<View><Text>ok</Text></View>", Map.of(), Map.of());
    assertEquals("0", root.key());
    assertEquals("0/0", root.children().getFirst().key());
    assertThrows(
        IllegalArgumentException.class, () -> UiMarkup.parse("<main/>", Map.of(), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> UiMarkup.parse("<Text style='color:red'/>", Map.of(), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            UiMarkup.parse(
                "<!DOCTYPE x [<!ENTITY y SYSTEM 'file:///etc/passwd'>]><Text>&y;</Text>",
                Map.of(),
                Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> UiMarkup.parse("<Button onClick='missing'/>", Map.of(), Map.of()));
  }

  @Test
  public void parsesNumericProgressValuesAndRejectsTextInterpolation() {
    UiNode progress = UiMarkup.parse("<Progress value='0.5' min='0' max='1'/>", Map.of(), Map.of());
    assertEquals(0.5f, progress.props().get("value"));
    assertEquals(0f, progress.props().get("min"));
    assertEquals(1f, progress.props().get("max"));

    UiNode animated =
        UiMarkup.parse("<Progress value='0.5' duration='0.4' delay='0.1'/>", Map.of(), Map.of());
    assertEquals(.4f, animated.props().get("duration"));
    assertEquals(.1f, animated.props().get("delay"));

    Object dynamic =
        UiMarkup.parse("<progress value='${amount}'/>", Map.of(), Map.of("amount", () -> .75f))
            .props()
            .get("value");
    assertTrue(dynamic instanceof java.util.function.Supplier<?>);
    assertEquals(.75f, ((java.util.function.Supplier<?>) dynamic).get());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            UiMarkup.parse(
                "<Progress value='${amount} percent'/>", Map.of(), Map.of("amount", () -> .5f)));
    assertThrows(
        IllegalArgumentException.class,
        () -> UiMarkup.parse("<Progress value='nope'/>", Map.of(), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> UiMarkup.parse("<Progress min='2' max='1'/>", Map.of(), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> UiMarkup.parse("<Progress max='1e400'/>", Map.of(), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> UiMarkup.parse("<Progress duration='-0.1'/>", Map.of(), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> UiMarkup.parse("<Progress delay='Infinity'/>", Map.of(), Map.of()));
  }

  @Test
  public void parsesExplicitTextWrapBoolean() {
    UiNode wrapped = UiMarkup.parse("<Text wrap='true'>a\nb\n</Text>", Map.of(), Map.of());
    assertEquals(Boolean.TRUE, wrapped.props().get("wrap"));
    assertEquals("a\nb\n", wrapped.props().get("text"));
    assertEquals(
        Boolean.FALSE,
        UiMarkup.parse("<Text wrap='false'/>", Map.of(), Map.of()).props().get("wrap"));
    assertThrows(
        IllegalArgumentException.class,
        () -> UiMarkup.parse("<Text wrap='yes'/>", Map.of(), Map.of()));
  }
}
