package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.List;
import org.javity.assets.AssetKey;
import org.javity.assets.AssetLease;
import org.javity.assets.AssetStreams;
import org.javity.assets.StreamBudget;
import org.javity.core.Component;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.render.Camera3D;
import org.javity.render.GdxRenderer;
import org.javity.render.ModelRenderer;
import org.javity.render.ShapeRenderer2D;
import org.javity.ui.flex.UiTheme;

/** Local, scene-owned streaming: components borrow the model while a lease pins its root. */
public final class AssetStreamingDemo extends ApplicationAdapter {
  static final String ROOT = "org/javity/demo/asset-streaming/";
  private static final AssetKey<Model> MODEL = new AssetKey<>(ROOT + "signal-crate.g3dj", Model.class);
  private static final AssetKey<Texture> PREFETCH = new AssetKey<>(ROOT + "prefetch.png", Texture.class);
  private static final AssetKey<Model> MISSING = new AssetKey<>(ROOT + "missing.g3dj", Model.class);
  private static final long MODEL_ESTIMATE = 64 * 1024, TEXTURE_ESTIMATE = 16 * 1024;
  private final boolean smoke;
  Game game;
  AssetStreams streams;
  GameObject modelObject;
  ModelRenderer display;
  AssetLease<Model> first, second, missing;
  AssetLease<Texture> prefetch;
  AssetStreamingGpuSmokeProbe.GatedTextureLoader gate;
  int frames, modelLoads;
  float sentinelTime;
  private List<AssetLease<?>> batch = List.of();
  private GdxRenderer renderer;
  private Stage stage;
  private UiTheme theme;
  private Table panel;
  private Label requests, statistics;
  private Transform3D modelTransform;
  private AssetStreamingGpuSmokeProbe probe;
  private boolean disposed;

  public AssetStreamingDemo(boolean smoke) { this.smoke = smoke; }

