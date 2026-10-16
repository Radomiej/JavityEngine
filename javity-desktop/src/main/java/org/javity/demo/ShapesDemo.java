package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.ArrayList;
import java.util.List;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.render.Camera2D;
import org.javity.render.DashPattern2D;
import org.javity.render.GdxRenderer;
import org.javity.render.ShapeMaterial2D;
import org.javity.render.ShapeRenderer2D;
import org.javity.render.SpriteRenderer2D;
import org.javity.render.TextRenderer2D;
import org.javity.ui.flex.UiTheme;

/** Focused native OpenGL gallery and pixel smoke for shader-smoothed 2D shapes. */
public final class ShapesDemo extends ApplicationAdapter {
  private final boolean smoke;
  private Game game;
  private GdxRenderer renderer;
  private Camera2D camera;
  private GameObject cameraObject;
  private Stage stage;
  private UiTheme theme;
  private Label uiSentinel;
  private Texture spriteTexture;
  private ShapeRenderer2D smoothCircle,
      aliasedCircle,
      screenMarker,
      softDashCut,
      hardDashCut,
      worldDashZoom;
  private GameObject smoothCircleObject, aliasedCircleObject, worldMarkerObject;
  private final List<GameObject> topAnchoredObjects = new ArrayList<>();
  private final List<String> dashProbeSamples = new ArrayList<>();
  private final List<String> dashSpaceSamples = new ArrayList<>();
  private int width, height, frames;

  public ShapesDemo(boolean smoke) {
    this.smoke = smoke;
  }

