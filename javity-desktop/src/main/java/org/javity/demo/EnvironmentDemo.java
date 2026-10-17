package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.render.*;
import org.javity.render.lighting.*;
import org.javity.ui.flex.*;
import org.javity.ui.inspector.RuntimeToolsOverlay;

/** Original stone observatory: plain GameObjects, typed lights, WORLD cycle and optional tools. */
public final class EnvironmentDemo extends ApplicationAdapter {
  private final boolean smoke;
  private Game game;
  private GdxRenderer renderer;
  private Camera3D camera;
  private RenderEnvironment3D environment;
  private DirectionalLight3D sun;
  private PointLight3D point;
  private SpotLight3D spot;
  private DayNightCycle3D cycle;
  private GameObject sunObject, pointObject, spotObject;
  private final Array<Model> models = new Array<>();
  private Stage stage;
  private UiTheme theme;
  private RuntimeToolsOverlay tools;
  private Flex panel;
  private Label status;
  private boolean fog, disposed;
  private int frame;
  private static final String OUTPUT = "docs/artifacts/environment/";

  public EnvironmentDemo(boolean smoke) {
    this.smoke = smoke;
  }

  @Override
  public void create() {
    try {
      game = new Game();
      environment = game.addSystem(new RenderEnvironment3D());
      renderer = new GdxRenderer();
      camera = game.createObject("Courtyard camera").addComponent(new Camera3D(46));
      camera.camera.position.set(15, 10, 21);
      camera.camera.lookAt(0, 1.2f, -2);
      camera.camera.far = 100;
      camera.camera.update();
      sunObject = game.createObject("Sun / WORLD day cycle");
      sunObject.addComponent(new Transform3D()).position.set(0, 8, -3);
      sun = sunObject.addComponent(new DirectionalLight3D());
      cycle = sunObject.addComponent(new DayNightCycle3D(sun, environment, 90));
      cycle.setPhase(.57);
      cycle.setEnabled(false);
      pointObject = game.createObject("Amber point / western plinth");
      pointObject.addComponent(new Transform3D()).position.set(-3, 2.8f, 0);
      point = pointObject.addComponent(new PointLight3D());
      point.setColor(1, .39f, .12f);
      point.setIntensity(2.8f);
      point.setRange(11);
      spotObject = game.createObject("Blue spot / central exhibit");
      var spotTransform = spotObject.addComponent(new Transform3D());
      spotTransform.position.set(4, 6, 3);
      spotTransform.rotation.setFromCross(new Vector3(0, 0, -1), new Vector3(-2, -5, -3).nor());
      spot = spotObject.addComponent(new SpotLight3D());
      spot.setColor(.22f, .58f, 1);
      spot.setIntensity(4);
      spot.setRange(15);
      spot.setCutoffDegrees(24);
      spot.setExponent(2);
      buildCourtyard();
      game.start();
      stage = new Stage(new ScreenViewport());
      theme = new UiTheme();
      panel = new Flex(theme, "flex-col p-4 gap-1 bg-slate-900 rounded-lg border border-slate-700");
      panel.add(theme.label("THE QUIET OBSERVATORY", "text-xl text-cyan-400"), "h-8");
      panel.add(
          theme.label("Light, distance and the passage of time", "text-sm text-slate-200", true),
          "h-8");
      button("01   Clear afternoon", () -> preset(.57, false));
      button("02   Last light", () -> preset(.735, false));
      button("03   Midnight / local lights", () -> preset(0, false));
      button("04   Morning mist", () -> preset(.43, true));
      button("WORLD cycle / Run or stop", () -> cycle.setEnabled(!cycle.isEnabled()));
      button(
          "Pause / resume WORLD",
          () -> {
            if (game.isPaused()) game.resume();
            else game.pause();
          });
      button("Point light / toggle", () -> point.setEnabled(!point.isEnabled()));
      button("Spot light / toggle", () -> spot.setEnabled(!spot.isEnabled()));
      button(
          "F1 tools / select sun",
          () -> {
            cycle.setEnabled(false);
            tools.inspector().setVisible(true);
            tools.inspector().selectObject(sunObject);
          });
      button(
          "Edit amber point",
          () -> {
            tools.inspector().setVisible(true);
            tools.inspector().selectObject(pointObject);
          });
      button(
          "Edit blue spotlight",
          () -> {
            tools.inspector().setVisible(true);
            tools.inspector().selectObject(spotObject);
          });
      status = panel.add(theme.label("", "text-sm text-slate-200", true), "h-16");
      panel.add(
          theme.label(
              "1-4 atmosphere / F1 scene tools\n"
                  + "Transform axes / Undo / Redo in tools.\n"
                  + "Cubemap: image stays fixed.",
              "text-sm text-slate-400",
              true),
          "h-20");
      stage.addActor(panel);
      tools = new RuntimeToolsOverlay(stage, theme, () -> game);
      tools.inspector().setGizmoCameras(camera.camera, null);
      tools.setDebugCameraProvider(() -> camera.camera);
      Gdx.input.setInputProcessor(
          new InputMultiplexer(
              tools.inspector().inputProcessor(),
              stage,
              tools.inspector().gizmoInputProcessor(),
              new InputAdapter() {
                @Override
                public boolean keyDown(int key) {
                  if (key >= Input.Keys.NUM_1 && key <= Input.Keys.NUM_4) {
                    int n = key - Input.Keys.NUM_1;
                    preset(n == 1 ? .735 : n == 2 ? 0 : n == 3 ? .43 : .57, n == 3);
                    return true;
                  }
                  return false;
                }
              }));
    } catch (RuntimeException | Error failure) {
      dispose();
      throw failure;
    }
  }

