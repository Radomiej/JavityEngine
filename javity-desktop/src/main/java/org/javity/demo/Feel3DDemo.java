package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.effects.Clock;
import org.javity.particles.ParticleBurstPool3D;
import org.javity.particles.ParticleBurstRenderer3D;
import org.javity.particles.ParticleBurstSpec3D;
import org.javity.postprocessing.WorldPostProcess;
import org.javity.presentation.CameraPresentation3D;
import org.javity.presentation.Presentation3D;
import org.javity.presentation.PresentationEffects3D;
import org.javity.render.Camera3D;
import org.javity.render.CameraRig3D;
import org.javity.render.GdxRenderer;
import org.javity.render.ModelRenderer;

/** Small interactive first-person gallery for render-only 3D feel effects. Requires GL30. */
public final class Feel3DDemo extends ApplicationAdapter {
  private final Vector3 eye = new Vector3(0, 0, 4),
      look = new Vector3(0, 0, -1),
      target = new Vector3();
  private Game game;
  private GdxRenderer renderer;
  private WorldPostProcess post;
  private ParticleBurstPool3D pool;
  private ParticleBurstRenderer3D particles;
  private Texture spark;
  private Model actorModel, viewModel;
  private ModelInstance hands;
  private Camera3D worldCamera;
  private CameraRig3D rig;
  private PresentationEffects3D actorEffects, cameraEffects;
  private Presentation3D actorPresentation;
  private CameraPresentation3D worldCameraPresentation, viewCameraPresentation;
  private SpriteBatch hud;
  private BitmapFont font;
  private WorldPostProcess.ControlLease flash;
  private float flashTime, walkPhase, authoredFov = 58;
  private boolean bobEnabled = true, postEnabled = true;