  @Override
  public void create() {
    renderer = new GdxRenderer();
    game = new Game();
    width = Math.max(1, Gdx.graphics.getWidth());
    height = Math.max(1, Gdx.graphics.getHeight());
    cameraObject = game.createObject("gallery camera").setPosition(width / 2f, height / 2f);
    camera = cameraObject.addComponent(new Camera2D());
    camera.resize(width, height);

    addCircle("filled circle", 85, 112, 30, Color.valueOf("f4f4f5"));
    addCircle("stroked circle", 175, 112, 30, Color.valueOf("67e8f9"), 5);
    smoothCircleObject = game.createObject("smoothed coverage sample");
    topAnchoredObjects.add(smoothCircleObject);
    smoothCircleObject.setPosition(265, height - 112);
    smoothCircle = smoothCircleObject.addComponent(ShapeRenderer2D.circle(30));
    smoothCircle.color.set(Color.WHITE);
    smoothCircleObject.transform.scale.set(1.2f, .75f);
    smoothCircleObject.transform.rotation = .27f;
    aliasedCircleObject = game.createObject("aliased coverage control");
    topAnchoredObjects.add(aliasedCircleObject);
    aliasedCircleObject.setPosition(355, height - 112);
    aliasedCircle = aliasedCircleObject.addComponent(ShapeRenderer2D.circle(30));
    aliasedCircle.antiAlias = false;
    aliasedCircle.color.set(Color.WHITE);
    aliasedCircleObject.transform.scale.set(1.2f, .75f);
    aliasedCircleObject.transform.rotation = .27f;

    addRoundedRectangle("rounded fill", 470, 112, 104, 68, 20, Color.valueOf("a78bfa"));
    GameObject cornerObject = game.createObject("rounded corner pixel control");
    topAnchoredObjects.add(cornerObject);
    cornerObject.setPosition(610, height - 112);
    ShapeRenderer2D corner = cornerObject.addComponent(ShapeRenderer2D.rectangle(104, 68));
    corner.cornerRadius = 20;
    corner.color.set(Color.valueOf("fbbf24"));

    addPolyline(
        "diagonal",
        730,
        height - 112,
        List.of(new Vector2(-38, -26), new Vector2(38, 26)),
        7,
        Color.valueOf("fb7185"),
        1f,
        ShapeRenderer2D.LineCap.ROUND);
    addPolyline(
        "translucent L",
        805,
        height - 112,
        List.of(new Vector2(0, 0), new Vector2(55, 0), new Vector2(55, 55)),
        16,
        new Color(.12f, .95f, .58f, .5f),
        .5f,
        ShapeRenderer2D.LineCap.ROUND);

    addPolyline(
        "butt cap",
        190,
        height - 245,
        List.of(new Vector2(0, 0), new Vector2(70, 0)),
        16,
        Color.valueOf("38bdf8"),
        1f,
        ShapeRenderer2D.LineCap.BUTT);
    addPolyline(
        "round cap",
        370,
        height - 245,
        List.of(new Vector2(0, 0), new Vector2(70, 0)),
        16,
        Color.valueOf("38bdf8"),
        1f,
        ShapeRenderer2D.LineCap.ROUND);
    addPolyline(
        "square cap",
        550,
        height - 245,
        List.of(new Vector2(0, 0), new Vector2(70, 0)),
        16,
        Color.valueOf("38bdf8"),
        1f,
        ShapeRenderer2D.LineCap.SQUARE);

    ShapeRenderer2D dashBase =
        addPolyline(
            "dashes base",
            80,
            height - 370,
            List.of(new Vector2(0, 0), new Vector2(120, 0)),
            12,
            Color.valueOf("22d3ee"),
            1f,
            ShapeRenderer2D.LineCap.BUTT);
    dashBase.setDashes(new DashPattern2D(20, 12), 0);
    ShapeRenderer2D dashShifted =
        addPolyline(
            "dashes phase plus seven",
            80,
            height - 410,
            List.of(new Vector2(0, 0), new Vector2(120, 0)),
            12,
            Color.valueOf("a78bfa"),
            1f,
            ShapeRenderer2D.LineCap.BUTT);
    dashShifted.setDashes(new DashPattern2D(20, 12), 7);
    dashShifted.material =
        ShapeMaterial2D.gradient(Color.valueOf("a78bfa"), Color.valueOf("f472b6"), 0);
    Vector2 coverageCenter = coveragePairCenter();
    worldDashZoom =
        addPolyline(
            "WORLD zoom dash probe",
            coverageCenter.x + 60,
            coverageCenter.y + 60,
            List.of(new Vector2(0, 0), new Vector2(120, 0)),
            12,
            Color.valueOf("a78bfa"),
            1f,
            ShapeRenderer2D.LineCap.BUTT);
    worldDashZoom.setDashes(new DashPattern2D(20, 12), 0);
    ShapeRenderer2D screenDash =
        addPolyline(
            "screen-space dashes",
            80,
            height - 460,
            List.of(new Vector2(0, 0), new Vector2(120, 0)),
            12,
            Color.valueOf("fbbf24"),
            1f,
            ShapeRenderer2D.LineCap.BUTT);
    screenDash.space = ShapeRenderer2D.Space.SCREEN;
    screenDash.setDashes(new DashPattern2D(20, 12), 0);
    List<Vector2> angledDashPoints = List.of(new Vector2(0, 0), new Vector2(80, 24));
    softDashCut =
        addPolyline(
            "AA angled dash cut",
            coverageCenter.x + 100,
            coverageCenter.y - 100,
            angledDashPoints,
            8,
            Color.valueOf("22d3ee"),
            1f,
            ShapeRenderer2D.LineCap.BUTT);
    softDashCut.setDashes(new DashPattern2D(20, 12), 0);
    softDashCut.getGameObject().transform.scale.set(1.25f, .75f);
    softDashCut.getGameObject().transform.rotation = .31f;
    hardDashCut =
        addPolyline(
            "hard angled dash cut",
            coverageCenter.x + 100,
            coverageCenter.y - 140,
            angledDashPoints,
            8,
            Color.valueOf("22d3ee"),
            1f,
            ShapeRenderer2D.LineCap.BUTT);
    hardDashCut.antiAlias = false;
    hardDashCut.setDashes(new DashPattern2D(20, 12), 0);
    hardDashCut.getGameObject().transform.scale.set(1.25f, .75f);
    hardDashCut.getGameObject().transform.rotation = .31f;
    ShapeRenderer2D dashBend =
        addPolyline(
            "continuous dash corner",
            330,
            height - 450,
            List.of(new Vector2(0, 0), new Vector2(13, 0), new Vector2(13, 30)),
            12,
            Color.valueOf("34d399"),
            1f,
            ShapeRenderer2D.LineCap.BUTT);
    dashBend.setDashes(new DashPattern2D(10, 10), 0);
    ShapeRenderer2D cornerInk =
        addPolyline(
            "continuous ink corner",
            610,
            height - 450,
            List.of(new Vector2(0, 0), new Vector2(5, 0), new Vector2(5, 30)),
            12,
            new Color(.12f, .95f, .58f, .5f),
            .5f,
            ShapeRenderer2D.LineCap.BUTT);
    cornerInk.setDashes(new DashPattern2D(15, 10), 0);
    ShapeRenderer2D dashCrossing =
        addPolyline(
            "dash crossing union",
            780,
            height - 450,
            List.of(
                new Vector2(-20, -20),
                new Vector2(20, 20),
                new Vector2(-20, 20),
                new Vector2(20, -20)),
            10,
            Color.valueOf("fb7185"),
            1f,
            ShapeRenderer2D.LineCap.BUTT);
    dashCrossing.setDashes(new DashPattern2D(20, 20), 0);
    for (int i = 0; i < 3; i++) {
      ShapeRenderer2D capLine =
          addPolyline(
              "dash cap " + i,
              80 + 230 * i,
              height - 530,
              List.of(new Vector2(0, 0), new Vector2(60, 0)),
              16,
              Color.valueOf("38bdf8"),
              1f,
              ShapeRenderer2D.LineCap.values()[i]);
      capLine.setDashes(new DashPattern2D(20, 20), 0);
    }
    ShapeRenderer2D boundaryDot =
        addPolyline(
            "phase just below ON/OFF boundary dot",
            700,
            height - 500,
            List.of(new Vector2(), new Vector2()),
            12,
            Color.valueOf("a78bfa"),
            1f,
            ShapeRenderer2D.LineCap.ROUND);
    boundaryDot.setDashes(new DashPattern2D(10, 10), Math.nextDown(10d));
    ShapeRenderer2D gapDot =
        addPolyline(
            "exact OFF boundary dot",
            730,
            height - 500,
            List.of(new Vector2(), new Vector2()),
            12,
            Color.valueOf("a78bfa"),
            1f,
            ShapeRenderer2D.LineCap.ROUND);
    gapDot.setDashes(new DashPattern2D(10, 10), 10d);
    ShapeRenderer2D oppositeBoundaryGapDot =
        addPolyline(
            "phase just below OFF/ON boundary dot",
            760,
            height - 500,
            List.of(new Vector2(), new Vector2()),
            12,
            Color.valueOf("a78bfa"),
            1f,
            ShapeRenderer2D.LineCap.ROUND);
    oppositeBoundaryGapDot.setDashes(
        new DashPattern2D(10, 10, 10, 10), Math.nextDown(20d));
    ShapeRenderer2D strictBoundaryBend =
        addPolyline(
            "strict interior phase continuing bend",
            800,
            height - 560,
            List.of(new Vector2(0, 0), new Vector2(20, 0), new Vector2(20, 20)),
            4,
            Color.valueOf("a78bfa"),
            1f,
            ShapeRenderer2D.LineCap.BUTT);
    strictBoundaryBend.setDashes(
        new DashPattern2D(10, 10, 10, 10), Math.ulp(20d));
    List<Vector2> maximumPath = new ArrayList<>();
    for (int i = 0; i < 64; i++) maximumPath.add(new Vector2(i * 2, (i & 1) == 0 ? 0 : 1));
    ShapeRenderer2D maximumDash =
        addPolyline(
            "64 point maximum pattern",
            80,
            height - 610,
            maximumPath,
            5,
            Color.valueOf("f97316"),
            1f,
            ShapeRenderer2D.LineCap.ROUND);
    maximumDash.setDashes(new DashPattern2D(12, 5, 7, 3, 9, 4, 6, 4), 13);

    addPolyline(
        "world diagonal",
        790,
        height - 285,
        List.of(new Vector2(-28, -20), new Vector2(28, 20)),
        6,
        Color.valueOf("f472b6"),
        1f,
        ShapeRenderer2D.LineCap.ROUND);
    GameObject worldMarker = game.createObject("world-space marker");
    worldMarkerObject = worldMarker;
    worldMarker.setPosition(310, height - 212);
    ShapeRenderer2D world = worldMarker.addComponent(ShapeRenderer2D.rectangle(22, 22));
    world.color.set(Color.valueOf("facc15"));
    GameObject screen = game.createObject("screen-space marker");
    screen.setPosition(width - 162, height - 72);
    screenMarker = screen.addComponent(ShapeRenderer2D.rectangle(22, 22));
    screenMarker.space = ShapeRenderer2D.Space.SCREEN;
    screenMarker.color.set(Color.valueOf("22d3ee"));

    Pixmap sprite = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    sprite.setColor(Color.WHITE);
    sprite.fill();
    spriteTexture = new Texture(sprite);
    sprite.dispose();
    GameObject spriteSentinel = game.createObject("sprite sentinel");
    spriteSentinel.setPosition(width - 42, height - 42);
    SpriteRenderer2D spriteRenderer =
        spriteSentinel.addComponent(new SpriteRenderer2D(new TextureRegion(spriteTexture)));
    spriteRenderer.width = 18;
    spriteRenderer.height = 18;
    spriteRenderer.tint.set(Color.valueOf("34d399"));

    theme = new UiTheme();
    GameObject textSentinel = game.createObject("world text sentinel").setPosition(18, height - 57);
    topAnchoredObjects.add(textSentinel);
    textSentinel.addComponent(new TextRenderer2D(theme.getFont(), "World text sentinel"));
    addWorldLabel("AA fill", 48, 170);
    addWorldLabel("Stroke", 145, 170);
    addWorldLabel("AA off", 318, 170);
    addWorldLabel("Rounded", 430, 170);
    addWorldLabel("Corner", 570, 170);
    addWorldLabel("Diagonal", 695, 170);
    addWorldLabel("Alpha L", 800, 170);
    addWorldLabel("BUTT", 190, 278);
    addWorldLabel("ROUND", 370, 278);
    addWorldLabel("SQUARE", 550, 278);
    addWorldLabel("DASH [20,12]", 80, 350);
    addWorldLabel("PHASE +7", 80, 390);
    addWorldLabel("CONTINUOUS PHASE", 300, 425);
    addWorldLabel("SCREEN DASH", 80, 440);
    addWorldLabel("DASH CAPS", 80, 510);
    addWorldLabel("WORLD", 720, 322);
    addWorldLabel("SCREEN", width - 195, 322);
    stage = new Stage(new ScreenViewport());
    uiSentinel = theme.label("UI sentinel  ·  +/- zoom", "text-sm text-cyan-400");
    uiSentinel.setPosition(16, height - 26);
    stage.addActor(uiSentinel);

    game.start();
    resize(width, height);
    Gdx.gl.glClearColor(.035f, .047f, .067f, 1f);
    Gdx.input.setInputProcessor(
        new InputMultiplexer(
            stage,
            new InputAdapter() {
              @Override
              public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.PLUS || keycode == Input.Keys.EQUALS) {
                  camera.setZoom(Math.max(.25f, camera.getZoom() * .8f));
                  return true;
                }
                if (keycode == Input.Keys.MINUS) {
                  camera.setZoom(Math.min(4f, camera.getZoom() * 1.25f));
                  return true;
                }
                return false;
              }
            }));
  }

  private void addCircle(String name, float x, float top, float radius, Color color) {
    addCircle(name, x, top, radius, color, 0);
  }

  private void addCircle(String name, float x, float top, float radius, Color color, float stroke) {
    GameObject object = game.createObject(name);
    topAnchoredObjects.add(object);
    object.setPosition(x, height - top);
    ShapeRenderer2D shape = object.addComponent(ShapeRenderer2D.circle(radius));
    shape.strokeWidth = stroke;
    shape.color.set(color);
  }

  private void addRoundedRectangle(
      String name, float x, float top, float w, float h, float radius, Color color) {
    GameObject object = game.createObject(name);
    topAnchoredObjects.add(object);
    object.setPosition(x, height - top);
    ShapeRenderer2D shape = object.addComponent(ShapeRenderer2D.rectangle(w, h));
    shape.cornerRadius = radius;
    shape.color.set(color);
  }

  private ShapeRenderer2D addPolyline(
      String name,
      float x,
      float y,
      List<Vector2> points,
      float stroke,
      Color color,
      float alpha,
      ShapeRenderer2D.LineCap cap) {
    GameObject object = game.createObject(name).setPosition(x, y);
    topAnchoredObjects.add(object);
    ShapeRenderer2D line = object.addComponent(ShapeRenderer2D.polyline(points, stroke));
    line.color.set(color.r, color.g, color.b, alpha);
    line.lineCap = cap;
    return line;
  }

  private void addWorldLabel(String text, float x, float top) {
    GameObject label = game.createObject("label " + text).setPosition(x, height - top);
    topAnchoredObjects.add(label);
    label.addComponent(new TextRenderer2D(theme.getFont(), text));
  }

  @Override
  public void render() {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    if (smoke) game.step(1f / 60f);
    else game.step(Gdx.graphics.getDeltaTime());
    renderer.render(game);
    stage.act(smoke ? 1f / 60f : Gdx.graphics.getDeltaTime());
    stage.draw();
    frames++;
    if (smoke) verifySmoke();
  }

  private void verifySmoke() {
    if (frames == 1) {
      Pixmap pixels = capture();
      try {
        try {
          verifyGallery(pixels);
          verifyDashPixels(pixels);
        } catch (RuntimeException | Error failure) {
          PixmapIO.writePNG(Gdx.files.local("shapes-failure.png"), pixels, -1, true);
          throw failure;
        }
      } finally {
        pixels.dispose();
      }
    }
    if (frames == 14) {
      focusCoveragePair();
      camera.setZoom(2f);
    }
    if (frames == 15) {
      assertCoverageAtCurrentZoom("zoom 2");
      Pixmap zoomed = capture();
      try {
        verifyDashSpacePixels(zoomed, "zoom 2");
      } finally {
        zoomed.dispose();
      }
      verifySpaceMarkers();
    }
    if (frames == 29) {
      focusCoveragePair();
      camera.setZoom(.5f);
    }
    if (frames == 30) {
      assertCoverageAtCurrentZoom("zoom 0.5");
      Pixmap zoomed = capture();
      try {
        verifyDashSpacePixels(zoomed, "zoom 0.5");
      } finally {
        zoomed.dispose();
      }
      verifySpaceMarkers();
    }
    if (frames == 44) {
      camera.setZoom(1f);
      Vector2 focus = coveragePairCenter();
      cameraObject.setPosition(focus.x + 36, focus.y - 18);
    }
    if (frames == 45) {
      Pixmap moved = capture();
      try {
        verifyDashSpacePixels(moved, "camera moved");
      } finally {
        moved.dispose();
      }
      verifySpaceMarkers();
    }
    if (frames == 60) {
      cameraObject.setPosition(width / 2f, height / 2f);
      Gdx.graphics.setWindowedMode(1000, 680);
    }
    if (frames == 75) verifySpaceMarkers();
    if (frames == 119) {
      camera.setZoom(1f);
      cameraObject.setPosition(width / 2f, height / 2f);
    }
    if (frames == 120) {
      Pixmap pixels = capture();
      try {
        verifyGallery(pixels);
        verifyDashPixels(pixels);
        var artifact = Gdx.files.local("docs/artifacts/shapes-dashes");
        artifact.mkdirs();
        PixmapIO.writePNG(artifact.child("shapes-dashes.png"), pixels, -1, true);
        artifact.child("shapes-dashes-results.json").writeString(dashProbeReport(pixels), false);
        String scaleResult = verifyFramebufferScale(artifact);
        artifact.child("shapes-dashes-scale-results.json").writeString(scaleResult, false);
        PixmapIO.writePNG(Gdx.files.local("shapes-smoke.png"), pixels, -1, true);
      } finally {
        pixels.dispose();
      }
      int error = Gdx.gl.glGetError();
      if (error != GL20.GL_NO_ERROR)
        throw new IllegalStateException(
            "Shapes smoke left OpenGL error 0x" + Integer.toHexString(error));
      Gdx.app.log(
          "JavityShapesSmoke",
          "PASS: 120 frames; AA/alias coverage, alpha, caps, rounded corners, SCREEN/WORLD,"
              + " sprite/text/UI sentinels, zoom, camera movement and resize");
      Gdx.app.exit();
    }
  }

  private void verifyGallery(Pixmap pixels) {
    Vector2 aaCenter =
        camera.worldToScreen(
            smoothCircleObject.transform.position.x, smoothCircleObject.transform.position.y);
    Vector2 hardCenter =
        camera.worldToScreen(
            aliasedCircleObject.transform.position.x, aliasedCircleObject.transform.position.y);
    float scanRadius = coverageScanRadius();
    int aa = partialPixels(pixels, aaCenter.x, aaCenter.y, scanRadius);
    int hard = partialPixels(pixels, hardCenter.x, hardCenter.y, scanRadius);
    if (aa < 8 || aa <= hard)
      throw new IllegalStateException(
          "AA circle edge lacked partial-coverage pixels: aa=" + aa + ", aliased=" + hard);
    assertRadiusBoundFringe(pixels);

    Color bg = clearColor();
    Color bgProbe = sample(pixels, 20, height / 2f);
    Color corner = sample(pixels, 610 - 48, 112 - 30);
    Color fill = sample(pixels, 610, 112);
    if (!near(corner, bg, .035f) || fill.r < .8f || fill.g < .45f)
      throw new IllegalStateException(
          "Rounded rectangle corner or fill pixel check failed: corner="
              + corner
              + ", fill="
              + fill
              + ", clearExpected="
              + bg
              + ", clearProbe="
              + bgProbe);

    Color straight = sample(pixels, 830, 112);
    Color joint = sample(pixels, 860, 112);
    Color expectedHalfAlpha =
        new Color((.12f + bg.r) * .5f, (.95f + bg.g) * .5f, (.58f + bg.b) * .5f, 1f);
    if (!near(straight, expectedHalfAlpha, .04f)
        || Math.abs(straight.r - joint.r) > .035f
        || Math.abs(straight.g - joint.g) > .035f
        || Math.abs(straight.b - joint.b) > .035f)
      throw new IllegalStateException(
          "Translucent L alpha/joint pixel check failed: straight="
              + straight
              + ", joint="
              + joint
              + ", expectedHalfAlpha="
              + expectedHalfAlpha
              + ", clearExpected="
              + bg
              + ", clearProbe="
              + bgProbe);

    Color buttOutside = sample(pixels, 185, 245);
    Color roundOutside = sample(pixels, 444, 245);
    Color squareOutside = sample(pixels, 624, 245);
    if (!near(buttOutside, bg, .04f)
        || near(roundOutside, bg, .04f)
        || near(squareOutside, bg, .04f))
      throw new IllegalStateException(
          "Polyline cap pixel check failed: butt="
              + buttOutside
              + ", round="
              + roundOutside
              + ", square="
              + squareOutside
              + ", clearExpected="
              + bg
              + ", clearProbe="
              + bgProbe);

    Color sprite = sample(pixels, width - 42, 42);
    if (sprite.g < .65f || sprite.r > .4f)
      throw new IllegalStateException("Sprite pass sentinel was corrupted: " + sprite);
    if (countBrightNeutral(pixels, 14, 42, 190, 67) < 5)
      throw new IllegalStateException("World text sentinel was not rendered");
    if (countCyanGlyphs(pixels, 14, 4, 220, 27) < 5)
      throw new IllegalStateException("UI text sentinel was not rendered");
    Color screen = sample(pixels, width - 162, 72);
    if (screen.g < .55f || screen.b < .65f)
      throw new IllegalStateException("SCREEN shape sentinel was not rendered: " + screen);
  }

  private void verifyDashPixels(Pixmap pixels) {
    dashProbeSamples.clear();
    Color bg = clearColor();
    assertDashProbe(pixels, 85, 370, true, bg, "base ink +5");
    assertDashProbe(pixels, 95, 370, true, bg, "base ink +15");
    assertDashProbe(pixels, 105, 370, false, bg, "base gap +25");
    assertDashProbe(pixels, 115, 370, true, bg, "base ink +35");
    assertDashProbe(pixels, 85, 410, true, bg, "phase +7 ink +5");
    assertDashProbe(pixels, 95, 410, false, bg, "phase +7 gap +15");
    assertDashProbe(pixels, 105, 410, true, bg, "phase +7 ink +25");
    assertDashProbe(pixels, 115, 410, true, bg, "phase +7 ink +35");
    assertDashProbe(pixels, 125, 410, false, bg, "phase +7 gap +45");
    assertDashProbe(pixels, 85, 460, true, bg, "SCREEN ink +5");
    assertDashProbe(pixels, 95, 460, true, bg, "SCREEN ink +15");
    Vector2 softCutPoint = transformedDashCut(softDashCut);
    Vector2 hardCutPoint = transformedDashCut(hardDashCut);
    Vector2 softCutScreen = camera.worldToScreen(softCutPoint.x, softCutPoint.y);
    Vector2 hardCutScreen = camera.worldToScreen(hardCutPoint.x, hardCutPoint.y);
    int softCut =
        partialDashPixels(pixels, softCutScreen.x, softCutScreen.y, Color.valueOf("22d3ee"));
    int hardCut =
        partialDashPixels(pixels, hardCutScreen.x, hardCutScreen.y, Color.valueOf("22d3ee"));
    if (softCut == 0 || hardCut != 0)
      throw new IllegalStateException(
          "Dash cut AA pixels mismatch: soft=" + softCut + ", hard=" + hardCut);
    dashProbeSamples.add(
        "{\"name\":\"angled dash cut AA\",\"expected\":\"partial count > 0\",\"observed\":"
            + softCut
            + "}");
    dashProbeSamples.add(
        "{\"name\":\"angled hard dash cut\",\"expected\":\"partial count = 0\",\"observed\":"
            + hardCut
            + "}");
    assertDashProbe(pixels, 343, 448, false, bg, "cumulative bend gap +2");
    assertDashProbe(pixels, 343, 441, true, bg, "cumulative bend ink +9");
    assertDashProbe(pixels, 343, 450, false, bg, "no OFF corner disk");
    assertDashProbe(pixels, 615, 450, true, bg, "translucent continuing corner");
    Color halfAlpha =
        new Color((.12f + bg.r) * .5f, (.95f + bg.g) * .5f, (.58f + bg.b) * .5f, .75f);
    if (!near(sample(pixels, 615, 450), halfAlpha, .05f))
      throw new IllegalStateException("Dashed translucent corner applied alpha more than once");
    dashProbeSamples.add(
        "{\"name\":\"translucent corner source-over\",\"expected\":"
            + rgba(halfAlpha)
            + ",\"observed\":"
            + rgba(sample(pixels, 615, 450))
            + "}");
    assertDashProbe(pixels, 780, 450, true, bg, "ON crossing survives OFF branch");
    assertDashProbe(pixels, 105, 530, false, bg, "BUTT dash endpoint");
    assertDashProbe(pixels, 335, 530, true, bg, "ROUND dash endpoint");
    assertDashProbe(pixels, 103, 530, false, bg, "internal BUTT cap centerline");
    assertDashProbe(pixels, 333, 530, true, bg, "internal ROUND cap centerline");
    assertDashProbe(pixels, 563, 530, true, bg, "internal SQUARE cap centerline");
    assertDashProbe(pixels, 336, 523, false, bg, "internal ROUND diagonal negative");
    assertDashProbe(pixels, 566, 523, true, bg, "internal SQUARE diagonal positive");
    assertDashProbe(pixels, 85, 610, true, bg, "64-point maximum path visible ink");
    assertDashProbe(pixels, 700, 500, true, bg, "double phase just below ON boundary dot");
    assertDashProbe(pixels, 730, 500, false, bg, "exact OFF boundary dot");
    assertDashProbe(pixels, 760, 500, false, bg, "double phase just below OFF/ON boundary dot");
    Vector2 strictJoin = camera.worldToScreen(821, height - 561);
    assertDashProbe(
        pixels, strictJoin.x, strictJoin.y, true, bg, "strict interior phase continuing bend");
  }

  private String verifyFramebufferScale(com.badlogic.gdx.files.FileHandle artifact) {
    int scale = 2;
    int physicalWidth = width * scale, physicalHeight = height * scale;
    FrameBuffer framebuffer =
        new FrameBuffer(Pixmap.Format.RGBA8888, physicalWidth, physicalHeight, false);
    org.javity.render.SmoothShapeRenderer offscreen = null;
    Pixmap pixels = null;
    boolean bound = false;
    try {
      offscreen = new org.javity.render.SmoothShapeRenderer();
      ShapeRenderer2D line =
          ShapeRenderer2D.polyline(List.of(new Vector2(), new Vector2(100, 0)), 12);
      line.lineCap = ShapeRenderer2D.LineCap.BUTT;
      line.setDashes(new DashPattern2D(20, 12), 0);
      framebuffer.begin();
      bound = true;
      Gdx.gl.glViewport(0, 0, physicalWidth, physicalHeight);
      Gdx.gl.glClearColor(.035f, .047f, .067f, 1f);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
      Matrix4 logicalProjection = new Matrix4().setToOrtho2D(0, 0, width, height);
      line.color.set(Color.valueOf("22d3ee"));
      Matrix4 logicalTransform = new Matrix4().setToTranslation(100.125f, height / 2f, 0);
      offscreen.draw(line, logicalProjection, logicalTransform, physicalWidth, physicalHeight);
      pixels = Pixmap.createFromFrameBuffer(0, 0, physicalWidth, physicalHeight);
      framebuffer.end();
      bound = false;
      Color bg = clearColor();
      Color scaleInk = sample(pixels, 2 * 105, 2 * (height - height / 2f));
      Color scaleGap = sample(pixels, 2 * 125, 2 * (height - height / 2f));
      assertDashProbe(pixels, 2 * 105, 2 * (height - height / 2f), true, bg, "2x FBO ink");
      assertDashProbe(pixels, 2 * 125, 2 * (height - height / 2f), false, bg, "2x FBO gap");
      int cut =
          partialDashPixels(pixels, 2 * 120, 2 * (height - height / 2f), Color.valueOf("22d3ee"));
      if (cut == 0) throw new IllegalStateException("2x framebuffer dash-cut AA was absent");
      PixmapIO.writePNG(artifact.child("shapes-dashes-2x-framebuffer.png"), pixels, -1, true);
      return "{\"logicalViewport\":["
          + width
          + ","
          + height
          + "],\"physicalViewport\":["
          + physicalWidth
          + ","
          + physicalHeight
          + "],\"scale\":2,\"projection\":\"logical viewport into 2x RGBA8888"
          + " FBO\",\"pattern\":[20,12],\"phase\":0,\"ink\":{\"logicalPointBottomLeft\":[105,"
          + (height / 2f)
          + "],\"localPathPoint\":[4.875,0],\"expected\":\"foreground\",\"observed\":"
          + rgba(scaleInk)
          + "},\"gap\":{\"logicalPointBottomLeft\":[125,"
          + (height / 2f)
          + "],\"localPathPoint\":[24.875,0],\"expected\":\"background\",\"observed\":"
          + rgba(scaleGap)
          + "},\"dashCutPartialPixels\":"
          + cut
          + ",\"osHiDpi\":false}\n";
    } finally {
      if (pixels != null) pixels.dispose();
      if (bound) framebuffer.end();
      Gdx.gl.glViewport(
          0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      if (offscreen != null) offscreen.dispose();
      framebuffer.dispose();
    }
  }

  private Vector2 transformedDashCut(ShapeRenderer2D shape) {
    double length = Math.hypot(80, 24);
    float localX = 20f * (float) (80 / length);
    float localY = 20f * (float) (24 / length);
    var transform = shape.getGameObject().transform;
    Vector2 local = new Vector2(localX * transform.scale.x, localY * transform.scale.y);
    local.rotateRad(transform.rotation);
    return local.add(transform.position.x, transform.position.y);
  }

  private void verifyDashSpacePixels(Pixmap pixels, String stageName) {
    Color bg = clearColor();
    assertDashSpaceProbe(pixels, 85, 460, true, bg, "SCREEN ink after " + stageName);
    assertDashSpaceProbe(pixels, 105, 460, false, bg, "SCREEN gap after " + stageName);
    Vector2 worldPosition = worldDashZoom.getGameObject().transform.position;
    Vector2 worldStart = camera.worldToScreen(worldPosition.x, worldPosition.y);
    float oneUnit = camera.worldToScreen(worldPosition.x + 1, worldPosition.y).x - worldStart.x;
    assertDashSpaceProbe(
        pixels, worldStart.x + 5 * oneUnit, worldStart.y, true, bg, "WORLD ink after " + stageName);
    assertDashSpaceProbe(
        pixels,
        worldStart.x + 25 * oneUnit,
        worldStart.y,
        false,
        bg,
        "WORLD gap after " + stageName);
    Vector2 softWorld = transformedDashCut(softDashCut);
    Vector2 hardWorld = transformedDashCut(hardDashCut);
    Vector2 softScreen = camera.worldToScreen(softWorld.x, softWorld.y);
    Vector2 hardScreen = camera.worldToScreen(hardWorld.x, hardWorld.y);
    int softPartial =
        partialDashPixels(pixels, softScreen.x, softScreen.y, Color.valueOf("22d3ee"));
    int hardPartial =
        partialDashPixels(pixels, hardScreen.x, hardScreen.y, Color.valueOf("22d3ee"));
    dashSpaceSamples.add(
        "{\"name\":\"transformed cut AA after "
            + stageName
            + "\",\"expected\":[\">0 partial\",\"less than AA partial\"],\"observed\":["
            + softPartial
            + ","
            + hardPartial
            + "]}");
    if (softPartial == 0 || hardPartial >= softPartial)
      throw new IllegalStateException(
          "Transformed dash cut AA failed after "
              + stageName
              + ": soft="
              + softPartial
              + ", hard="
              + hardPartial
              + " (expected hard < soft)");
  }

  private void assertDashSpaceProbe(
      Pixmap pixels, float x, float y, boolean ink, Color background, String name) {
    Color observed = sample(pixels, x, y);
    boolean colored = observed.g > background.g + .12f || observed.b > background.b + .12f;
    dashSpaceSamples.add(
        "{\"name\":\""
            + name
            + "\",\"expected\":\""
            + (ink ? "ink" : "gap")
            + "\",\"position\":["
            + decimal(x)
            + ","
            + decimal(y)
            + "],\"observed\":"
            + rgba(observed)
            + "}");
    if (colored != ink)
      throw new IllegalStateException(
          "Dash probe " + name + " expected ink=" + ink + " but got " + observed);
  }

  private void assertDashProbe(
      Pixmap pixels, float x, float y, boolean ink, Color background, String name) {
    Color observed = sample(pixels, x, y);
    boolean colored = observed.g > background.g + .12f || observed.b > background.b + .12f;
    dashProbeSamples.add(
        "{\"name\":\""
            + name
            + "\",\"expected\":\""
            + (ink ? "ink" : "gap")
            + "\",\"observed\":"
            + rgba(observed)
            + "}");
    if (colored != ink)
      throw new IllegalStateException(
          "Dash probe " + name + " expected ink=" + ink + " but got " + observed);
  }

  private String dashProbeReport(Pixmap pixels) {
    return "{\"framebufferPhysical\":["
        + pixels.getWidth()
        + ","
        + pixels.getHeight()
        + "],\"windowLogical\":["
        + width
        + ","
        + height
        + "],\"projection\":\"Camera2D orthographic WORLD; SCREEN logical bottom-origin\","
        + "\"zoom\":1,\"probeDefinitions\":["
        + "{\"name\":\"base\",\"pattern\":[20,12],\"phase\":0},"
        + "{\"name\":\"phase shifted\",\"pattern\":[20,12],\"phase\":7},"
        + "{\"name\":\"continuous bend\",\"pattern\":[10,10],\"phase\":0},"
        + "{\"name\":\"crossing\",\"pattern\":[20,20],\"phase\":0},"
        + "{\"name\":\"translucent bend\",\"pattern\":[15,10],\"phase\":0},"
        + "{\"name\":\"caps\",\"pattern\":[20,20],\"phase\":0},"
        + "{\"name\":\"maximum path\",\"pattern\":[12,5,7,3,9,4,6,4],\"phase\":13}],"
        + "\"observedProbes\":["
        + String.join(",", dashProbeSamples)
        + "],\"worldScreenZoomProbes\":["
        + String.join(",", dashSpaceSamples)
        + "]}\n";
  }

  private static String rgba(Color color) {
    return "["
        + decimal(color.r)
        + ","
        + decimal(color.g)
        + ","
        + decimal(color.b)
        + ","
        + decimal(color.a)
        + "]";
  }

  private static String decimal(float value) {
    return String.format(java.util.Locale.ROOT, "%.4f", value);
  }

  private int partialDashPixels(Pixmap pixels, float cx, float cy, Color ink) {
    Color bg = clearColor();
    int found = 0;
    for (int y = Math.round(cy) - 3; y <= Math.round(cy) + 3; y++)
      for (int x = Math.round(cx) - 3; x <= Math.round(cx) + 3; x++) {
        Color pixel = sample(pixels, x, y);
        if (pixel.g > bg.g + .04f
            && pixel.g < ink.g - .04f
            && pixel.b > bg.b + .04f
            && pixel.b < ink.b - .04f) found++;
      }
    return found;
  }

  private void assertCoverageAtCurrentZoom(String label) {
    Pixmap pixels = capture();
    try {
      float radius = coverageScanRadius();
      Vector2 aa =
          camera.worldToScreen(
              smoothCircleObject.transform.position.x, smoothCircleObject.transform.position.y);
      Vector2 hard =
          camera.worldToScreen(
              aliasedCircleObject.transform.position.x, aliasedCircleObject.transform.position.y);
      int softCount = partialPixels(pixels, aa.x, aa.y, radius);
      int hardCount = partialPixels(pixels, hard.x, hard.y, radius);
      if (softCount < 4 || softCount <= hardCount)
        throw new IllegalStateException(
            label + " AA edge check failed: aa=" + softCount + ", aliased=" + hardCount);
    } finally {
      pixels.dispose();
    }
  }

  private float coverageScanRadius() {
    float largestScale =
        Math.max(
            Math.abs(smoothCircleObject.transform.scale.x),
            Math.abs(smoothCircleObject.transform.scale.y));
    return 30 * largestScale * camera.getPixelsPerUnit() / camera.getZoom() + 2;
  }

  private void assertRadiusBoundFringe(Pixmap pixels) {
    float screenPerLocal =
        camera.getPixelsPerUnit()
            / camera.getZoom()
            * Math.abs(smoothCircleObject.transform.scale.x);
    float localRadius = 30 - .35f / screenPerLocal;
    Vector2 edgeWorld =
        new Vector2(localRadius * smoothCircleObject.transform.scale.x, 0)
            .rotateRad(smoothCircleObject.transform.rotation)
            .add(smoothCircleObject.transform.position);
    Vector2 edge = camera.worldToScreen(edgeWorld.x, edgeWorld.y);
    int boundaryPixels = partialPixels(pixels, edge.x, edge.y, 1.5f);
    if (boundaryPixels == 0)
      throw new IllegalStateException(
          "AA radius-bound fringe was clipped or missing near screen pixel "
              + edge.x
              + ","
              + edge.y);
  }

  private Vector2 coveragePairCenter() {
    return new Vector2(
        (smoothCircleObject.transform.position.x + aliasedCircleObject.transform.position.x) / 2f,
        (smoothCircleObject.transform.position.y + aliasedCircleObject.transform.position.y) / 2f);
  }

  private void focusCoveragePair() {
    Vector2 center = coveragePairCenter();
    cameraObject.setPosition(center.x, center.y);
  }

  private void verifySpaceMarkers() {
    Pixmap pixels = capture();
    try {
      Vector2 screen = new Vector2(width - 162, 72);
      Color fixed = sample(pixels, screen.x, screen.y);
      if (fixed.g < .55f || fixed.b < .65f)
        throw new IllegalStateException(
            "SCREEN shape moved with camera at zoom=" + camera.getZoom() + ": " + fixed);
      Vector2 world =
          camera.worldToScreen(
              worldMarkerObject.transform.position.x, worldMarkerObject.transform.position.y);
      Color projected = sample(pixels, world.x, world.y);
      if (projected.r < .75f || projected.g < .55f)
        throw new IllegalStateException(
            "WORLD shape did not follow Camera2D projection: " + projected);
    } finally {
      pixels.dispose();
    }
  }

  private int partialPixels(Pixmap pixels, float centerX, float centerY, float radius) {
    int count = 0;
    int reach = (int) Math.ceil(radius + 2);
    for (int y = Math.max(0, (int) centerY - reach);
        y <= Math.min(pixels.getHeight() - 1, centerY + reach);
        y++)
      for (int x = Math.max(0, (int) centerX - reach);
          x <= Math.min(pixels.getWidth() - 1, centerX + reach);
          x++) {
        Color c = sample(pixels, x, y);
        float l = (c.r + c.g + c.b) / 3f;
        if (l > .14f && l < .88f) count++;
      }
    return count;
  }

  private int countBrightNeutral(Pixmap pixels, int x0, int y0, int x1, int y1) {
    int count = 0;
    for (int y = y0; y < y1; y++)
      for (int x = x0; x < x1; x++) {
        Color pixel = sample(pixels, x, y);
        float max = Math.max(pixel.r, Math.max(pixel.g, pixel.b));
        float min = Math.min(pixel.r, Math.min(pixel.g, pixel.b));
        if (min > .72f && max - min < .16f) count++;
      }
    return count;
  }

  private int countCyanGlyphs(Pixmap pixels, int x0, int y0, int x1, int y1) {
    int count = 0;
    for (int y = y0; y < y1; y++)
      for (int x = x0; x < x1; x++) {
        Color pixel = sample(pixels, x, y);
        if (pixel.r < .48f
            && pixel.g > .5f
            && pixel.b > .6f
            && pixel.g > pixel.r + .2f
            && pixel.b > pixel.r + .2f) count++;
      }
    return count;
  }

  private static Color clearColor() {
    return new Color(.035f, .047f, .067f, 1f);
  }

  private Pixmap capture() {
    return Pixmap.createFromFrameBuffer(
        0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
  }

  /** Screenshot Pixmaps use bottom-left storage; demo probes use top-left pixel coordinates. */
  private Color sample(Pixmap pixels, float x, float yFromTop) {
    int px = Math.round(x);
    int topY = Math.round(yFromTop);
    if (px < 0 || px >= pixels.getWidth() || topY < 0 || topY >= pixels.getHeight())
      throw new IllegalStateException("Pixel probe is outside framebuffer: " + x + "," + yFromTop);
    int py = pixels.getHeight() - 1 - topY;
    return new Color(pixels.getPixel(px, py));
  }

  private static boolean near(Color a, Color b, float tolerance) {
    return Math.abs(a.r - b.r) <= tolerance
        && Math.abs(a.g - b.g) <= tolerance
        && Math.abs(a.b - b.b) <= tolerance;
  }

  @Override
  public void resize(int width, int height) {
    if (width <= 0 || height <= 0) return;
    int heightDelta = height - this.height;
    for (GameObject object : topAnchoredObjects) object.transform.position.y += heightDelta;
    this.width = width;
    this.height = height;
    if (renderer != null) renderer.resize(width, height);
    if (camera != null) {
      camera.resize(width, height);
      cameraObject.setPosition(width / 2f, height / 2f);
    }
    if (stage != null) {
      stage.getViewport().update(width, height, true);
      if (uiSentinel != null) uiSentinel.setPosition(16, height - 26);
    }
    if (worldMarkerObject != null) worldMarkerObject.setPosition(310, height - 212);
    if (screenMarker != null) {
      var object = screenMarker.getGameObject();
      object.setPosition(width - 162, height - 72);
    }
    if (spriteTexture != null) {
      var sprite =
          game.getObjects().stream()
              .filter(o -> o.name.equals("sprite sentinel"))
              .findFirst()
              .orElse(null);
      if (sprite != null) sprite.setPosition(width - 42, height - 42);
    }
  }

  @Override
  public void dispose() {
    if (game != null) game.dispose();
    if (stage != null) stage.dispose();
    if (theme != null) theme.dispose();
    if (renderer != null) renderer.dispose();
    if (spriteTexture != null) spriteTexture.dispose();
  }
}