  private void button(String text, Runnable action) {
    panel.add(theme.button(text, "text-sm text-white bg-slate-700 rounded", action), "h-8");
  }

  private void preset(double phase, boolean mist) {
    cycle.setEnabled(false);
    cycle.setPhase(phase);
    setFog(mist);
  }

  private void setFog(boolean value) {
    fog = value;
    if (value) environment.setFog(.60f, .72f, .81f, 10, 42);
    else environment.clearFog();
  }

  private void buildCourtyard() {
    Model ground = cylinder(24, .6f, 24, 0x8e9b91ff, 64),
        base = cylinder(11, .45f, 11, 0xbab8a4ff, 64);
    object("Observatory island", ground, 0, -.55f, -2, 1, 1, 1);
    object("Circular limestone court", base, 0, -.08f, -2, 1, 1, 1);
    Model block = box(0xc8c8b8ff),
        dark = box(0x687d82ff),
        path = box(0xb3b3a3ff),
        brass = box(0xd6a354ff);
    for (int i = 0; i < 6; i++)
      object("Approach slab " + i, path, 0, -.16f, 5 + i * 1.4f, 2.5f, .2f, 1.15f);
    for (int i = 0; i < 12; i++) {
      float a = i * MathUtils.PI2 / 12;
      float x = MathUtils.cos(a) * 4.8f, z = -2 + MathUtils.sin(a) * 4.8f;
      object("Court border " + i, dark, x, .12f, z, .55f, .18f, .55f);
    }
    for (int i = 0; i < 5; i++) {
      float x = (i - 2) * 2.65f;
      object("North colonnade foot " + i, block, x, .25f, -6.5f, 1.3f, .4f, 1.3f);
      object("North colonnade shaft " + i, block, x, 2.0f, -6.5f, .68f, 3.2f, .68f);
      object("North colonnade capital " + i, block, x, 3.7f, -6.5f, 1.15f, .28f, 1.15f);
    }
    object("Lintel", block, 0, 4.05f, -6.5f, 12.2f, .5f, 1.05f);
    object("Lintel bronze band", brass, 0, 4.08f, -5.95f, 11.4f, .12f, .05f);
    object("Western pedestal", dark, -3, .5f, 0, 1.8f, 1, 1.8f);
    object("Eastern pedestal", dark, 2.4f, .6f, -1.5f, 2, 1.2f, 2);
    Model orange = sphere(0xda8d49ff), blue = sphere(0x5d9cafff);
    object("Amber ceramic sphere", orange, -3, 1.6f, 0, 1.7f, 1.7f, 1.7f);
    object("Blue ceramic sphere", blue, 2.4f, 1.9f, -1.5f, 2, 2, 2);
    object("Center obelisk", block, -.2f, 1.4f, -3.2f, .65f, 2.8f, .65f);
    object("Obelisk crown", brass, -.2f, 2.9f, -3.2f, .8f, .18f, .8f);
    // Receding architectural silhouettes give distance fog something legible to hide.
    for (int i = 0; i < 7; i++)
      object(
          "Distant stone " + i,
          dark,
          -14 + i * 4,
          1.5f,
          -19 - (i % 3) * 4,
          1.5f,
          3 + (i % 3),
          1.5f);
  }