  @Override public void create() {
    if (smoke) probe = new AssetStreamingGpuSmokeProbe();
    game = new Game();
    AssetManager manager = new AssetManager();
    if (smoke) {
      gate = new AssetStreamingGpuSmokeProbe.GatedTextureLoader(manager.getFileHandleResolver());
      manager.setLoader(Texture.class, gate);
      manager.setLoader(Model.class, ".g3dj", new AssetStreamingGpuSmokeProbe.CountingModelLoader(
          manager.getFileHandleResolver(), () -> modelLoads++));
    }
    // One resident ROOT entry makes pinned backpressure and unpinned replacement observable.
    streams = game.addSystem(new AssetStreams(manager, new StreamBudget(8, 1, 96 * 1024, 1, 2)));
    renderer = new GdxRenderer();
    renderer.environment.clear();
    renderer.environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 1, 1, 1, 1));
    GameObject cameraObject = game.createObject("streaming camera");
    cameraObject.addComponent(new Transform3D()).position.set(0, 0, 6);
    var camera = cameraObject.addComponent(new Camera3D(50)).camera;
    camera.position.set(0, 0, 6);
    camera.far = 30;
    modelObject = game.createObject("leased signal crate");
    modelTransform = modelObject.addComponent(new Transform3D());
    modelTransform.position.set(1.35f, 0, 0);
    GameObject sentinel = game.createObject("live UI sentinel");
    ShapeRenderer2D shape = sentinel.addComponent(ShapeRenderer2D.circle(12));
    shape.space = ShapeRenderer2D.Space.SCREEN;
    shape.color.set(Color.valueOf("fbbf24"));
    sentinel.addComponent(new Component() {
      { updateMode = UpdateMode.UI; }
      @Override public void onUpdate(float delta) {
        sentinelTime += delta;
        getTransform().position.set(Gdx.graphics.getWidth() * .68f + 65 * (float) Math.sin(sentinelTime * 3), 90);
      }
    });
    theme = new UiTheme();
    stage = new Stage(new ScreenViewport());
    panel = new Table();
    panel.top().left();
    panel.defaults().left().padBottom(9).growX();
    stage.addActor(panel);
    panel.add(theme.label("LOCAL ASSET STREAMING", "text-xl text-cyan-400")).row();
    panel.add(theme.label("Authored signal crate · default libGDX loaders", "text-sm text-slate-400")).row();
    panel.add(theme.label("R  Request model (two leases)\n1  Release first lease\nX  Detach model, release both\nP  Prefetch different texture\nO  Release prefetch lease\nE  Request missing file / explicit retry\nSpace  Pause WORLD (sentinel uses UI time)", "text-base text-white")).row();
    panel.add(theme.button("Request model [R]", "text-base bg-slate-800 text-white", this::requestModel)).height(36).row();
    Table release = new Table();
    release.add(theme.button("Release first [1]", "text-sm bg-slate-800 text-white", this::releaseFirst)).growX().height(34).padRight(8);
    release.add(theme.button("Detach + release [X]", "text-sm bg-slate-800 text-white", this::releaseAll)).growX().height(34);
    panel.add(release).row();
    Table cache = new Table();
    cache.add(theme.button("Prefetch [P]", "text-sm bg-slate-800 text-white", this::prefetch)).growX().height(34).padRight(8);
    cache.add(theme.button("Release prefetch [O]", "text-sm bg-slate-800 text-white", this::releasePrefetch)).growX().height(34);
    panel.add(cache).row();
    panel.add(theme.button("Missing file / explicit retry [E]", "text-sm bg-slate-800 text-white", this::requestMissing)).height(34).row();
    requests = theme.label("", "text-sm text-white");
    statistics = theme.label("", "text-sm text-slate-400");
    panel.add(requests).row(); panel.add(statistics).row();
    panel.add(theme.label("Capacity: 1 root / 96 KiB estimated\nClose unpins cache; queued pressure evicts.\nInflight is actual 0/1. Counts are not byte progress.\nRelease prefetch before requesting the model again.", "text-sm text-slate-400")).row();
    InputAdapter keys = new InputAdapter() {
      @Override public boolean keyDown(int key) {
        switch (key) {
          case Input.Keys.R -> requestModel();
          case Input.Keys.NUM_1 -> releaseFirst();
          case Input.Keys.X -> releaseAll();
          case Input.Keys.P -> prefetch();
          case Input.Keys.O -> releasePrefetch();
          case Input.Keys.E -> requestMissing();
          case Input.Keys.SPACE -> { if (game.isPaused()) game.resume(); else game.pause(); }
          default -> { return false; }
        }
        return true;
      }
    };
    Gdx.input.setInputProcessor(new InputMultiplexer(stage, keys));
    game.start();
    resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    requestModel();
  }

  void requestModel() {
    releaseAll();
    first = streams.request(MODEL, 10, MODEL_ESTIMATE);
    second = streams.request(MODEL, 10, MODEL_ESTIMATE);
    batch = List.of(first, second);
  }

  void releaseFirst() {
    if (!ready(second)) detachModel();
    if (first != null) first.close(); // second lease continues to pin the displayed model.
  }

  private void detachModel() {
    if (display != null) { modelObject.removeComponent(display); display = null; }
  }

  void releaseAll() {
    // Detach the borrowed renderer before the final lease can become unpinned.
    detachModel();
    if (first != null) first.close();
    if (second != null) second.close();
  }

  void prefetch() {
    if (prefetch == null || prefetch.state() == AssetLease.State.CLOSED)
      prefetch = streams.request(PREFETCH, 1, TEXTURE_ESTIMATE);
    batch = List.of(prefetch);
  }

  void releasePrefetch() { if (prefetch != null) prefetch.close(); }

  void requestMissing() {
    // Only explicit input retries a failed generation; old failures stay inspectable.
    if (missing == null || missing.state() == AssetLease.State.FAILED || missing.state() == AssetLease.State.CLOSED)
      missing = streams.request(MISSING, 5, MODEL_ESTIMATE);
    batch = List.of(missing);
  }

  int batchReady() { return (int) batch.stream().filter(AssetStreamingDemo::ready).count(); }
  int batchFailed() { return (int) batch.stream().filter(lease -> lease.state() == AssetLease.State.FAILED).count(); }
  private static boolean ready(AssetLease<?> lease) { return lease != null && lease.state() == AssetLease.State.READY; }
  private static String state(AssetLease<?> lease) { return lease == null ? "none" : lease.state().name(); }

  @Override public void render() {
    if (disposed) return;
    frames++;
    game.step(Math.min(Gdx.graphics.getDeltaTime(), .05f));
    AssetLease<Model> live = ready(second) ? second : ready(first) ? first : null;
    if (display == null && live != null) {
      display = modelObject.addComponent(new ModelRenderer(live.get()));
      game.initializePendingComponents();
    }
    modelTransform.rotation.setEulerAngles(25 + 10 * (float) Math.sin(game.elapsed), 15, 0);
    updatePanel();
    Gdx.gl.glClearColor(.025f, .04f, .065f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    renderer.render(game);
    stage.act(Math.min(Gdx.graphics.getDeltaTime(), .05f));
    stage.draw();
    if (probe != null) probe.afterRender(this);
    // No ModelBatch/SpriteBatch remains open; pressure/rollback can safely release here.
    if (!disposed) {
      streams.finishFrame();
      if (probe != null) probe.afterFinishFrame();
    }
  }

  private void updatePanel() {
    requests.setText("Model A: " + state(first) + "    B: " + state(second)
        + "\nPrefetch: " + state(prefetch) + "    Missing: " + state(missing)
        + "\nCurrent batch " + batch.size() + " requests: ready " + batchReady() + " / failed " + batchFailed()
        + (missing != null && missing.failure() != null ? "\nMissing file failed; explicit retry required." : ""));
    var stats = streams.stats();
    statistics.setText("Roots queued " + stats.queued() + " / inflight " + stats.inflight()
        + " / ready " + stats.ready() + " / pinned " + stats.pinned()
        + "\nResident estimate " + stats.estimatedResidentBytes() + " B / pressure " + stats.overBudget()
        + "\nSentinel UI time " + String.format(java.util.Locale.ROOT, "%.2f", sentinelTime)
        + " s / WORLD " + (game.isPaused() ? "paused" : "running"));
  }

  @Override public void resize(int width, int height) {
    if (renderer != null) renderer.resize(width, height);
    if (stage != null) {
      stage.getViewport().update(width, height, true);
      panel.setBounds(24, 20, Math.min(438, Math.max(1, width - 48)), Math.max(1, height - 44));
    }
  }

  @Override public void dispose() {
    if (disposed) return;
    disposed = true;
    if (gate != null) gate.release.countDown();
    releaseAll();
    // Game disposes components before provider systems. No rendering follows scene close.
    try { if (game != null) game.close(); }
    finally {
      try { if (renderer != null) renderer.dispose(); }
      finally { try { if (stage != null) stage.dispose(); } finally { if (theme != null) theme.dispose(); } }
    }
  }
}