  @Override
  public void create() {
    if (Gdx.gl30 == null)
      throw new IllegalStateException("Feel3DDemo requires an OpenGL 3.0 context");
    game = new Game();
    renderer = new GdxRenderer();
    int width = Math.max(1, Gdx.graphics.getWidth()),
        height = Math.max(1, Gdx.graphics.getHeight());
    renderer.resize(width, height);
    actorModel =
        new ModelBuilder()
            .createBox(
                .9f,
                1.2f,
                .9f,
                new Material(ColorAttribute.createDiffuse(.08f, .8f, .16f, 1)),
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                    | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
    viewModel =
        new ModelBuilder()
            .createBox(
                .32f,
                .25f,
                .4f,
                new Material(ColorAttribute.createDiffuse(.85f, .14f, .04f, 1)),
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                    | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
    hands = new ModelInstance(viewModel);
    hands.transform.setToTranslation(.45f, -.3f, -1.5f);
    GameObject view = game.createObject("view");
    worldCamera = view.addComponent(new Camera3D(authoredFov));
    worldCameraPresentation =
        view.addComponent(new CameraPresentation3D(CameraPresentation3D.Space.WORLD));
    viewCameraPresentation =
        view.addComponent(new CameraPresentation3D(CameraPresentation3D.Space.VIEWMODEL));
    cameraEffects = view.addComponent(new PresentationEffects3D(Clock.WORLD));
    GameObject actor = game.createObject("practice target");
    actor.addComponent(new Transform3D());
    actor.addComponent(new ModelRenderer(actorModel));
    actorPresentation = actor.addComponent(new Presentation3D());
    actorEffects = actor.addComponent(new PresentationEffects3D(Clock.WORLD));
    pool = game.addSystem(new ParticleBurstPool3D(Clock.WORLD, 512, 24));
    game.start();
    game.step(0);
    particles = new ParticleBurstRenderer3D(512);
    post = new WorldPostProcess(width, height);
    spark = createSpark();
    rig =
        new CameraRig3D(
            worldCamera, new PerspectiveCamera(72, width, height), (from, to, radius, owner) -> 1f);
    rig.setRadius(.06f);
    rig.setWorldProjection(authoredFov, .1f, 80);
    rig.resize(width, height);
    hud = new SpriteBatch();
    font = new BitmapFont();
    font.setColor(Color.WHITE);
  }

  @Override
  public void render() {
    float dt = Math.min(Gdx.graphics.getDeltaTime(), .1f);
    updateControls(dt);
    game.step(dt);
    updateCamera();
    post.renderWorld(
        () -> {
          Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
          Gdx.gl.glClearColor(.025f, .035f, .06f, 1);
          Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
          renderer.renderWorld(game, worldCamera, null);
          particles.draw(
              pool,
              worldCamera.camera,
              spark,
              0,
              0,
              Gdx.graphics.getWidth(),
              Gdx.graphics.getHeight());
        });
    renderer.renderViewmodel(rig.viewmodelCamera(), hands);
    drawHud();
  }

  @Override
  public void resize(int width, int height) {
    if (width <= 0 || height <= 0 || renderer == null) return;
    renderer.resize(width, height);
    rig.resize(width, height);
    post.resize(width, height);
  }

  @Override
  public void dispose() {
    if (flash != null) {
      flash.close();
      flash = null;
    }
    if (particles != null) particles.close();
    if (post != null) post.dispose();
    if (game != null) game.close();
    if (renderer != null) renderer.dispose();
    if (actorModel != null) actorModel.dispose();
    if (viewModel != null) viewModel.dispose();
    if (spark != null) spark.dispose();
    if (font != null) font.dispose();
    if (hud != null) hud.dispose();
  }

  private void updateControls(float dt) {
    float move =
        (Gdx.input.isKeyPressed(Input.Keys.W) ? 1 : 0)
            - (Gdx.input.isKeyPressed(Input.Keys.S) ? 1 : 0);
    float strafe =
        (Gdx.input.isKeyPressed(Input.Keys.D) ? 1 : 0)
            - (Gdx.input.isKeyPressed(Input.Keys.A) ? 1 : 0);
    if (move != 0 || strafe != 0) {
      eye.x += strafe * dt * 2.5f;
      eye.z += move * dt * 2.5f;
      walkPhase += (float) Math.hypot(move, strafe) * dt * 5;
    }
    if (Gdx.input.isKeyJustPressed(Input.Keys.B)) bobEnabled = !bobEnabled;
    if (Gdx.input.isKeyJustPressed(Input.Keys.P)) postEnabled = !postEnabled;
    if (Gdx.input.isKeyJustPressed(Input.Keys.PLUS)
        || Gdx.input.isKeyJustPressed(Input.Keys.EQUALS))
      authoredFov = Math.min(110, authoredFov + 2);
    if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS)) authoredFov = Math.max(35, authoredFov - 2);
    rig.setWorldProjection(authoredFov, .1f, 80);
    if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
        || Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) fire();
    if (bobEnabled) {
      // Persistent presentation is still a finite lease; refresh before expiry while keeping phase
      // caller-owned.
      if (bob == null || !bob.isActive())
        bob =
            cameraEffects.play(
                PresentationEffects3D.distanceBob(
                    PresentationEffects3D.Surface.VIEWMODEL_CAMERA, 0, .12, 0, 3600));
      bob.setDistanceBobInput(
          Math.min(1, (Math.abs(move) + Math.abs(strafe)) * .5), walkPhase * Math.PI * 2);
    } else if (bob != null) {
      bob.cancel();
      bob = null;
    }
    if (flash != null) {
      flashTime = Math.max(0, flashTime - dt);
      flash.setIntensity(Math.min(1, flashTime / .28f));
      if (flashTime == 0) {
        flash.close();
        flash = null;
      }
    }
    var controls = post.controls();
    controls.setBaseDesaturation(postEnabled ? 0 : 1);
    controls.setBaseVignette(postEnabled ? .18f : 0);
  }

  private PresentationEffects3D.Playback bob;

  private void fire() {
    actorEffects.play(PresentationEffects3D.punchScale(.25, .25, .25, 3, .8, .45));
    cameraEffects.play(
        PresentationEffects3D.recoil(
            PresentationEffects3D.Surface.VIEWMODEL_CAMERA, .16, .025, 0, .1, .05, 0, 4, .8, .55));
    cameraEffects.play(
        PresentationEffects3D.shake(
            PresentationEffects3D.Surface.WORLD_CAMERA,
            .035,
            .025,
            .015,
            .008,
            .01,
            .006,
            .3,
            16,
            System.nanoTime()));
    cameraEffects.play(
        PresentationEffects3D.fovKick(PresentationEffects3D.Surface.WORLD_CAMERA, 2.5, 3, .8, .35));
    target.set(eye).mulAdd(look, 3);
    pool.burst(
        new ParticleBurstSpec3D(
            target,
            new Vector3(-.8f, -.5f, -.5f),
            new Vector3(.8f, .9f, .5f),
            new Vector3(0, -1.2f, 0),
            .7f,
            .22f,
            new Color(1, .38f, .08f, 1),
            24,
            System.nanoTime()));
    if (flash != null) flash.close();
    flash = post.controls().acquire();
    flash.setTint(1, .65f, .25f);
    flash.setDesaturation(.3f);
    flash.setVignette(.45f);
    flash.setIntensity(1);
    flashTime = .28f;
  }

  private void updateCamera() {
    look.set(-eye.x, -.1f, -eye.z).nor();
    rig.update(eye, look, null, worldCameraPresentation, viewCameraPresentation);
  }

  private void drawHud() {
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    hud.setProjectionMatrix(renderer.camera2D.combined);
    hud.begin();
    font.draw(
        hud,
        "Feel3D  |  WASD move  SPACE/click fire  B bob  P postprocess  +/- authored FOV",
        18,
        Gdx.graphics.getHeight() - 18);
    font.draw(
        hud,
        String.format(
            java.util.Locale.ROOT,
            "FOV %.1f°  presented %.1f°  particles %d  walk phase %.2f",
            authoredFov,
            rig.presentedWorldFov(),
            pool.getActiveParticleCount(),
            walkPhase),
        18,
        Gdx.graphics.getHeight() - 42);
    hud.end();
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
  }

  private Texture createSpark() {
    Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
    p.setBlending(Pixmap.Blending.None);
    for (int y = 0; y < 16; y++)
      for (int x = 0; x < 16; x++) {
        float dx = (x + .5f - 8) / 8f, dy = (y + .5f - 8) / 8f;
        p.setColor(
            1, 1, 1, Math.max(0, Math.min(1, (1 - (float) Math.sqrt(dx * dx + dy * dy)) * 5)));
        p.drawPixel(x, y);
      }
    Texture t = new Texture(p);
    p.dispose();
    t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    return t;
  }
}
