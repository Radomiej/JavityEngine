package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.javity.script.ScriptUiRuntime;
import org.javity.ui.declarative.UiDocument;
import org.javity.ui.declarative.UiNode;
import org.javity.ui.flex.UiTheme;
import org.javity.ui.flex.UtilityStyles;
import org.javity.ui.flex.YogaMeasuredActor;

/**
 * Real OpenGL proof scene that renders the same keyed fixture as the Tailwind browser reference.
 */
final class UiParityDemo extends ApplicationAdapter {
  private static final List<String> BOX_KEYS =
      List.of(
          "screen",
          "nested",
          "nested-header",
          "header-left",
          "header-label",
          "header-right",
          "nested-action",
          "nested-content",
          "flex-left",
          "left-text",
          "flex-right",
          "right-text",
          "controls",
          "classic-button",
          "classic-input",
          "classic-progress",
          "classic-card",
          "card-title",
          "card-body",
          "typography",
          "type-inherited",
          "type-xl",
          "type-2xl",
          "scroll-sample");
  private final boolean smoke;
  private final Map<String, Integer> statePixelErrors = new HashMap<>();
  private Stage stage;
  private UiTheme theme;
  private UiDocument document;
  private int frames;
  private int fixtureClicks;
  private boolean complete;

  UiParityDemo(boolean smoke) {
    this.smoke = smoke;
  }

  @Override
  public void create() {
    theme = new UiTheme();
    verifyFontSizeCache();
    stage = new Stage(new ScreenViewport());
    Gdx.input.setInputProcessor(stage);
    document = new UiDocument(theme);
    JsonValue fixture =
        new JsonReader().parse(Gdx.files.classpath("org/javity/demo/ui-reference/fixture.json"));
    document.render(read(fixture));
    document.root().setFillParent(true);
    stage.addActor(document.root());
    stage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
  }

  private static void verifyFontSizeCache() {
    UiTheme thirtyTwo =
        UiTheme.fromTrueType(Gdx.files.classpath("org/javity/ui/fonts/OpenSans-Regular.ttf"), 32);
    try {
      var base = thirtyTwo.label("MMMMMMMM", "text-base");
      var large = thirtyTwo.label("MMMMMMMM", "text-2xl");
      var baseFont = base.getStyle().font;
      var largeFont = large.getStyle().font;
      float ratio =
          new com.badlogic.gdx.graphics.g2d.GlyphLayout(baseFont, base.getText()).width
              / new com.badlogic.gdx.graphics.g2d.GlyphLayout(largeFont, large.getText()).width;
      if (baseFont.getData().lineHeight != 24
          || largeFont.getData().lineHeight != 32
          || ratio < .60f
          || ratio > .73f)
        throw new AssertionError(
            "fromTrueType(32) must resolve Tailwind 16px/24px and 24px/32px fonts; lineHeights="
                + baseFont.getData().lineHeight
                + "/"
                + largeFont.getData().lineHeight
                + ", widthRatio="
                + ratio);
    } finally {
      thirtyTwo.dispose();
    }

    UiTheme owner = new UiTheme();
    BitmapFont borrowedFont = owner.getFont();
    float originalLineHeight = borrowedFont.getData().lineHeight;
    float originalScaleX = borrowedFont.getData().scaleX;
    UiTheme borrower = new UiTheme(borrowedFont, false);
    try {
      borrower.label("MMMM", "text-2xl");
      if (borrowedFont.getData().lineHeight != originalLineHeight
          || borrowedFont.getData().scaleX != originalScaleX)
        throw new AssertionError("Borrowed BitmapFont metrics were mutated by size styling");
    } finally {
      borrower.dispose();
      owner.dispose();
    }
  }

  private UiNode read(JsonValue value) {
    Map<String, Object> props = new HashMap<>();
    JsonValue fields = value.get("props");
    if (fields != null)
      for (JsonValue p = fields.child; p != null; p = p.next) {
        Object converted =
            switch (p.type()) {
              case stringValue -> p.asString();
              case booleanValue -> p.asBoolean();
              case doubleValue -> p.asFloat();
              case longValue -> p.asLong();
              default ->
                  throw new IllegalArgumentException("Unsupported fixture property " + p.name);
            };
        props.put(p.name, converted);
      }
    if (value.getString("key", "").equals("classic-button"))
      props.put("onClick", (Runnable) () -> fixtureClicks++);
    List<UiNode> children = new ArrayList<>();
    JsonValue list = value.get("children");
    if (list != null)
      for (JsonValue child = list.child; child != null; child = child.next)
        children.add(read(child));
    return new UiNode(
        value.getString("type"),
        value.getString("key"),
        value.getString("classes", ""),
        props,
        children);
  }

  @Override
  public void render() {
    ScreenUtils.clear(1, 1, 1, 1);
    stage.act(Gdx.graphics.getDeltaTime());
    stage.draw();
    frames++;
    if (!complete && frames >= (smoke ? 120 : 1)) {
      complete = true;
      capture();
      if (smoke) Gdx.app.exit();
    }
  }

