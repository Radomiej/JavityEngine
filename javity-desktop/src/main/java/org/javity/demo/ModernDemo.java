package org.javity.demo;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import org.javity.core.*;
import org.javity.core.Game;
import org.javity.physics2d.*;
import org.javity.render.*;
import org.javity.ui.flex.*;
import org.javity.ui.flex.Rectangle;

/** New component runtime alongside the imported legacy backend (--legacy). */
final class ModernDemo extends ApplicationAdapter {
  private final boolean smoke;
  private Game game;
  private GdxRenderer renderer;
  private Stage stage;
  private UiTheme theme;
  private Flex root;
  private TextButton pause;
  private Texture texture;
  private Model model;
  private Physics2D physics;
  private GameObject ball, floor, trigger, smokeCamera;
  private Texture solidTexture;
  private Rectangle rectangle;
  private Label contactStatus;
  private Vector2 pausedPosition;
  private int groundContacts, triggerContacts;
  private int frames, worldUpdates, uiUpdates;

  ModernDemo(boolean smoke) {
    this.smoke = smoke;
  }

  @Override
  public void create() {
    renderer = new GdxRenderer();
    stage = new Stage(new ScreenViewport());
    Gdx.input.setInputProcessor(stage);
    game = new Game();
    physics = game.addSystem(new Physics2D(new Vector2(0, -980), 100));
    var camera = game.createObject("camera").addComponent(new Camera3D(60));
    camera.camera.position.set(0, 1, 5);
    camera.camera.lookAt(0, 0, 0);
    model = renderer.loadModel("models/cube.obj");
    model.materials.get(0).set(ColorAttribute.createDiffuse(Color.valueOf("10b981")));
    GameObject cube = game.createObject("cube").addTag("model");
    Transform3D transform = cube.addComponent(new Transform3D());
    transform.position.set(1.4f, 0, 0);
    cube.addComponent(new ModelRenderer(model));
    cube.addComponent(
        new Component() {
          public void onUpdate(float delta) {
            transform.rotation.mulLeft(new Quaternion(Vector3.Y, delta * 35));
            worldUpdates++;
          }
        });
    Pixmap image = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
    image.setColor(Color.valueOf("22d3ee"));
    image.fillCircle(32, 32, 28);
    image.setColor(Color.WHITE);
    image.fillCircle(25, 40, 5);
    texture = new Texture(image);
    image.dispose();
    ball = game.createObject("ball").setPosition(Gdx.graphics.getWidth() * .7f, 290);
    ball.addComponent(new SpriteRenderer2D(new TextureRegion(texture)));
    ball.addComponent(new RigidBody2D().setFixedRotation(true));
    ball.addComponent(new CircleCollider2D(28).setRestitution(.2f));
    ball.addComponent(new ContactMonitor());
    Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pixel.setColor(Color.WHITE);
    pixel.fill();
    solidTexture = new Texture(pixel);
    pixel.dispose();
    floor = game.createObject("floor").addTag("floor");
    floor.addComponent(new BoxCollider2D(360, 18));
    SpriteRenderer2D floorSprite =
        floor.addComponent(new SpriteRenderer2D(new TextureRegion(solidTexture)));
    floorSprite.width = 360;
    floorSprite.height = 18;
    floorSprite.tint.set(UtilityStyles.color("slate-700"));
    trigger = game.createObject("trigger");
    trigger.addComponent(new BoxCollider2D(360, 40).setTrigger(true));
    Component ui =
        game.createObject("ui-clock")
            .addComponent(
                new Component() {
                  public void onUpdate(float delta) {
                    uiUpdates++;
                  }
                });
    ui.updateMode = Component.UpdateMode.UI;
    createUi();
    game.start();
    resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
  }

  private void createUi() {
    theme = new UiTheme();
    root = new Flex(theme, "flex-col p-8 gap-6");
    root.setFillParent(true);
    stage.addActor(root);
    Flex header = root.add(new Flex(theme, "flex-row items-center justify-between"), "h-16");
    header.add(theme.label("JAVITY", "text-2xl text-cyan-400"), "");
    header.add(theme.label("Java 25  /  2D + 3D  /  Yoga", "text-slate-400"), "");
    Flex body = root.add(new Flex(theme, "flex-row gap-6"), "flex-1");
    Flex panel =
        body.add(
            new Flex(theme, "flex-col p-6 gap-4 bg-slate-800 rounded-xl border-2 border-slate-700"),
            "w-80");
    panel.add(theme.label("A simpler engine", "text-xl"), "");
    panel.add(theme.label("Scene-local component indexes", "text-slate-400"), "");
    panel.add(theme.label("2D sprites + 3D models", "text-emerald-500"), "");
    panel.add(theme.label("Real Yoga / Flexbox layout", "text-cyan-400"), "");
    panel.add(theme.label("Fixed-step physics + rounded UI", "text-slate-400"), "");
    Label status = panel.add(theme.label("World: running", "text-emerald-500"), "");
    contactStatus = panel.add(theme.label("Contacts: 0 / triggers: 0", "text-slate-400"), "");
    rectangle =
        panel.add(
            theme.rectangle("bg-slate-900 rounded-lg border-2 border-cyan-400"), "w-full h-8");
    pause =
        panel.add(
            theme.button(
                "Pause / resume",
                "bg-blue-600 rounded-lg border border-cyan-400",
                () -> {
                  if (game.isPaused()) game.resume();
                  else game.pause();
                  status.setText(game.isPaused() ? "World: paused / UI active" : "World: running");
                }),
            "h-12 w-full");
    panel.add(
        theme.button(
            "Reset ball",
            "bg-slate-700 rounded-lg border border-slate-400",
            () -> {
              ball.setPosition(Gdx.graphics.getWidth() * .7f, 290);
              ball.getComponent(RigidBody2D.class).setLinearVelocity(0, 0);
            }),
        "h-10 w-full");
    body.add(new Flex("flex-col"), "flex-1");
    root.add(
        theme.label(
            "Resize the window. The same components drive 2D and 3D.", "text-sm text-slate-400"),
        "h-8");
  }

