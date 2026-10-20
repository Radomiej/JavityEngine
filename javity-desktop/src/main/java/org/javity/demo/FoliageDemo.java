package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.utils.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.*;
import org.javity.core.*;
import org.javity.render.*;
import org.javity.render.foliage.*;
import org.javity.render.lighting.*;

/** Original CC0 woodland, ordinary merged patches and shared branching tree models. */
public final class FoliageDemo extends ApplicationAdapter {
  private final boolean smoke, gallery;
  private final FileHandle output;
  private final List<Model> models = new ArrayList<>();
  private Game game;
  private GdxRenderer renderer;
  private Camera3D camera;
  private RenderEnvironment3D environment;
  private DirectionalLight3D sun;
  private Texture atlasTexture;
  private SpriteBatch batch;
  private BitmapFont font;
  private Throwable failure;
  private boolean disposed;
  private int frame;

  public FoliageDemo(boolean smoke) {
    this(smoke, false, "build/reports/foliage");
  }

  private FoliageDemo(boolean smoke, boolean gallery, String output) {
    this.smoke = smoke;
    this.gallery = gallery;
    this.output = new FileHandle(output);
  }

  public static void main(String[] args) {
    List<String> flags = Arrays.asList(args);
    boolean smoke = flags.contains("--smoke"), gallery = flags.contains("--gallery");
    if (smoke && gallery) throw new IllegalArgumentException("Choose --smoke or --gallery");
    String output = gallery ? "build/reports/gallery/foliage" : "build/reports/foliage";
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith("--output=")) output = args[i].substring(9);
      else if (args[i].equals("--output") && i + 1 < args.length) output = args[++i];
    }
    boolean gl20 = flags.contains("--gl20");
    var config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Javity · The fernwood path");
    config.setWindowedMode(gallery ? 1920 : 960, gallery ? 1080 : 640);
    config.setInitialVisible(!smoke && !gallery);
    config.disableAudio(true);
    config.setOpenGLEmulation(
        gl20
            ? Lwjgl3ApplicationConfiguration.GLEmulation.GL20
            : Lwjgl3ApplicationConfiguration.GLEmulation.GL30,
        gl20 ? 2 : 3,
        gl20 ? 0 : 2);
    var demo = new FoliageDemo(smoke, gallery, output);
    new Lwjgl3Application(demo, config);
    if (demo.failure != null) throw new AssertionError("Foliage demo failed", demo.failure);
  }

  @Override
  public void create() {
    try {
      if (smoke) pixelProof();
      game = new Game();
      environment = game.addSystem(new RenderEnvironment3D());
      renderer = new GdxRenderer();
      camera = game.createObject("Woodland view").addComponent(new Camera3D(48));
      camera.camera.near = .1f;
      camera.camera.far = 90;
      view(false);
      GameObject light = game.createObject("Warm afternoon sun");
      light
          .addComponent(new Transform3D())
          .rotation
          .setFromCross(new Vector3(0, 0, -1), new Vector3(-.5f, -1, -.3f).nor());
      sun = light.addComponent(new DirectionalLight3D());
      day(false);
      atlasTexture = new Texture(Gdx.files.classpath("foliage/leaves-and-grass.png"), true);
      atlasTexture.setFilter(
          Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
      FoliageAtlas atlas = FoliageAtlas.bundled(atlasTexture);
      Model ground =
          new ModelBuilder()
              .createBox(
                  32,
                  .2f,
                  35,
                  new Material(ColorAttribute.createDiffuse(new Color(0x657249ff))),
                  attributes());
      models.add(ground);
      object("Moss and root bed", ground, 0, -.17f, -5, 1, 1, 1, 0);
      Model path =
          new ModelBuilder()
              .createBox(
                  1,
                  1,
                  1,
                  new Material(ColorAttribute.createDiffuse(new Color(0xb7a57eff))),
                  attributes());
      models.add(path);
      for (int i = 0; i < 12; i++)
        object(
            "Warm woodland path " + i,
            path,
            (float) Math.sin(i * .39f) * .7f,
            -.075f,
            7 - i * 1.6f,
            2.35f,
            .08f,
            1.9f,
            i * 3);
      for (int side : new int[] {-1, 1}) {
        for (int i = 0; i < 3; i++) {
          patch(
              "Meadow bank " + side + "/" + i,
              FoliagePatch3D.grass(atlas).area(5, 6).count(240).seed(40 + side * 5 + i),
              side * 4.2f,
              0,
              3 - i * 5.7f);
          patch(
              "Fern and twig bank " + side + "/" + i,
              FoliagePatch3D.bushes(atlas).area(4, 3).count(7).seed(203 + side + i),
              side * 5.1f,
              0,
              1 - i * 5.5f);
        }
      }
      Model tree = tree(atlas);
      models.add(tree);
      object("Forked oak / west", tree, -4.8f, 0, -3.3f, 1, 1, 1, 14)
          .addComponent(new InstancedModel());
      object("Forked oak / east", tree, 5.3f, 0, -7.2f, 1.2f, 1.2f, 1.2f, -27)
          .addComponent(new InstancedModel());
      object("Distant forked oak", tree, -7.2f, 0, -13.5f, .9f, .9f, .9f, 53)
          .addComponent(new InstancedModel());
      game.start();
      batch = new SpriteBatch();
      font = new BitmapFont();
    } catch (Throwable t) {
      fail(t);
    }
  }

  private static long attributes() {
    return VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
  }

  private void patch(String name, FoliagePatch3D patch, float x, float y, float z) {
    GameObject o = game.createObject(name);
    o.addComponent(new Transform3D()).position.set(x, y, z);
    o.addComponent(patch);
  }

  private GameObject object(
      String name,
      Model model,
      float x,
      float y,
      float z,
      float sx,
      float sy,
      float sz,
      float yaw) {
    GameObject o = game.createObject(name);
    Transform3D t = o.addComponent(new Transform3D());
    t.position.set(x, y, z);
    t.scale.set(sx, sy, sz);
    t.rotation.set(Vector3.Y, yaw);
    o.addComponent(new ModelRenderer(model));
    return o;
  }

  /** A shared ordinary Model: tapered trunk, forked branches, irregular twig-card crown. */
  private static Model tree(FoliageAtlas atlas) {
    ModelBuilder b = new ModelBuilder();
    b.begin();
    Material bark = new Material(ColorAttribute.createDiffuse(new Color(0x6b5135ff)));
    MeshPartBuilder trunk = b.part("branching-bark", GL20.GL_TRIANGLES, attributes(), bark);
    branch(trunk, new Vector3(0, 0, 0), new Vector3(.12f, 3.4f, 0), .45f, .20f);
    Random random = new Random(742);
    List<Vector3> crowns = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      float a = i * MathUtils.PI2 / 7;
      Vector3 end =
          new Vector3(
              MathUtils.cos(a) * (1.1f + (i % 2) * .4f),
              3.25f + (i % 3) * .48f,
              MathUtils.sin(a) * 1.35f);
      branch(trunk, new Vector3(.08f, 1.7f + i * .13f, 0), end, .19f, .07f);
      crowns.add(end);
      Vector3 twig =
          end.cpy().add(.35f * MathUtils.cos(a + .7f), .47f, .35f * MathUtils.sin(a + .7f));
      branch(trunk, end, twig, .07f, .025f);
      crowns.add(twig);
    }
    Material leaves =
        new Material(
            TextureAttribute.createDiffuse(atlas.texture()),
            FloatAttribute.createAlphaTest(.5f),
            new BlendingAttribute(false, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1),
            new DepthTestAttribute(GL20.GL_LEQUAL, true),
            IntAttribute.createCullFace(GL20.GL_NONE));
    MeshPartBuilder cards =
        b.part(
            "authored-leafy-crown",
            GL20.GL_TRIANGLES,
            attributes() | VertexAttributes.Usage.TextureCoordinates,
            leaves);
    for (Vector3 center : crowns)
      for (int j = 0; j < 12; j++) {
        float a = random.nextFloat() * MathUtils.PI2, w = .65f + random.nextFloat() * .55f;
        Vector3 c =
            center
                .cpy()
                .add(
                    (random.nextFloat() - .5f) * 1.3f,
                    (random.nextFloat() - .5f) * 1.1f,
                    (random.nextFloat() - .5f) * 1.3f);
        Vector3 right = new Vector3(MathUtils.cos(a) * w, 0, MathUtils.sin(a) * w);
        Vector3 up =
            new Vector3(
                (random.nextFloat() - .5f) * .9f,
                .55f + random.nextFloat() * .65f,
                (random.nextFloat() - .5f) * .9f);
        FoliageAtlas.Region uv =
            atlas.leafRegions().get(random.nextInt(atlas.leafRegions().size()));
        cards.setUVRange(uv.u(), uv.v(), uv.u2(), uv.v2());
        cards.rect(
            c.cpy().sub(right).sub(up),
            c.cpy().add(right).sub(up),
            c.cpy().add(right).add(up),
            c.cpy().sub(right).add(up),
            new Vector3(.1f, 1, .15f).nor());
      }
    return b.end();
  }

  private static void branch(
      MeshPartBuilder part, Vector3 from, Vector3 to, float bottom, float top) {
    Vector3 delta = to.cpy().sub(from);
    Quaternion rotation = new Quaternion().setFromCross(Vector3.Y, delta.cpy().nor());
    for (int i = 0; i < 9; i++) {
      float a = i * MathUtils.PI2 / 9, next = (i + 1) * MathUtils.PI2 / 9;
      Vector3 p0 =
          rotation
              .transform(
                  new Vector3(MathUtils.cos(a) * bottom * .5f, 0, MathUtils.sin(a) * bottom * .5f))
              .add(from);
      Vector3 p1 =
          rotation
              .transform(
                  new Vector3(
                      MathUtils.cos(a) * top * .5f, delta.len(), MathUtils.sin(a) * top * .5f))
              .add(from);
      Vector3 p2 =
          rotation
              .transform(
                  new Vector3(
                      MathUtils.cos(next) * top * .5f,
                      delta.len(),
                      MathUtils.sin(next) * top * .5f))
              .add(from);
      Vector3 p3 =
          rotation
              .transform(
                  new Vector3(
                      MathUtils.cos(next) * bottom * .5f, 0, MathUtils.sin(next) * bottom * .5f))
              .add(from);
      Vector3 normal =
          rotation.transform(
              new Vector3(MathUtils.cos((a + next) * .5f), 0, MathUtils.sin((a + next) * .5f)));
      part.rect(p0, p1, p2, p3, normal);
    }
  }

  private void view(boolean close) {
    camera.camera.position.set(close ? 2.6f : 11.5f, close ? 1.7f : 7.3f, close ? 4.2f : 15.4f);
    if (gallery && !close) camera.camera.position.set(8.5f, 4.3f, 12);
    camera.camera.lookAt(close ? -1.6f : 0, close ? .8f : 1.1f, close ? -1.5f : -4);
    camera.camera.update();
  }

  private void day(boolean dusk) {
    environment.setAmbient(dusk ? .13f : .40f, dusk ? .16f : .43f, dusk ? .22f : .31f);
    sun.setColor(dusk ? 1 : .98f, dusk ? .48f : .87f, dusk ? .23f : .64f);
    sun.setIntensity(dusk ? .48f : .95f);
    if (dusk) environment.setFog(.36f, .39f, .45f, 12, 34);
    else environment.clearFog();
  }

  @Override
  public void resize(int width, int height) {
    if (renderer != null) renderer.resize(width, height);
    if (batch != null) batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
  }

  @Override
  public void render() {
    if (disposed || failure != null) return;
    try {
      if (!smoke && !gallery) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
          day(false);
          view(false);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) view(true);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) {
          day(true);
          view(false);
        }
      }
      if ((smoke || gallery) && frame == 2) view(true);
      if ((smoke || gallery) && frame == 4) {
        view(false);
        day(true);
      }
      game.step(0);
      Gdx.gl.glClearColor(.55f, .66f, .72f, 1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      renderer.renderWorld(game, camera, null);
      if (!gallery) drawHud();
      if (smoke || gallery) {
        if (gallery
            && (Gdx.graphics.getBackBufferWidth() != 1920
                || Gdx.graphics.getBackBufferHeight() != 1080))
          throw new IllegalStateException("Gallery requires an actual 1920x1080 framebuffer");
        if (frame == 1) capture("day-wide");
        if (frame == 3) capture("day-close");
        if (frame == 5) capture("dusk-wide");
        if (frame++ == 6) {
          if (Gdx.gl.glGetError() != GL20.GL_NO_ERROR)
            throw new AssertionError("Foliage demo GL error");
          System.out.println(
              (gallery ? "FoliageGallery: PASS native 1920x1080 " : "FoliageDemo: PASS beauty ")
                  + "day/close/dusk "
                  + renderer.foliageStats()
                  + " trees="
                  + renderer.instanceStats());
          dispose();
          Gdx.app.exit();
        }
      }
    } catch (Throwable t) {
      fail(t);
    }
  }

  private void drawHud() {
    batch.begin();
    font.setColor(Color.WHITE);
    font.draw(
        batch,
        "THE FERNWOOD PATH   |   1 Day   2 Close   3 Dusk",
        24,
        Gdx.graphics.getHeight() - 24);
    font.draw(
        batch,
        "Static merged grass / shrubs. Shared branching trees. Original CC0 atlas.",
        24,
        56);
    var stats = renderer.foliageStats();
    font.draw(
        batch,
        "Patches "
            + stats.patches()
            + " / draws "
            + stats.draws()
            + " / vertices "
            + stats.vertices()
            + " / geometry "
            + stats.geometryBytes()
            + " bytes / builds "
            + stats.builds(),
        24,
        20);
    var trees = renderer.instanceStats();
    font.draw(
        batch,
        "Shared trees: "
            + trees.instanced()
            + " instanced / "
            + trees.fallback()
            + " ordinary fallback (scene lighting)",
        24,
        38);
    batch.end();
  }

  private void capture(String name) {
    Pixmap p =
        ScreenUtils.getFrameBufferPixmap(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      output.mkdirs();
      PixmapIO.writePNG(output.child(name + ".png"), p, -1, true);
    } finally {
      p.dispose();
    }
  }

  private void fail(Throwable t) {
    failure = t;
    try {
      dispose();
    } catch (RuntimeException | Error cleanupFailure) {
      appendFailure(t, cleanupFailure);
    } finally {
      try {
        t.printStackTrace();
      } finally {
        Gdx.app.exit();
      }
    }
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    Throwable cleanupFailure = null;
    if (game != null) cleanupFailure = clean(cleanupFailure, game::close);
    if (renderer != null) cleanupFailure = clean(cleanupFailure, renderer::dispose);
    for (Model model : models) cleanupFailure = clean(cleanupFailure, model::dispose);
    models.clear();
    if (atlasTexture != null) cleanupFailure = clean(cleanupFailure, atlasTexture::dispose);
    if (batch != null) cleanupFailure = clean(cleanupFailure, batch::dispose);
    if (font != null) cleanupFailure = clean(cleanupFailure, font::dispose);
    if (cleanupFailure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
    if (cleanupFailure instanceof Error errorFailure) throw errorFailure;
  }

  private static Throwable clean(Throwable first, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException | Error next) {
      return appendFailure(first, next);
    }
    return first;
  }

  private static Throwable appendFailure(Throwable first, Throwable next) {
    if (first == null) return next;
    if (first == next) return first;
    for (Throwable previous : first.getSuppressed()) if (previous == next) return first;
    first.addSuppressed(next);
    return first;
  }

  private static void pixelProof() {
    FoliagePixelProof.run();
  }
}