  private void capture() {
    Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, 1);
    Pixmap pixels =
        ScreenUtils.getFrameBufferPixmap(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      Gdx.files.local("artifacts").mkdirs();
      writeScreenshot("artifacts/ui-parity-native.png", pixels);
      StringBuilder out =
          new StringBuilder("{\n  \"viewport\": {\"width\": ")
              .append(Gdx.graphics.getWidth())
              .append(", \"height\": ")
              .append(Gdx.graphics.getHeight())
              .append("},\n  \"frames\": ")
              .append(frames)
              .append(",\n  \"els\": {\n");
      for (int i = 0; i < BOX_KEYS.size(); i++) {
        String key = BOX_KEYS.get(i);
        var actor = document.actor(key);
        var pos = actor.localToStageCoordinates(new com.badlogic.gdx.math.Vector2());
        float domX = pos.x;
        float domY = Gdx.graphics.getHeight() - pos.y - actor.getHeight();
        out.append("    \"")
            .append(key)
            .append("\": {\"rect\": {\"x\": ")
            .append(round(domX))
            .append(", \"y\": ")
            .append(round(domY))
            .append(", \"width\": ")
            .append(round(actor.getWidth()))
            .append(", \"height\": ")
            .append(round(actor.getHeight()))
            .append("}, \"pixelCenter\": ")
            .append(pixelCenter(pixels, domX + actor.getWidth() / 2, domY + actor.getHeight() / 2));
        if (actor instanceof com.badlogic.gdx.scenes.scene2d.ui.Label)
          out.append(", \"glyphPixels\": ")
              .append(glyphBounds(pixels, domX, domY, actor.getWidth(), actor.getHeight()));
        out.append("}").append(i + 1 == BOX_KEYS.size() ? "\n" : ",\n");
      }
      var scroll = document.actor("scroll-sample");
      var scrollPosition = scroll.localToStageCoordinates(new com.badlogic.gdx.math.Vector2());
      out.append("  },\n  \"scrollbarThumbPixels\": ")
          .append(
              colorBounds(
                  pixels,
                  scrollPosition.x,
                  Gdx.graphics.getHeight() - scrollPosition.y - scroll.getHeight(),
                  scroll.getWidth(),
                  scroll.getHeight()))
          .append("\n}\n");
      Gdx.files.local("artifacts/ui-parity-native-metrics.json").writeString(out.toString(), false);
      compareToBrowser(pixels);
      captureStates();
      captureWrappedTextProof();
      captureMountedTextMutationProof();
      captureBorrowedWrappedFontProof();
      K4ImageParityProbe.capture();
    } finally {
      pixels.dispose();
    }
  }

  /** Proves same-width Yoga cache invalidation through the mounted Graal/React bridge. */
  private void captureMountedTextMutationProof() {
    BitmapFont source = theme.getFont();
    var sourceData = source.getData();
    float sourceScaleX = sourceData.scaleX;
    float sourceScaleY = sourceData.scaleY;
    float sourceLineHeight = sourceData.lineHeight;
    int sourcePolishAdvance = sourceData.getGlyph('Ż').xadvance;
    var sourceTexture = source.getRegion().getTexture();
    UiTheme borrower = new UiTheme(source, false);
    Stage proofStage = new Stage(new ScreenViewport());
    UiDocument proofDocument = new UiDocument(borrower);
    ScriptUiRuntime runtime = new ScriptUiRuntime(proofDocument);
    String mountFunction =
        "globalThis.renderWrapState=(width,font,text,wrap,reverse)=>{const"
            + " R=JavityReact.React,V=JavityReact.View,T=JavityReact.Text;const"
            + " panel=R.createElement(V,{id:'wrap-panel',className:'w-['+width+'px] '+font},"
            + "R.createElement(T,{id:'wrap-text',className:'w-full',text:text,wrap:wrap}));const"
            + " peer=R.createElement(T,{id:'wrap-peer',className:'w-[80px]',text:'peer',wrap:true});JavityReact.render(R.createElement(V,{id:'wrap-root',className:'flex"
            + " flex-col items-start'},...(reverse?[peer,panel]:[panel,peer])))}";
    try {
      proofStage.getViewport().update(1100, 720, true);
      proofDocument.mount(proofStage);
      proofDocument.root().setSize(300, 500);
      runtime.eval(mountFunction);
      Actor textActor = null;
      StringBuilder metrics = new StringBuilder("{\n  \"states\": [\n");
      List<String> names =
          List.of("short", "long", "shortAgain", "largeFont", "nowrap", "narrow", "wide");
      List<String> texts =
          List.of(
              "Zażółć gęślą jaźń.",
              "Zażółć gęślą jaźń i białą łąkę. Źródło światła, żółw i port.",
              "Zażółć gęślą jaźń.",
              "Zażółć gęślą jaźń i białą łąkę. Źródło światła, żółw i port.",
              "Zażółć gęślą jaźń i białą łąkę. Źródło światła, żółw i port.",
              "Zażółć gęślą jaźń i białą łąkę. Źródło światła, żółw i port.",
              "Zażółć gęślą jaźń i białą łąkę. Źródło światła, żółw i port.");
      int[] widths = {140, 140, 140, 140, 140, 96, 240};
      String[] fonts = {
        "text-sm", "text-sm", "text-sm", "text-2xl", "text-2xl", "text-base", "text-base"
      };
      boolean[] wrapping = {true, true, true, true, false, true, true};
      for (int index = 0; index < names.size(); index++) {
        runtime.eval(
            "renderWrapState("
                + widths[index]
                + ","
                + quote(fonts[index])
                + ","
                + quote(texts.get(index))
                + ","
                + wrapping[index]
                + ","
                + (index == 1)
                + ")");
        Actor current = proofDocument.actor("wrap-text");
        if (textActor == null) textActor = current;
        else if (textActor != current)
          throw new AssertionError("Keyed wrapped Text actor changed during " + names.get(index));
        proofDocument.root().validate();
        proofStage.act(0);
        com.badlogic.gdx.Gdx.gl.glClearColor(1, 1, 1, 1);
        com.badlogic.gdx.Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        proofStage.draw();
        YogaMeasuredActor measured = (YogaMeasuredActor) current;
        List<String> lines = measured.measureForContentWidth(current.getWidth()).lines();
        if (index == 0 && lines.size() != 1)
          throw new AssertionError("Short paragraph should occupy one line: " + lines);
        if (index == 1 && lines.size() <= 1)
          throw new AssertionError("Same-width long paragraph did not wrap: " + lines);
        if (index == 2 && lines.size() != 1)
          throw new AssertionError("Same-width short update retained stale lines: " + lines);
        if (index == 3 && lines.size() <= 1)
          throw new AssertionError("Inherited text-2xl update did not wrap: " + lines);
        if (index == 4 && lines.size() != 1)
          throw new AssertionError("wrap=false did not restore one line: " + lines);
        if ((index == 5 || index == 6) && lines.size() <= 1)
          throw new AssertionError("Width resize failed to reflow paragraph: " + lines);
        Actor panel = proofDocument.actor("wrap-panel");
        if (Math.abs(panel.getWidth() - widths[index]) > .05f)
          throw new AssertionError("Authored width did not reach Yoga: " + panel.getWidth());
        if (current.getHeight() <= 0)
          throw new AssertionError("Wrapped Text has no measured height: " + current.getHeight());
        Actor peer = proofDocument.actor("wrap-peer");
        boolean reversed = index == 1;
        if ((reversed && peer.getZIndex() >= panel.getZIndex())
            || (!reversed && panel.getZIndex() >= peer.getZIndex()))
          throw new AssertionError("Keyed sibling order was not applied: " + names.get(index));
        if (sourceData.scaleX != sourceScaleX
            || sourceData.scaleY != sourceScaleY
            || sourceData.lineHeight != sourceLineHeight
            || sourceData.getGlyph('Ż').xadvance != sourcePolishAdvance
            || sourceTexture.getWidth() <= 0)
          throw new AssertionError("Borrowed font data changed during mounted text mutation");
        if (index > 0) metrics.append(",\n");
        metrics
            .append("    {\"name\": ")
            .append(jsonString(names.get(index)))
            .append(", \"panelWidth\": ")
            .append(round(panel.getWidth()))
            .append(", \"textHeight\": ")
            .append(round(current.getHeight()))
            .append(", \"lineCount\": ")
            .append(lines.size())
            .append(", \"lineTexts\": ")
            .append(jsonStrings(lines))
            .append(", \"actorIdentityStable\": true}");
      }
      Gdx.files
          .local("artifacts/k2-fix1-mounted-updates.json")
          .writeString(
              metrics
                  .append(
                      "\n"
                          + "  ],\n"
                          + "  \"sameWidthMutations\": true,\n"
                          + "  \"borrowedFontUnchanged\": true\n"
                          + "}\n")
                  .toString(),
              false);
    } finally {
      runtime.close();
      proofDocument.close();
      proofStage.dispose();
      borrower.dispose();
    }
    if (sourceTexture.getWidth() <= 0
        || sourceData.scaleX != sourceScaleX
        || sourceData.scaleY != sourceScaleY
        || sourceData.lineHeight != sourceLineHeight
        || sourceData.getGlyph('Ż').xadvance != sourcePolishAdvance)
      throw new AssertionError("Closing mounted borrower changed the source font or atlas");
  }

  private static String quote(String value) {
    return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
  }

  private static String jsonString(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
  }

  private void captureWrappedTextProof() {
    JsonValue fixture =
        new JsonReader()
            .parse(Gdx.files.classpath("org/javity/demo/ui-reference/k2-wrap-fixture.json"));
    Stage proofStage = new Stage(new ScreenViewport());
    UiDocument proofDocument = new UiDocument(theme);
    try {
      proofStage.getViewport().update(1100, 720, true);
      proofDocument.render(wrapTree(fixture, false));
      proofDocument.root().setSize(1100, 720);
      proofStage.addActor(proofDocument.root());
      StringBuilder output =
          new StringBuilder(
              "{\n"
                  + "  \"viewport\": {\"width\": 1100, \"height\": 720},\n"
                  + "  \"font\": \"Open Sans\",\n"
                  + "  \"states\": {\n");
      for (boolean wide : List.of(false, true)) {
        if (wide) proofDocument.render(wrapTree(fixture, true));
        proofDocument.root().invalidateHierarchy();
        proofStage.getViewport().apply(true);
        proofStage.getViewport().update(1100, 720, true);
        ScreenUtils.clear(1, 1, 1, 1);
        proofStage.draw();
        Pixmap pixels =
            ScreenUtils.getFrameBufferPixmap(
                0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
          String state = wide ? "wide" : "narrow";
          writeScreenshot("artifacts/k2-wrap-native-" + state + ".png", pixels);
          if (wide) output.append(",\n");
          output.append("    \"").append(state).append("\": {\n");
          int itemIndex = 0;
          for (JsonValue item = fixture.get("paragraphs").child; item != null; item = item.next) {
            if (itemIndex++ > 0) output.append(",\n");
            String id = item.getString("id");
            var actor = proofDocument.actor(id);
            var position = actor.localToStageCoordinates(new com.badlogic.gdx.math.Vector2());
            float x = position.x;
            float y = Gdx.graphics.getHeight() - position.y - actor.getHeight();
            float lineHeight = item.getInt("lineHeight");
            float contentHeight = actor.getHeight() - 18;
            int lineCount = Math.max(1, Math.round(contentHeight / lineHeight));
            List<String> lineTexts =
                ((YogaMeasuredActor) actor).measureForContentWidth(actor.getWidth() - 18).lines();
            String ink = wrapInkBounds(pixels, x, y, actor.getWidth() + 64, actor.getHeight());
            int sampleX = Math.min(pixels.getWidth() - 1, Math.round(x + actor.getWidth() - 5));
            int sampleY = Math.max(0, pixels.getHeight() - 1 - Math.round(y + 5));
            int sample = pixels.getPixel(sampleX, sampleY);
            float overflow = 0;
            if (!"null".equals(ink)) {
              JsonValue box = new JsonReader().parse(ink);
              float right = box.getFloat("x") + box.getFloat("width");
              overflow = Math.max(0, right - (x + actor.getWidth()));
            }
            output
                .append("      \"")
                .append(id)
                .append("\": {\"x\": ")
                .append(round(x))
                .append(", \"y\": ")
                .append(round(y))
                .append(", \"width\": ")
                .append(round(actor.getWidth()))
                .append(", \"height\": ")
                .append(round(actor.getHeight()))
                .append(", \"contentWidth\": ")
                .append(round(actor.getWidth() - 18))
                .append(", \"lineHeight\": ")
                .append(round(lineHeight))
                .append(", \"fontSize\": ")
                .append(item.getInt("fontSize"))
                .append(", \"lineCount\": ")
                .append(lineCount)
                .append(", \"lineTexts\": ")
                .append(jsonStrings(lineTexts))
                .append(", \"fontMetrics\": {\"capHeight\": ")
                .append(
                    round(
                        ((com.badlogic.gdx.scenes.scene2d.ui.Label) actor)
                            .getStyle()
                            .font
                            .getData()
                            .capHeight))
                .append(", \"ascent\": ")
                .append(
                    round(
                        ((com.badlogic.gdx.scenes.scene2d.ui.Label) actor)
                            .getStyle()
                            .font
                            .getData()
                            .ascent))
                .append(", \"descent\": ")
                .append(
                    round(
                        ((com.badlogic.gdx.scenes.scene2d.ui.Label) actor)
                            .getStyle()
                            .font
                            .getData()
                            .descent))
                .append(", \"scaleY\": ")
                .append(
                    round(
                        ((com.badlogic.gdx.scenes.scene2d.ui.Label) actor)
                            .getStyle()
                            .font
                            .getScaleY()))
                .append("}")
                .append(", \"inkBounds\": ")
                .append(ink)
                .append(", \"horizontalOverflowPx\": ")
                .append(round(overflow))
                .append(", \"backgroundSample\": [")
                .append((sample >>> 24) & 255)
                .append(", ")
                .append((sample >>> 16) & 255)
                .append(", ")
                .append((sample >>> 8) & 255)
                .append("]}");
          }
          output.append("\n    }");
        } finally {
          pixels.dispose();
        }
      }
      Gdx.files
          .local("artifacts/k2-wrap-native-metrics.json")
          .writeString(output.append("\n  }\n}\n").toString(), false);
    } finally {
      proofDocument.close();
      proofStage.dispose();
    }
  }

  private static UiNode wrapTree(JsonValue fixture, boolean wide) {
    List<UiNode> paragraphs = new ArrayList<>();
    for (JsonValue item = fixture.get("paragraphs").child; item != null; item = item.next) {
      String id = item.getString("id");
      int width = item.getInt(wide ? "wide" : "narrow");
      String classes =
          "w-["
              + width
              + "px] p-2 border border-[#9ca3af] bg-[#f9fafb] text-[#111827] "
              + item.getString("fontClass")
              + (id.equals("wrap-14-pl") ? " opacity-50" : "");
      paragraphs.add(
          UiNode.element(
              "Text",
              id,
              classes,
              Map.of("text", item.getString("text"), "wrap", true),
              List.of()));
    }
    return UiNode.element(
        "View",
        "k2-wrap-root",
        "flex flex-col items-start gap-2 p-[10px] w-full h-full",
        Map.of(),
        paragraphs);
  }

  private void captureBorrowedWrappedFontProof() {
    BitmapFont source = theme.getFont();
    var sourceData = source.getData();
    float scaleX = sourceData.scaleX;
    float scaleY = sourceData.scaleY;
    float lineHeight = sourceData.lineHeight;
    var sourcePolish = sourceData.getGlyph('Ż');
    int polishAdvance = sourcePolish.xadvance;
    var texture = source.getRegion().getTexture();
    UiTheme borrower = new UiTheme(source, false);
    Stage borrowedStage = new Stage(new ScreenViewport());
    try {
      borrowedStage.getViewport().update(1100, 180, true);
      var small = borrower.label("Zażółć gęślą jaźń i białą łąkę.", "text-sm", true);
      var large = borrower.label("Źródło światła, żółw i port.", "text-2xl", true);
      small.setBounds(16, 92, 250, 72);
      large.setBounds(16, 12, 430, 72);
      borrowedStage.addActor(small);
      borrowedStage.addActor(large);
      ScreenUtils.clear(1, 1, 1, 1);
      borrowedStage.draw();
      Pixmap pixels =
          ScreenUtils.getFrameBufferPixmap(
              0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      try {
        writeScreenshot("artifacts/k2-wrap-borrowed-font.png", pixels);
      } finally {
        pixels.dispose();
      }
      if (sourceData.scaleX != scaleX
          || sourceData.scaleY != scaleY
          || sourceData.lineHeight != lineHeight
          || sourceData.getGlyph('Ż').xadvance != polishAdvance)
        throw new AssertionError("Wrapped borrowed fonts changed source BitmapFontData");
    } finally {
      borrower.dispose();
      borrowedStage.dispose();
    }
    if (texture.getWidth() <= 0
        || sourceData.scaleX != scaleX
        || sourceData.scaleY != scaleY
        || sourceData.lineHeight != lineHeight
        || sourceData.getGlyph('Ż').xadvance != polishAdvance)
      throw new AssertionError("Disposing wrapped borrower changed the source font or atlas");
  }

  private void captureStates() {
    var button = (com.badlogic.gdx.scenes.scene2d.ui.Button) document.actor("classic-button");
    var input = (com.badlogic.gdx.scenes.scene2d.ui.TextField) document.actor("classic-input");
    var inputCenter =
        input.localToStageCoordinates(
            new com.badlogic.gdx.math.Vector2(input.getWidth() / 2, input.getHeight() / 2));
    var inputScreen = stage.stageToScreenCoordinates(inputCenter);
    stage.mouseMoved(Math.round(inputScreen.x), Math.round(inputScreen.y));
    stage.act(0);
    redraw();
    captureScreenshot("artifacts/ui-parity-native-input-hover.png", "input-hover");
    var center =
        button.localToStageCoordinates(
            new com.badlogic.gdx.math.Vector2(button.getWidth() / 2, button.getHeight() / 2));
    var screen = stage.stageToScreenCoordinates(center);
    stage.mouseMoved(Math.round(screen.x), Math.round(screen.y));
    stage.act(0);
    redraw();
    captureScreenshot("artifacts/ui-parity-native-hover.png", "hover");

    stage.setKeyboardFocus(input);
    if (!input.getStyle().focusedFontColor.equals(UtilityStyles.color("blue-600")))
      throw new AssertionError("TextField focus:text utility did not reach focusedFontColor");
    stage.act(0);
    redraw();
    captureScreenshot("artifacts/ui-parity-native-focus.png", "focus");

    stage.mouseMoved(-1, -1);
    button.setDisabled(true);
    stage.act(0);
    redraw();
    int clicksBefore = fixtureClicks;
    var buttonCenter =
        button.localToStageCoordinates(
            new com.badlogic.gdx.math.Vector2(button.getWidth() / 2, button.getHeight() / 2));
    var buttonScreen = stage.stageToScreenCoordinates(buttonCenter);
    stage.touchDown(
        Math.round(buttonScreen.x),
        Math.round(buttonScreen.y),
        0,
        com.badlogic.gdx.Input.Buttons.LEFT);
    stage.touchUp(
        Math.round(buttonScreen.x),
        Math.round(buttonScreen.y),
        0,
        com.badlogic.gdx.Input.Buttons.LEFT);
    if (fixtureClicks != clicksBefore)
      throw new AssertionError("Disabled declarative button callback ran");
    redraw();
    captureScreenshot("artifacts/ui-parity-native-disabled.png", "disabled");
    input.setDisabled(true);
    var expectedDisabledText = UtilityStyles.color("red-500");
    expectedDisabledText.a *= .5f;
    if (!input.getStyle().disabledFontColor.equals(expectedDisabledText))
      throw new AssertionError("TextField disabled:text utility did not reach disabledFontColor");
    redraw();
    captureScreenshot("artifacts/ui-parity-native-input-disabled.png", "input-disabled");
    Gdx.files
        .local("artifacts/ui-parity-state-comparison.json")
        .writeString(stateComparisonsJson(), false);
  }

  private void redraw() {
    ScreenUtils.clear(1, 1, 1, 1);
    stage.draw();
  }

  private void captureScreenshot(String path, String state) {
    Pixmap pixels =
        ScreenUtils.getFrameBufferPixmap(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      writeScreenshot(path, pixels);
      compareState(pixels, state);
    } finally {
      pixels.dispose();
    }
  }

  private void compareState(Pixmap pixels, String state) {
    JsonValue reference =
        new JsonReader()
            .parse(Gdx.files.classpath("org/javity/demo/ui-reference/reference-metrics.json"))
            .get("states");
    String pixelName = state + "Pixel";
    String pointName =
        switch (state) {
          case "hover" -> "buttonHoverPoint";
          case "input-hover" -> "inputHoverPoint";
          case "focus" -> "inputFocusPoint";
          case "input-disabled" -> "inputDisabledPoint";
          default -> "buttonDisabledPoint";
        };
    JsonValue point = reference.get(pointName);
    statePixelErrors.put(
        state,
        assertPixel(pixels, point.getInt(0), point.getInt(1), reference.get(pixelName), state));
  }

  private int assertPixel(Pixmap pixels, int x, int y, JsonValue expected, String label) {
    int rgba = pixels.getPixel(x, pixels.getHeight() - 1 - y);
    int maxError = 0;
    for (int channel = 0; channel < 3; channel++)
      maxError =
          Math.max(
              maxError, Math.abs(((rgba >>> (24 - channel * 8)) & 255) - expected.getInt(channel)));
    if (maxError > 3)
      throw new AssertionError(
          label
              + " pixel differs from browser by "
              + maxError
              + " at "
              + x
              + ","
              + y
              + "; native="
              + pixel(rgba)
              + " browser="
              + expected);
    return maxError;
  }

  private String stateComparisonsJson() {
    StringBuilder json = new StringBuilder("{\n");
    int index = 0;
    for (var result : statePixelErrors.entrySet()) {
      if (index++ > 0) json.append(",\n");
      json.append("  \"").append(result.getKey()).append("\": ").append(result.getValue());
    }
    return json.append("\n}\n").toString();
  }

  private static String pixel(int rgba) {
    return "["
        + ((rgba >>> 24) & 255)
        + ","
        + ((rgba >>> 16) & 255)
        + ","
        + ((rgba >>> 8) & 255)
        + "]";
  }

  private static String glyphBounds(Pixmap pixels, float x, float y, float width, float height) {
    int minX = pixels.getWidth(), minY = pixels.getHeight(), maxX = -1, maxY = -1;
    for (int px = Math.max(0, (int) Math.floor(x));
        px < Math.min(pixels.getWidth(), (int) Math.ceil(x + width));
        px++)
      for (int py = Math.max(0, (int) Math.floor(y));
          py < Math.min(pixels.getHeight(), (int) Math.ceil(y + height));
          py++) {
        int rgba = pixels.getPixel(px, pixels.getHeight() - 1 - py);
        int red = (rgba >>> 24) & 255, green = (rgba >>> 16) & 255, blue = (rgba >>> 8) & 255;
        if (Math.max(red, Math.max(green, blue)) < 180) {
          minX = Math.min(minX, px);
          minY = Math.min(minY, py);
          maxX = Math.max(maxX, px);
          maxY = Math.max(maxY, py);
        }
      }
    return maxX < 0
        ? "null"
        : "{\"x\":"
            + minX
            + ",\"y\":"
            + minY
            + ",\"width\":"
            + (maxX - minX + 1)
            + ",\"height\":"
            + (maxY - minY + 1)
            + "}";
  }

  private static String wrapInkBounds(Pixmap pixels, float x, float y, float width, float height) {
    int minX = pixels.getWidth(), minY = pixels.getHeight(), maxX = -1, maxY = -1;
    for (int px = Math.max(0, (int) Math.floor(x));
        px < Math.min(pixels.getWidth(), (int) Math.ceil(x + width));
        px++)
      for (int py = Math.max(0, (int) Math.floor(y));
          py < Math.min(pixels.getHeight(), (int) Math.ceil(y + height));
          py++) {
        int rgba = pixels.getPixel(px, pixels.getHeight() - 1 - py);
        int red = (rgba >>> 24) & 255, green = (rgba >>> 16) & 255, blue = (rgba >>> 8) & 255;
        if (Math.max(red, Math.max(green, blue)) < 150) {
          minX = Math.min(minX, px);
          minY = Math.min(minY, py);
          maxX = Math.max(maxX, px);
          maxY = Math.max(maxY, py);
        }
      }
    return maxX < 0
        ? "null"
        : "{\"x\":"
            + minX
            + ",\"y\":"
            + minY
            + ",\"width\":"
            + (maxX - minX + 1)
            + ",\"height\":"
            + (maxY - minY + 1)
            + "}";
  }

  private static String colorBounds(Pixmap pixels, float x, float y, float width, float height) {
    int minX = pixels.getWidth(), minY = pixels.getHeight(), maxX = -1, maxY = -1;
    for (int px = Math.max(0, (int) Math.floor(x));
        px < Math.min(pixels.getWidth(), (int) Math.ceil(x + width));
        px++)
      for (int py = Math.max(0, (int) Math.floor(y));
          py < Math.min(pixels.getHeight(), (int) Math.ceil(y + height));
          py++) {
        int rgba = pixels.getPixel(px, pixels.getHeight() - 1 - py);
        int r = (rgba >>> 24) & 255, g = (rgba >>> 16) & 255, b = (rgba >>> 8) & 255;
        if (r == 144 && g == 161 && b == 185) {
          minX = Math.min(minX, px);
          minY = Math.min(minY, py);
          maxX = Math.max(maxX, px);
          maxY = Math.max(maxY, py);
        }
      }
    return maxX < 0
        ? "null"
        : "{\"x\":"
            + minX
            + ",\"y\":"
            + minY
            + ",\"width\":"
            + (maxX - minX + 1)
            + ",\"height\":"
            + (maxY - minY + 1)
            + "}";
  }

  private static void writeScreenshot(String path, Pixmap pixels) {
    Pixmap flipped = flipY(pixels);
    try {
      PixmapIOCompat.write(path, flipped);
    } finally {
      flipped.dispose();
    }
  }

  private static Pixmap flipY(Pixmap source) {
    Pixmap flipped = new Pixmap(source.getWidth(), source.getHeight(), source.getFormat());
    for (int y = 0; y < source.getHeight(); y++)
      for (int x = 0; x < source.getWidth(); x++)
        flipped.drawPixel(x, source.getHeight() - 1 - y, source.getPixel(x, y));
    return flipped;
  }

  private static float round(float n) {
    return Math.round(n * 100f) / 100f;
  }

  private static String jsonStrings(List<String> values) {
    StringBuilder json = new StringBuilder("[");
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) json.append(',');
      json.append('"');
      for (int character = 0; character < values.get(index).length(); character++) {
        char value = values.get(index).charAt(character);
        if (value == '"' || value == '\\') json.append('\\');
        if (value == '\n') json.append("\\n");
        else if (value == '\r') json.append("\\r");
        else json.append(value);
      }
      json.append('"');
    }
    return json.append(']').toString();
  }

  private static String pixelCenter(Pixmap pixels, float x, float yFromTop) {
    int rgba =
        pixels.getPixel(
            Math.max(0, Math.min(pixels.getWidth() - 1, Math.round(x))),
            Math.max(
                0,
                Math.min(pixels.getHeight() - 1, pixels.getHeight() - 1 - Math.round(yFromTop))));
    return "["
        + ((rgba >>> 24) & 255)
        + ","
        + ((rgba >>> 16) & 255)
        + ","
        + ((rgba >>> 8) & 255)
        + "]";
  }

  private void compareToBrowser(Pixmap pixels) {
    var file = Gdx.files.classpath("org/javity/demo/ui-reference/reference-metrics.json");
    if (!file.exists())
      throw new IllegalStateException(
          "Browser reference missing; run npm run compare in web/javity-ui-reference first");
    JsonValue browser = new JsonReader().parse(file);
    JsonValue elements = browser.get("els");
    float maxGlyphVerticalError = 0, maxGlyphWidthError = 0;
    for (String key : BOX_KEYS) {
      JsonValue expectedInk = elements.get(key).get("glyphPixels");
      if (expectedInk == null || expectedInk.isNull()) continue;
      var actor = document.actor(key);
      var origin = actor.localToStageCoordinates(new com.badlogic.gdx.math.Vector2());
      JsonValue actualInk =
          new JsonReader()
              .parse(
                  glyphBounds(
                      pixels,
                      origin.x,
                      Gdx.graphics.getHeight() - origin.y - actor.getHeight(),
                      actor.getWidth(),
                      actor.getHeight()));
      if (actualInk.isNull()) throw new AssertionError("Missing rendered glyphs: " + key);
      for (String axis : List.of("y", "height"))
        maxGlyphVerticalError =
            Math.max(
                maxGlyphVerticalError,
                Math.abs(actualInk.getFloat(axis) - expectedInk.getFloat(axis)));
      maxGlyphWidthError =
          Math.max(
              maxGlyphWidthError,
              Math.abs(actualInk.getFloat("width") - expectedInk.getFloat("width")));
      if (maxGlyphVerticalError > 1 || maxGlyphWidthError > 4)
        throw new AssertionError(
            "Glyph ink bounds exceed measured rasterizer residual at "
                + key
                + ": native="
                + actualInk
                + ", browser="
                + expectedInk);
    }
    JsonValue baseBorderPoint = browser.get("states").get("inputBorderPoint");
    int inputBorderError =
        assertPixel(
            pixels,
            baseBorderPoint.getInt(0),
            baseBorderPoint.getInt(1),
            browser.get("states").get("inputBorderPixel"),
            "input-base-border");
    JsonValue expectedThumb = browser.get("scrollbar").get("thumbPixelBounds");
    var scroll = document.actor("scroll-sample");
    var scrollPos = scroll.localToStageCoordinates(new com.badlogic.gdx.math.Vector2());
    String thumbJson =
        colorBounds(
            pixels,
            scrollPos.x,
            Gdx.graphics.getHeight() - scrollPos.y - scroll.getHeight(),
            scroll.getWidth(),
            scroll.getHeight());
    JsonValue actualThumb = new JsonReader().parse(thumbJson);
    float thumbDelta = 0;
    String[] thumbFields = {"x", "y", "width", "height"};
    for (String name : thumbFields)
      thumbDelta =
          Math.max(thumbDelta, Math.abs(actualThumb.getFloat(name) - expectedThumb.getFloat(name)));
    if (thumbDelta > 2.0f)
      throw new AssertionError(
          "Visible scrollbar thumb differs by "
              + thumbDelta
              + "px; native="
              + thumbJson
              + " browser="
              + expectedThumb);
    float maxGeometry = 0;
    String maxKey = "";
    for (String key : BOX_KEYS) {
      JsonValue expected = elements.get(key).get("rect");
      var actor = document.actor(key);
      var position = actor.localToStageCoordinates(new com.badlogic.gdx.math.Vector2());
      float[] nativeBox = {
        position.x,
        Gdx.graphics.getHeight() - position.y - actor.getHeight(),
        actor.getWidth(),
        actor.getHeight()
      };
      String[] names = {"x", "y", "width", "height"};
      for (int i = 0; i < names.length; i++) {
        float delta = Math.abs(nativeBox[i] - expected.getFloat(names[i]));
        if (delta > maxGeometry) {
          maxGeometry = delta;
          maxKey = key + "." + names[i];
        }
      }
    }
    int maxColor = 0;
    String colorKey = "";
    for (String key :
        List.of("nested", "flex-left", "flex-right", "classic-progress", "classic-card")) {
      JsonValue expected = elements.get(key).get("pixelCenter");
      var actor = document.actor(key);
      var position = actor.localToStageCoordinates(new com.badlogic.gdx.math.Vector2());
      int rgba =
          pixels.getPixel(
              Math.round(position.x + actor.getWidth() / 2),
              pixels.getHeight()
                  - 1
                  - Math.round(Gdx.graphics.getHeight() - position.y - actor.getHeight() / 2));
      for (int channel = 0; channel < 3; channel++) {
        int actual = (rgba >>> (24 - channel * 8)) & 255;
        int delta = Math.abs(actual - expected.getInt(channel));
        if (delta > maxColor) {
          maxColor = delta;
          colorKey = key + ".rgb" + channel;
        }
      }
    }
    String diagnostics =
        "{\"maxGeometryErrorPx\":"
            + round(maxGeometry)
            + ",\"maxGlyphVerticalErrorPx\":"
            + round(maxGlyphVerticalError)
            + ",\"maxGlyphWidthErrorPx\":"
            + round(maxGlyphWidthError)
            + ",\"geometryAt\":\""
            + maxKey
            + "\",\"maxColorChannelError\":"
            + maxColor
            + ",\"colorAt\":\""
            + colorKey
            + "\",\"scrollbarThumbPixelBoundsDelta\":"
            + round(thumbDelta)
            + ",\"inputBaseBorderPixelError\":"
            + inputBorderError
            + "}\n";
    Gdx.files.local("artifacts/ui-parity-comparison.json").writeString(diagnostics, false);
    if (maxGeometry > 2.5f || maxColor > 2) {
      writeScreenshot("artifacts/ui-parity-failure.png", pixels);
      throw new AssertionError("UI browser parity exceeded measured tolerance: " + diagnostics);
    }
  }

  @Override
  public void resize(int width, int height) {
    if (stage != null) stage.getViewport().update(width, height, true);
  }

  @Override
  public void dispose() {
    if (stage != null) stage.dispose();
    if (document != null) document.close();
    if (theme != null) theme.dispose();
  }

  /** Keep the app's capture independent of backend-specific ScreenshotFactory versions. */
  private static final class PixmapIOCompat {
    static void write(String path, Pixmap pixels) {
      com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.local(path), pixels);
    }
  }
}