  @Override
  public void render() {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(.043f, .071f, .125f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    game.step(Gdx.graphics.getDeltaTime());
    renderer.render(game);
    physics.drawDebug(renderer.camera2D);
    stage.act(game.unscaledDeltaTime);
    stage.draw();
    frames++;
    if (smoke) verifySmoke();
  }

  private void clickPause() {
    Vector2 screen =
        stage.stageToScreenCoordinates(
            pause.localToStageCoordinates(
                new Vector2(pause.getWidth() / 2, pause.getHeight() / 2)));
    stage.touchDown(Math.round(screen.x), Math.round(screen.y), 0, Input.Buttons.LEFT);
    stage.touchUp(Math.round(screen.x), Math.round(screen.y), 0, Input.Buttons.LEFT);
  }

  private void verifySmoke() {
    if (frames == 10) {
      smokeCamera = game.createObject("smoke camera").setPosition(470, 310);
      smokeCamera.transform.rotation = .2f;
      Camera2D camera = smokeCamera.addComponent(new Camera2D());
      camera.setZoom(1.2f);
      camera.setPixelsPerUnit(1.5f);
    }
    if (frames == 15) {
      Camera2D camera = smokeCamera.getComponent(Camera2D.class);
      Vector2 expected = camera.worldToScreen(700, 300);
      Vector3 actual =
          renderer.camera2D.project(
              new Vector3(700, 300, 0), 0, 0, camera.getWidth(), camera.getHeight());
      if (Math.abs(expected.x - actual.x) > .01f
          || Math.abs(expected.y - (camera.getHeight() - actual.y)) > .01f)
        throw new IllegalStateException("Camera2D native projection mismatch");
    }
    if (frames == 20) smokeCamera.destroy();
    if (frames == 30) {
      clickPause();
      pausedPosition = ball.transform.position.cpy();
      if (!game.isPaused()) throw new IllegalStateException("UI click did not pause game");
    }
    if (frames == 60) {
      if (!pausedPosition.equals(ball.transform.position))
        throw new IllegalStateException("Physics advanced during pause");
      clickPause();
      if (game.isPaused()) throw new IllegalStateException("UI click did not resume game");
    }
    if (frames == 90) Gdx.graphics.setWindowedMode(1000, 680);
    if (frames == 120) {
      if (worldUpdates < 80 || uiUpdates != 120 || root.getWidth() != stage.getWidth())
        throw new IllegalStateException("Lifecycle, pause or resize smoke failed");
      if (groundContacts == 0 || triggerContacts == 0 || physics.getBodyCount() != 3)
        throw new IllegalStateException("Physics contacts or bodies were not initialized");
      Pixmap screenshot =
          Pixmap.createFromFrameBuffer(
              0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      verifyRectanglePixels(screenshot);
      PixmapIO.writePNG(Gdx.files.local("smoke.png"), screenshot, -1, true);
      screenshot.dispose();
      Gdx.app.log(
          "JavitySmoke",
          "PASS: 120 frames, 2D + 3D + Camera2D, Yoga rounded/border/Rectangle, Box2D,"
              + " pause/resume, resize; WORLD="
              + worldUpdates
              + ", UI="
              + uiUpdates
              + ", contacts="
              + groundContacts
              + ", triggers="
              + triggerContacts);
      Gdx.app.exit();
    }
  }

  @Override
  public void resize(int width, int height) {
    if (renderer != null) renderer.resize(width, height);
    if (stage != null) stage.getViewport().update(width, height, true);
    if (floor != null) floor.setPosition(width * .7f, 65);
    if (trigger != null) trigger.setPosition(width * .7f, 180);
  }

  @Override
  public void dispose() {
    game.dispose();
    root.dispose();
    stage.dispose();
    theme.dispose();
    renderer.dispose();
    texture.dispose();
    solidTexture.dispose();
  }

  private final class ContactMonitor extends Component implements CollisionListener2D {
    public void onCollisionEnter(Contact2D contact) {
      if (contact.other().hasTag("floor")) groundContacts++;
    }

    public void onTriggerEnter(Contact2D contact) {
      triggerContacts++;
    }

    public void onLateUpdate(float delta) {
      contactStatus.setText("Contacts: " + groundContacts + " / triggers: " + triggerContacts);
    }
  }

  private void verifyRectanglePixels(Pixmap pixels) {
    Color corner = rectanglePixel(pixels, 1, 1);
    Color edge = rectanglePixel(pixels, 0, rectangle.getHeight() / 2);
    Color center = rectanglePixel(pixels, rectangle.getWidth() / 2, rectangle.getHeight() / 2);
    Color panel = UtilityStyles.color("slate-800"), fill = UtilityStyles.color("slate-900");
    if (Math.abs(corner.r - panel.r) > .03f
        || Math.abs(corner.g - panel.g) > .03f
        || Math.abs(center.b - fill.b) > .03f
        || edge.g < .5f
        || edge.b < .5f)
      throw new IllegalStateException(
          "Rounded corner, border or rectangle fill pixel check failed");
  }

  private Color rectanglePixel(Pixmap pixels, float x, float y) {
    Vector2 point = rectangle.localToStageCoordinates(new Vector2(x, y));
    int px = Math.round(point.x * pixels.getWidth() / stage.getWidth());
    int py = Math.round(point.y * pixels.getHeight() / stage.getHeight());
    return new Color(pixels.getPixel(px, py));
  }
}
