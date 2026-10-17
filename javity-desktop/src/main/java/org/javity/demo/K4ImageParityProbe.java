package org.javity.demo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.javity.script.ScriptUiRuntime;
import org.javity.ui.declarative.UiDocument;
import org.javity.ui.declarative.UiNode;
import org.javity.ui.flex.ImageFit;
import org.javity.ui.flex.UiImage;
import org.javity.ui.flex.UiTheme;

/** Focused real-GL and Graal proof for the bounded K4 registered-image surface. */
final class K4ImageParityProbe {
  private static final int WIDTH = 1100;
  private static final int HEIGHT = 720;

  private K4ImageParityProbe() {}

  static void capture() {
    JsonValue fixture =
        new JsonReader()
            .parse(Gdx.files.classpath("org/javity/demo/ui-reference/k4-image-fixture.json"));
    Texture texture =
        new Texture(Gdx.files.classpath("org/javity/demo/ui-reference/k4-image-fixture.png"));
    int fixtureTextureHandle = texture.getTextureObjectHandle();
    TextureRegion full = new TextureRegion(texture);
    TextureRegion atlas = new TextureRegion(texture, 32, 24, 128, 80);
    TextureRegion flipped = new TextureRegion(atlas);
    float atlasU = atlas.getU(),
        atlasU2 = atlas.getU2(),
        atlasV = atlas.getV(),
        atlasV2 = atlas.getV2();
    flipped.flip(true, false);
    float flippedU = flipped.getU(),
        flippedU2 = flipped.getU2(),
        flippedV = flipped.getV(),
        flippedV2 = flipped.getV2();
    TextureRegion vertical = new TextureRegion(atlas);
    vertical.flip(false, true);

    UiTheme theme = new UiTheme();
    Stage stage = new Stage(new ScreenViewport());
    UiDocument document = new UiDocument(theme);
    ScriptUiRuntime runtime = null;
    UiDocument graalDocument = null;
    try {
      theme.registerImage("k4-pattern", full);
      theme.registerImage("k4-atlas", atlas);
      theme.registerImage("k4-atlas-flipped", flipped);
      theme.registerImage("k4-atlas-vertical", vertical);
      expectIllegal(() -> theme.registerImage(" ", full), "blank image names must be rejected");
      expectIllegal(
          () -> theme.registerImage("empty", new TextureRegion(texture, 0, 0, 0, 1)),
          "empty regions must be rejected");

      stage.getViewport().update(WIDTH, HEIGHT, true);
      document.render(read(fixture.get("tree"), null));
      document.root().setFillParent(true);
      stage.addActor(document.root());
      applyAlpha(fixture.get("tree"), document);
      draw(stage, "artifacts/k4-image-native.png");
      Map<String, Object> initial = geometry(fixture.get("tree"), document, stage);
      Gdx.files
          .local("artifacts/k4-image-native-metrics.json")
          .writeString(
              json(
                  Map.of("viewport", Map.of("width", WIDTH, "height", HEIGHT), "initial", initial)),
              false);
      assertRoundedAndLetterboxPixels(document);

      JsonValue resize = fixture.get("resize");
      Actor beforeResize = document.actor(resize.getString("key"));
      document.render(read(fixture.get("tree"), resize));
      if (document.actor(resize.getString("key")) != beforeResize)
        throw new AssertionError("A keyed image fit/source/class update replaced its actor");
      applyAlpha(fixture.get("tree"), document);
      draw(stage, "artifacts/k4-image-native-resized.png");
      Map<String, Object> resized = geometry(fixture.get("tree"), document, stage);

      assertAtlasSampling(theme, texture, atlas, flipped);
      assertGraalSchemaAndKeyedUpdates(theme, full, atlas, flipped);
      assertBatchStateRestored(theme, atlas);
      if (Math.abs(atlas.getU() - atlasU) > 1e-6f
          || Math.abs(atlas.getU2() - atlasU2) > 1e-6f
          || Math.abs(atlas.getV() - atlasV) > 1e-6f
          || Math.abs(atlas.getV2() - atlasV2) > 1e-6f
          || Math.abs(flipped.getU() - flippedU) > 1e-6f
          || Math.abs(flipped.getU2() - flippedU2) > 1e-6f
          || Math.abs(flipped.getV() - flippedV) > 1e-6f
          || Math.abs(flipped.getV2() - flippedV2) > 1e-6f)
        throw new AssertionError("Rendering mutated the caller's atlas TextureRegion or flips");

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("viewport", Map.of("width", WIDTH, "height", HEIGHT));
      result.put("initial", initial);
      result.put("resized", resized);
      Gdx.files.local("artifacts/k4-image-native-metrics.json").writeString(json(result), false);
    } finally {
      if (runtime != null) runtime.close();
      if (graalDocument != null) graalDocument.close();
      document.close();
      stage.dispose();
      theme.dispose();
      if (texture.getTextureObjectHandle() != fixtureTextureHandle)
        throw new AssertionError("UiTheme disposed a borrowed image texture");
      texture.dispose();
    }
  }

