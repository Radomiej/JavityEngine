package org.javity.wasm;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import org.javity.core.Component;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.render.Camera3D;
import org.javity.render.GdxRenderer;
import org.javity.render.ModelRenderer;
import org.teavm.jso.JSBody;

/** Actual Javity lifecycle and renderer on the libGDX TeaVM backend. */
public final class JavityWasmDemo extends ApplicationAdapter {
  private static final long VERTEX_ATTRIBUTES =
      VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
  private Game game;
  private GdxRenderer renderer;
  private Camera3D view;
  private Transform3D player;
  private Model cube, sphere, ground;
  private boolean disposed;
  private long rendered;
  private int firstGlError;

  @Override
  public void create() {
    try {
      createScene();
    } catch (RuntimeException | Error failure) {
      disposed = true;
      releaseScene(failure);
    }
  }

  private void createScene() {
    if (Gdx.gl30 == null) {
      throw new IllegalStateException("This Javity Wasm spike requires WebGL2 (GL30)");
    }
    game = new Game();
    renderer = new GdxRenderer();
    ModelBuilder builder = new ModelBuilder();
    cube = builder.createBox(1, 1, 1,
        new Material(ColorAttribute.createDiffuse(.12f, .75f, .62f, 1)), VERTEX_ATTRIBUTES);
    sphere = builder.createSphere(1.3f, 1.3f, 1.3f, 20, 16,
        new Material(ColorAttribute.createDiffuse(1, .55f, .12f, 1)), VERTEX_ATTRIBUTES);
    ground = builder.createBox(24, .3f, 24,
        new Material(ColorAttribute.createDiffuse(.22f, .31f, .4f, 1)), VERTEX_ATTRIBUTES);
    model("ground", ground, 0, -.2f, 0);
    player = model("player", cube, 0, .5f, 0).getComponent(Transform3D.class);
    player.getGameObject().addComponent(new KeyboardMovement(player));
    Transform3D orb = model("animated sphere", sphere, -3, 1.7f, 0)
        .getComponent(Transform3D.class);
    orb.getGameObject().addComponent(new Hover(orb));
    for (int i = 0; i < 6; i++) {
      Transform3D column = model("column-" + i, cube, -5 + i * 2, .75f, -4)
          .getComponent(Transform3D.class);
      column.scale.set(.6f, 1.5f + (i % 3) * .7f, .6f);
    }
    view = game.createObject("camera").addComponent(new Camera3D(60));
    view.camera.position.set(11, 10, 15);
    view.camera.lookAt(0, 0, 0);
    view.camera.near = .1f;
    view.camera.far = 80;
    game.start();
    if (OrbitBrowser.available()) {
      OrbitBrowser.request("player.get", "{}", json -> {
        if (!disposed) OrbitBrowser.reportJavaPlayer(json);
      }, error -> { /* The standalone renderer does not depend on an ORBIT session. */ });
    }
    resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
  }

  private GameObject model(String name, Model resource, float x, float y, float z) {
    GameObject object = game.createObject(name);
    object.addComponent(new Transform3D()).position.set(x, y, z);
    object.addComponent(new ModelRenderer(resource));
    return object;
  }

  @Override
  public void resize(int width, int height) {
    if (renderer == null || disposed || width < 1 || height < 1) return;
    renderer.resize(width, height);
    view.camera.viewportWidth = width;
    view.camera.viewportHeight = height;
    view.camera.update();
  }

  @Override
  public void render() {
    if (disposed) return;
    if (stopRequested()) {
      dispose();
      return;
    }
    OrbitBrowser.Restore restore = OrbitBrowser.takeRestore();
    if (restore != null) player.position.set(restore.getX(), player.position.y, restore.getZ());
    game.step(Math.min(Gdx.graphics.getDeltaTime(), .05f));
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(.12f, .18f, .28f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    renderer.render(game);
    rendered++;
    captureGlError();
    report(rendered, game.getFrameIndex(), player.position.x, player.position.z,
        Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), Gdx.gl30 != null, firstGlError, false);
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    OrbitBrowser.dispose();
    releaseScene(null);
    captureGlError();
    report(rendered, -1, 0, 0, 0, 0, Gdx.gl30 != null, firstGlError, true);
  }

  private void releaseScene(Throwable failure) {
    try {
      SceneCleanup.close(failure,
          game == null ? null : game::close,
          renderer == null ? null : renderer::dispose,
          cube == null ? null : cube::dispose,
          sphere == null ? null : sphere::dispose,
          ground == null ? null : ground::dispose);
    } finally {
      game = null;
      renderer = null;
      cube = sphere = ground = null;
      player = null;
      view = null;
    }
  }

  private void captureGlError() {
    if (firstGlError == 0) firstGlError = Gdx.gl.glGetError();
  }

  private static final class KeyboardMovement extends Component {
    private final Transform3D transform;
    KeyboardMovement(Transform3D transform) { this.transform = transform; }
    @Override
    public void onUpdate(float delta) {
      float x = (pressed(Input.Keys.D, Input.Keys.RIGHT) ? 1 : 0)
          - (pressed(Input.Keys.A, Input.Keys.LEFT) ? 1 : 0);
      float z = (pressed(Input.Keys.S, Input.Keys.DOWN) ? 1 : 0)
          - (pressed(Input.Keys.W, Input.Keys.UP) ? 1 : 0);
      float length = (float) Math.sqrt(x * x + z * z);
      if (length == 0) return;
      transform.position.x = Math.max(-10, Math.min(10, transform.position.x + x / length * delta * 4));
      transform.position.z = Math.max(-10, Math.min(10, transform.position.z + z / length * delta * 4));
    }
    private static boolean pressed(int a, int b) {
      return Gdx.input.isKeyPressed(a) || Gdx.input.isKeyPressed(b);
    }
  }

  private static final class Hover extends Component {
    private final Transform3D transform;
    private float time;
    Hover(Transform3D transform) { this.transform = transform; }
    @Override
    public void onUpdate(float delta) {
      time += delta;
      transform.position.y = 1.6f + (float) Math.sin(time * 2) * .6f;
    }
  }

  @JSBody(script = "return globalThis.javityWasmStop === true;")
  private static native boolean stopRequested();

  @JSBody(params = {"rendered", "frame", "x", "z", "width", "height", "gl30", "error", "disposed"},
      script = "globalThis.javityWasm={renderer:'org.javity.render.GdxRenderer',backend:'gdx-teavm',rendered:rendered,frame:frame,x:x,z:z,width:width,height:height,webgl2:gl30,glError:error,disposed:disposed};")
  private static native void report(double rendered, double frame, float x, float z,
      int width, int height, boolean gl30, int error, boolean disposed);
}