  private Material material(int rgba) {
    return new Material(
        ColorAttribute.createDiffuse(new Color(rgba)),
        ColorAttribute.createSpecular(.12f, .12f, .12f, 1),
        FloatAttribute.createShininess(18));
  }

  private Model box(int color) {
    Model m =
        new ModelBuilder()
            .createBox(
                1,
                1,
                1,
                material(color),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    models.add(m);
    return m;
  }

  private Model cylinder(float x, float y, float z, int color, int divisions) {
    Model m =
        new ModelBuilder()
            .createCylinder(
                x,
                y,
                z,
                divisions,
                material(color),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    models.add(m);
    return m;
  }

  private Model sphere(int color) {
    Model m =
        new ModelBuilder()
            .createSphere(
                1,
                1,
                1,
                32,
                24,
                material(color),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    models.add(m);
    return m;
  }

  private void object(
      String name, Model model, float x, float y, float z, float sx, float sy, float sz) {
    GameObject o = game.createObject(name);
    var t = o.addComponent(new Transform3D());
    t.position.set(x, y, z);
    t.scale.set(sx, sy, sz);
    o.addComponent(new ModelRenderer(model));
    o.addComponent(new InstancedModel());
  }

  @Override
  public void resize(int width, int height) {
    if (renderer != null) renderer.resize(width, height);
    if (stage != null) stage.getViewport().update(width, height, true);
  }

  @Override
  public void render() {
    if (disposed) return;
    float dt = smoke ? 0 : Math.min(.05f, Gdx.graphics.getDeltaTime());
    game.step(dt);
    if (smoke) {
      if (frame == 0) preset(.57, false);
      if (frame == 6) preset(.735, false);
      if (frame == 12) preset(0, false);
      if (frame == 18) preset(.43, true);
    }
    Gdx.gl.glClearColor(.05f, .08f, .12f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    renderer.renderWorld(game, camera, null);
    tools.update(dt);
    tools.drawGizmos();
    status.setText(
        String.format(
            java.util.Locale.ROOT,
            "Phase %.3f  /  %s\nWORLD %s  /  Mist %s\nPoint %s  /  Spot %s",
            cycle.phase(),
            cycle.isEnabled() ? "cycling" : "held",
            game.isPaused() ? "paused" : "running",
            fog ? "on" : "off",
            point.isEnabled() ? "on" : "off",
            spot.isEnabled() ? "on" : "off"));
    panel.setVisible(!tools.inspector().isVisible());
    panel.setBounds(24, 24, 330, Math.max(0, stage.getHeight() - 48));
    panel.validate();
    stage.act(dt);
    stage.draw();
    if (smoke) {
      if (frame == 4) capture("afternoon");
      if (frame == 10) capture("last-light");
      if (frame == 16) capture("midnight");
      if (frame == 22) capture("mist");
      if (frame == 24) {
        preset(0, false);
        point.setEnabled(false);
      }
      if (frame == 28) capture("spot-only");
      if (frame == 30) {
        point.setEnabled(true);
        spot.setEnabled(false);
      }
      if (frame == 34) capture("point-only");
      if (frame == 36) {
        preset(.57, false);
        spot.setEnabled(true);
        tools.inspector().setVisible(true);
        tools.inspector().selectObject(spotObject);
      }
      if (frame == 38) {
        com.badlogic.gdx.scenes.scene2d.ui.TextField intensity =
            stage.getRoot().findActor("light-3");
        stage.setKeyboardFocus(intensity);
        intensity.setText("3.5");
        tools.lightAuthoring().apply();
        if (spot.intensity() != 3.5f)
          throw new AssertionError("Typed panel Apply did not edit selected spotlight");
      }
      if (frame == 42) capture("authoring");
      if (frame == 43) {
        var focused = stage.getKeyboardFocus();
        if (focused == null) throw new AssertionError("Light field focus setup failed");
        tools.inspector().setVisible(false);
        if (stage.getKeyboardFocus() == focused)
          throw new AssertionError("Hidden light editor retained keyboard focus");
      }
      if (frame == 44) checkDynamicLightAuthoring();
      if (frame++ > 44) {
        if (Gdx.gl.glGetError() != GL20.GL_NO_ERROR) throw new AssertionError("Demo GL error");
        dispose();
        dispose();
        System.out.println(
            "EnvironmentDemo: PASS courtyard day/dusk/night/fog/local lights/authoring captures and"
                + " repeated owner dispose");
        Gdx.app.exit();
      }
    }
  }

  private void checkDynamicLightAuthoring() {
    var editor = tools.lightAuthoring();
    Flex root = stage.getRoot().findActor("light-authoring-panel");
    GameObject target = game.createObject("Dynamic light authoring smoke");
    target.addComponent(new Transform3D());
    tools.inspector().setVisible(true);
    tools.inspector().selectObject(target);
    tools.update(0);
    if (editor.hasLight() || root.isVisible())
      throw new AssertionError("Selected lightless object shows a light draft");

    PointLight3D first = target.addComponent(new PointLight3D());
    game.initializePendingComponents();
    tools.update(0);
    if (!editor.hasLight() || !root.isVisible())
      throw new AssertionError("First added light was not discovered on the same selection");
    com.badlogic.gdx.scenes.scene2d.ui.TextField intensity = root.findActor("light-3");
    intensity.setText("2.25");
    stage.setKeyboardFocus(intensity);
    target.addComponent(new org.javity.core.Component() {});
    PointLight3D second = target.addComponent(new PointLight3D());
    second.setIntensity(7);
    game.initializePendingComponents();
    tools.update(0);
    tools.update(0);
    if (!intensity.getText().equals("2.25") || stage.getKeyboardFocus() != intensity)
      throw new AssertionError("Component insertion or update reset the existing dirty light draft");
    editor.apply();
    if (first.intensity() != 2.25f || second.intensity() != 7)
      throw new AssertionError("Dirty light draft Apply lost exact component identity");

    target.removeComponent(first);
    tools.update(0);
    intensity.setText("4.5");
    editor.apply();
    if (second.intensity() != 4.5f)
      throw new AssertionError("Removing selected light did not refresh to the remaining light");
    target.removeComponent(second);
    tools.update(0);
    if (editor.hasLight() || root.isVisible() || stage.getKeyboardFocus() == intensity)
      throw new AssertionError("Removing final light retained draft, visibility or keyboard focus");
    PointLight3D replacement = target.addComponent(new PointLight3D());
    game.initializePendingComponents();
    tools.update(0);
    if (!editor.hasLight() || !root.isVisible())
      throw new AssertionError("Readded first light was not discovered on the same selection");
    intensity.setText("6.25");
    editor.apply();
    if (replacement.intensity() != 6.25f
        || stage.getRoot().findActor("light-authoring-panel") != root)
      throw new AssertionError("Replacement light Apply failed or rebuilt the panel root");
    System.out.println("LightAuthoringPanel: PASS dynamic first-light discovery, dirty draft identity, removal/readd and focus");
  }

  private void capture(String name) {
    Pixmap p =
        Pixmap.createFromFrameBuffer(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      com.badlogic.gdx.graphics.PixmapIO.writePNG(
          Gdx.files.local(OUTPUT + name + ".png"), p, -1, true);
    } finally {
      p.dispose();
    }
  }

  private static Throwable clean(Throwable first, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException | Error failure) {
      if (first == null) return failure;
      if (first != failure) first.addSuppressed(failure);
    }
    return first;
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    Throwable failure = null;
    Gdx.input.setInputProcessor(null);
    if (tools != null) failure = clean(failure, tools::close);
    if (panel != null)
      failure =
          clean(
              failure,
              () -> {
                panel.remove();
                panel.close();
              });
    if (stage != null) failure = clean(failure, stage::dispose);
    if (theme != null) failure = clean(failure, theme::dispose);
    if (game != null) failure = clean(failure, game::close);
    if (renderer != null) failure = clean(failure, renderer::dispose);
    for (Model model : models) failure = clean(failure, model::dispose);
    models.clear();
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }
}