  private static void assertGraalSchemaAndKeyedUpdates(
      UiTheme theme, TextureRegion full, TextureRegion atlas, TextureRegion flipped) {
    theme.registerImage("k4-pattern", full);
    theme.registerImage("k4-atlas", atlas);
    theme.registerImage("k4-atlas-flipped", flipped);
    UiDocument document = new UiDocument(theme);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.eval(
          "globalThis.k4Render=(source,fit,classes)=>{const"
              + " R=JavityReact.React;JavityReact.render(R.createElement('div',{id:'graal-image-root',className:'flex"
              + " flex-col'},R.createElement('img',{id:'graal-image',source,fit,className:classes})))"
              + " };");
      runtime.eval("k4Render('k4-pattern','contain','w-[96px] h-[64px]')");
      Actor actor = document.actor("graal-image");
      if (!(actor instanceof UiImage image)
          || !image.source().equals("k4-pattern")
          || image.fit() != ImageFit.CONTAIN)
        throw new AssertionError("Graal img did not produce a registered native Image");

      if (!theme.unregisterImage("k4-pattern")) throw new AssertionError("Image unregister failed");
      runtime.eval("k4Render('k4-pattern','cover','w-[120px] h-[72px]')");
      if (document.actor("graal-image") != actor || image.fit() != ImageFit.COVER)
        throw new AssertionError(
            "A mounted image did not retain its borrowed source on unregister");

      theme.registerImage("k4-pattern", full);
      runtime.eval("k4Render('k4-atlas','cover','w-[128px] h-[72px] rounded-lg')");
      if (document.actor("graal-image") != actor
          || !image.source().equals("k4-atlas")
          || image.fit() != ImageFit.COVER)
        throw new AssertionError(
            "A keyed registered source update failed: actorSame="
                + (document.actor("graal-image") == actor)
                + ", source="
                + image.source()
                + ", fit="
                + image.fit());
      runtime.eval("k4Render('k4-atlas-flipped','stretch','w-[128px] h-[72px]')");
      if (document.actor("graal-image") != actor
          || !image.source().equals("k4-atlas-flipped")
          || image.fit() != ImageFit.STRETCH)
        throw new AssertionError("A keyed atlas source/fit update did not reach the actor");

      if (!theme.unregisterImage("k4-atlas-flipped"))
        throw new AssertionError("Atlas image unregister failed");
      runtime.eval("k4Render('k4-atlas-flipped','contain','w-[128px] h-[72px]')");
      if (document.actor("graal-image") != actor || image.fit() != ImageFit.CONTAIN)
        throw new AssertionError("A mounted atlas image did not retain its borrowed source");

      expectScriptFailure(runtime, "k4Render('missing-source','stretch','w-[72px] h-[72px]')");
      if (document.actor("graal-image") != actor
          || !image.source().equals("k4-atlas-flipped")
          || image.fit() != ImageFit.CONTAIN)
        throw new AssertionError("Unknown image source mutated the mounted UI tree");

      try {
        document.render(
            UiNode.element(
                "View",
                "graal-image-root",
                "flex flex-col",
                Map.of(),
                List.of(
                    new UiNode(
                        "Image",
                        "graal-image",
                        "w-[72px] h-[72px]",
                        Map.of("source", "k4-atlas-flipped", "fit", "round"),
                        List.of()))));
        throw new AssertionError("Unknown native image fit was accepted");
      } catch (IllegalArgumentException expected) {
        // Native image fit validation also runs before keyed actor updates.
      }
      if (document.actor("graal-image") != actor || image.fit() != ImageFit.CONTAIN)
        throw new AssertionError("Unknown image fit mutated the mounted UI tree");
    } finally {
      runtime.close();
      document.close();
    }
  }

  private static void expectScriptFailure(ScriptUiRuntime runtime, String script) {
    try {
      runtime.eval(script);
      throw new AssertionError("Expected image preflight rejection: " + script);
    } catch (RuntimeException expected) {
      // The React host, Graal bridge, and document validation report strict surface failures here.
    }
  }

  private static void assertAtlasSampling(
      UiTheme theme, Texture texture, TextureRegion atlas, TextureRegion flipped) {
    TextureRegion vertical = new TextureRegion(atlas);
    vertical.flip(false, true);
    theme.registerImage("k4-atlas-vertical", vertical);
    Stage stage = new Stage(new ScreenViewport());
    stage.getViewport().update(WIDTH, HEIGHT, true);
    UiImage left = new UiImage(theme, "k4-atlas", atlas, ImageFit.STRETCH, "");
    UiImage right = new UiImage(theme, "k4-atlas-flipped", flipped, ImageFit.STRETCH, "");
    UiImage upDown = new UiImage(theme, "k4-atlas-vertical", vertical, ImageFit.STRETCH, "");
    left.setBounds(120, 500, 128, 80);
    right.setBounds(280, 500, 128, 80);
    upDown.setBounds(440, 500, 128, 80);
    stage.addActor(left);
    stage.addActor(right);
    stage.addActor(upDown);
    try {
      draw(stage, "artifacts/k4-image-atlas-native.png");
      Pixmap pixels =
          ScreenUtils.getFrameBufferPixmap(
              0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      try {
        int y = Math.round(500 + 70);
        Color normal = colorAt(pixels, 120 + 32, y);
        Color reverse = colorAt(pixels, 280 + 32, y);
        Color verticalReverse = colorAt(pixels, 440 + 32, y);
        if (normal.r < .72f
            || normal.g > .42f
            || reverse.g < .55f
            || reverse.r > .42f
            || verticalReverse.b < .72f
            || verticalReverse.r > .42f)
          throw new AssertionError(
              "Atlas crop/flip pixels were wrong: normal="
                  + normal
                  + ", flipped="
                  + reverse
                  + ", vertical="
                  + verticalReverse);
      } finally {
        pixels.dispose();
      }
    } finally {
      stage.dispose();
    }
  }

  private static void assertRoundedAndLetterboxPixels(UiDocument document) {
    Pixmap pixels =
        ScreenUtils.getFrameBufferPixmap(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      Color roundedCorner = pixelAtActor(document.actor("cover-wide"), pixels, 1, 1);
      Color paddedRoundedCorner = pixelAtActor(document.actor("contain-padded"), pixels, 1, 1);
      Color avatarCorner = pixelAtActor(document.actor("alpha-avatar"), pixels, 1, 1);
      Color transparentPixel = pixelAtActor(document.actor("stretch-tall"), pixels, 3, 3);
      Color letterbox = pixelAtActor(document.actor("contain-padded"), pixels, 50, 8);
      Color border = pixelAtActor(document.actor("contain-padded"), pixels, 0, 40);
      Color emptyFill = pixelAtActor(document.actor("empty-content"), pixels, 16, 16);
      Color emptyBorder = pixelAtActor(document.actor("empty-content"), pixels, 0, 16);
      if (roundedCorner.r < .92f || roundedCorner.g < .92f || roundedCorner.b < .92f)
        throw new AssertionError("Rounded cover corner retained texture pixels: " + roundedCorner);
      if (paddedRoundedCorner.r < .92f
          || paddedRoundedCorner.g < .92f
          || paddedRoundedCorner.b < .92f)
        throw new AssertionError(
            "Rounded padded image corner was not clipped: " + paddedRoundedCorner);
      if (avatarCorner.r < .92f || avatarCorner.g < .92f || avatarCorner.b < .92f)
        throw new AssertionError("Rounded avatar corner retained texture pixels: " + avatarCorner);
      if (transparentPixel.r < .92f || transparentPixel.g < .92f || transparentPixel.b < .92f)
        throw new AssertionError(
            "Transparent source pixels did not reveal the stage: " + transparentPixel);
      if (Math.abs(letterbox.r - 221 / 255f) > .08f
          || Math.abs(letterbox.g - 228 / 255f) > .08f
          || Math.abs(letterbox.b - 237 / 255f) > .08f)
        throw new AssertionError(
            "Contain letterbox did not retain the authored background: " + letterbox);
      if (border.r > .35f || border.g > .4f || border.b > .5f)
        throw new AssertionError("Padded image border was not painted once: " + border);
      if (Math.abs(emptyFill.r - 221 / 255f) > .08f
          || Math.abs(emptyFill.g - 228 / 255f) > .08f
          || Math.abs(emptyFill.b - 237 / 255f) > .08f)
        throw new AssertionError("Image background disappeared with empty content: " + emptyFill);
      if (emptyBorder.r > .35f || emptyBorder.g > .4f || emptyBorder.b > .5f)
        throw new AssertionError("Empty-content image border was not drawn: " + emptyBorder);
    } finally {
      pixels.dispose();
    }
  }

  private static void assertBatchStateRestored(UiTheme theme, TextureRegion region) {
    ShaderProgram probeShader =
        new ShaderProgram(
            """
            attribute vec4 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            uniform mat4 u_projTrans;
            varying vec4 v_color;
            varying vec2 v_uv;
            void main() { v_color=a_color; v_uv=a_texCoord0; gl_Position=u_projTrans*a_position; }
            """,
            """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            varying vec2 v_uv;
            uniform sampler2D u_texture;
            void main() { gl_FragColor=v_color*texture2D(u_texture,v_uv); }
            """);
    if (!probeShader.isCompiled()) {
      String log = probeShader.getLog();
      probeShader.dispose();
      throw new AssertionError("Batch state probe shader failed: " + log);
    }
    SpriteBatch batch = new SpriteBatch();
    UiImage image = new UiImage(theme, "k4-pattern", region, ImageFit.CONTAIN, "rounded-lg");
    image.setBounds(0, 0, 48, 48);
    try {
      batch.begin();
      batch.setColor(.2f, .3f, .4f, .5f);
      float originalColor = batch.getPackedColor();
      batch.setShader(probeShader);
      image.draw(batch, .35f);
      if (batch.getShader() != probeShader)
        throw new AssertionError("Image drawing did not restore the caller's Batch shader");
      if (batch.getPackedColor() != originalColor)
        throw new AssertionError("Image drawing did not restore the caller's Batch color");
      batch.end();
    } finally {
      if (batch.isDrawing()) batch.end();
      batch.dispose();
      probeShader.dispose();
    }
  }

  private static Color pixelAtActor(Actor actor, Pixmap pixels, int localX, int localYFromTop) {
    Vector2 position = actor.localToStageCoordinates(new Vector2());
    int x = Math.round(position.x) + localX;
    int yFromTop =
        Gdx.graphics.getHeight() - Math.round(position.y + actor.getHeight()) + localYFromTop;
    return colorAt(pixels, x, pixels.getHeight() - 1 - yFromTop);
  }

  private static Color colorAt(Pixmap pixels, int x, int yFromBottom) {
    return new Color(pixels.getPixel(x, yFromBottom));
  }

  private static void expectIllegal(Runnable operation, String message) {
    try {
      operation.run();
      throw new AssertionError(message);
    } catch (IllegalArgumentException expected) {
      // Expected registry validation.
    }
  }

  private static UiNode read(JsonValue value, JsonValue override) {
    String key = value.getString("key");
    String classes = value.getString("classes", "");
    Map<String, Object> props = new HashMap<>();
    JsonValue fields = value.get("props");
    if (fields != null)
      for (JsonValue prop = fields.child; prop != null; prop = prop.next)
        props.put(prop.name, scalar(prop));
    if (override != null && key.equals(override.getString("key"))) {
      classes = override.getString("classes");
      props.put("source", override.getString("source"));
      props.put("fit", override.getString("fit"));
    }
    List<UiNode> children = new ArrayList<>();
    JsonValue childList = value.get("children");
    if (childList != null)
      for (JsonValue child = childList.child; child != null; child = child.next)
        children.add(read(child, override));
    return new UiNode(value.getString("type"), key, classes, props, children);
  }

  private static Object scalar(JsonValue value) {
    return switch (value.type()) {
      case stringValue -> value.asString();
      case booleanValue -> value.asBoolean();
      case doubleValue -> value.asFloat();
      case longValue -> value.asLong();
      default ->
          throw new IllegalArgumentException("Unsupported K4 fixture property: " + value.name);
    };
  }

  private static void applyAlpha(JsonValue tree, UiDocument document) {
    JsonValue alpha = tree.get("alpha");
    if (alpha != null) document.actor(tree.getString("key")).setColor(1, 1, 1, alpha.asFloat());
    JsonValue children = tree.get("children");
    if (children != null)
      for (JsonValue child = children.child; child != null; child = child.next)
        applyAlpha(child, document);
  }

  private static Map<String, Object> geometry(JsonValue tree, UiDocument document, Stage stage) {
    Map<String, Object> images = new LinkedHashMap<>();
    collectGeometry(tree, document, stage, images);
    return images;
  }

  private static void collectGeometry(
      JsonValue tree, UiDocument document, Stage stage, Map<String, Object> images) {
    if (tree.getString("type").equals("Image")) {
      Actor actor = document.actor(tree.getString("key"));
      Vector2 position = actor.localToStageCoordinates(new Vector2());
      images.put(
          tree.getString("key"),
          Map.of(
              "rect",
              Map.of(
                  "x", round(position.x),
                  "y", round(stage.getHeight() - position.y - actor.getHeight()),
                  "width", round(actor.getWidth()),
                  "height", round(actor.getHeight())),
              "source",
              ((UiImage) actor).source(),
              "fit",
              ((UiImage) actor).fit().value()));
    }
    JsonValue children = tree.get("children");
    if (children != null)
      for (JsonValue child = children.child; child != null; child = child.next)
        collectGeometry(child, document, stage, images);
  }

  private static float round(float value) {
    return Math.round(value * 100f) / 100f;
  }

  private static String json(Object value) {
    if (value instanceof Map<?, ?> map) {
      StringBuilder output = new StringBuilder("{");
      boolean first = true;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!first) output.append(',');
        first = false;
        output.append(json(entry.getKey().toString())).append(':').append(json(entry.getValue()));
      }
      return output.append('}').toString();
    }
    if (value instanceof String string) {
      StringBuilder output = new StringBuilder("\"");
      for (int index = 0; index < string.length(); index++) {
        char character = string.charAt(index);
        if (character == '"' || character == '\\') output.append('\\');
        if (character == '\n') output.append("\\n");
        else if (character == '\r') output.append("\\r");
        else output.append(character);
      }
      return output.append('"').toString();
    }
    if (value instanceof Number || value instanceof Boolean) return value.toString();
    throw new IllegalArgumentException("Unsupported K4 metrics value: " + value);
  }

  private static void draw(Stage stage, String path) {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    ScreenUtils.clear(Color.WHITE);
    stage.act(0);
    stage.draw();
    Pixmap pixels =
        ScreenUtils.getFrameBufferPixmap(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      Pixmap flipped = new Pixmap(pixels.getWidth(), pixels.getHeight(), pixels.getFormat());
      try {
        for (int row = 0; row < pixels.getHeight(); row++)
          for (int column = 0; column < pixels.getWidth(); column++)
            flipped.drawPixel(column, pixels.getHeight() - 1 - row, pixels.getPixel(column, row));
        com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.local(path), flipped);
      } finally {
        flipped.dispose();
      }
    } finally {
      pixels.dispose();
    }
  }
}
