package org.javity.android;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.javity.render.*;

/**
 * Small authored GLES300 fixture, retained with its raw batch owners across real EGL replacement.
 */
public final class AndroidInstancingContextProbe implements Disposable {
  public record Snapshot(
      long ownerIdentity,
      long recoveries,
      String glVersion,
      String glRenderer,
      int maxVertexTextureUnits,
      int maxTextureSize,
      long skinDraws,
      long staticDraws,
      long paletteUploadBytes,
      long instanceUploadBytes,
      int uniquePalettes,
      int redPixels,
      int greenPixels,
      int bluePixels,
      int wrongRowChangedPixels,
      int maxRgbDelta,
      boolean noGlError) {}

  public record Cleanup(
      boolean ownedNamesDeleted,
      boolean borrowedSourceRenders,
      int trackedNames,
      int redPixels,
      int greenPixels,
      int bluePixels,
      boolean noGlError) {}

  private static final AtomicLong NEXT = new AtomicLong();
  private final long identity = NEXT.incrementAndGet();
  private SkinnedInstanceBatch skin;
  private StaticInstanceBatch rigid;
  private Mesh skinnedMesh, staticMesh;
  private final Array<Renderable> skins = new Array<>(), statics = new Array<>();
  private Renderable ordinaryBlue;
  private final Environment environment = new Environment();
  private final OrthographicCamera camera = new OrthographicCamera(128, 80);
  private FrameBuffer target;
  private final Calls calls = new Calls();
  private ModelBatch ordinary;
  private long actualSkinDraws, actualStaticDraws;
  private long recoveries;
  private boolean closed;

  AndroidInstancingContextProbe() {
    try {
      skin = new SkinnedInstanceBatch(256, 16);
      rigid = new StaticInstanceBatch(256);
      skinnedMesh = quad(true);
      staticMesh = quad(false);
      target = new RecoverableFrameBuffer(Pixmap.Format.RGBA8888, 128, 80, true);
      ordinary = createOrdinary();
      environment.set(ColorAttribute.createAmbientLight(1, 1, 1, 1));
      camera.position.set(64, 40, 100);
      camera.lookAt(64, 40, 0);
      camera.near = .1f;
      camera.far = 200;
      camera.update();
      Renderable a = renderable(skinnedMesh, Color.RED, 12, 40),
          b = renderable(skinnedMesh, Color.RED, 52, 40);
      a.bones = new Matrix4[] {new Matrix4(), new Matrix4().setToTranslation(6, 0, 0)};
      b.bones = new Matrix4[] {new Matrix4(), new Matrix4().setToTranslation(-5, 8, 0)};
      skins.add(a);
      skins.add(b);
      statics.add(renderable(staticMesh, Color.GREEN, 90, 45));
      statics.add(renderable(staticMesh, Color.GREEN, 108, 45));
      ordinaryBlue = renderable(staticMesh, Color.BLUE, 98, 12);
    } catch (RuntimeException | Error failure) {
      disposeAfterFailure(failure);
      throw failure;
    }
  }

  private static ModelBatch createOrdinary() {
    var config = new DefaultShader.Config();
    config.numBones = 2;
    config.numBoneWeights = 2;
    return new ModelBatch(new DefaultShaderProvider(config));
  }

  private static Mesh quad(boolean bones) {
    VertexAttribute[] attrs =
        bones
            ? new VertexAttribute[] {
              VertexAttribute.Position(),
              VertexAttribute.Normal(),
              VertexAttribute.BoneWeight(0),
              VertexAttribute.BoneWeight(1)
            }
            : new VertexAttribute[] {VertexAttribute.Position(), VertexAttribute.Normal()};
    Mesh mesh = new Mesh(true, 4, 6, attrs);
    float[] vertices = new float[bones ? 40 : 24];
    for (int i = 0; i < 4; i++) {
      int stride = bones ? 10 : 6, at = i * stride;
      vertices[at] = (i == 1 || i == 2) ? 12 : 0;
      vertices[at + 1] = i >= 2 ? 18 : 0;
      vertices[at + 5] = 1;
      if (bones) {
        vertices[at + 6] = 0;
        vertices[at + 7] = i < 2 ? 1 : 0;
        vertices[at + 8] = 1;
        vertices[at + 9] = i >= 2 ? 1 : 0;
      }
    }
    try {
      mesh.setVertices(vertices);
      mesh.setIndices(new short[] {0, 1, 2, 2, 3, 0});
      return mesh;
    } catch (RuntimeException | Error failure) {
      mesh.dispose();
      throw failure;
    }
  }

  private Renderable renderable(Mesh mesh, Color color, float x, float y) {
    Renderable r = new Renderable();
    r.meshPart.set("quad", mesh, 0, 6, GL20.GL_TRIANGLES);
    r.material = new Material(ColorAttribute.createDiffuse(color));
    r.worldTransform.setToTranslation(x, y, 0);
    r.environment = environment;
    return r;
  }

  void contextRestored() {
    if (closed) return;
    skin.contextRestored();
    rigid.contextRestored();
    calls.clear();
    ordinary.dispose();
    ordinary = createOrdinary();
    recoveries++;
  }

  Snapshot snapshot() {
    if (closed) throw new IllegalStateException("closed instancing probe");
    Pixmap reference = null, actual = null, wrong = null;
    try {
      reference = draw(false);
      actual = draw(true);
      long palette = calls.palette, instance = calls.instance;
      int delta = 0, red = 0, green = 0, blue = 0;
      for (int y = 0; y < 80; y++)
        for (int x = 0; x < 128; x++) {
          int a = reference.getPixel(x, y), b = actual.getPixel(x, y);
          for (int shift : new int[] {8, 16, 24})
            delta = Math.max(delta, Math.abs((a >>> shift & 255) - (b >>> shift & 255)));
          int r = b >>> 24 & 255, g = b >>> 16 & 255, bl = b >>> 8 & 255;
          if (r > 180 && g < 50 && bl < 50) red++;
          if (g > 180 && r < 50 && bl < 50) green++;
          if (bl > 180 && r < 50 && g < 50) blue++;
        }
      // Ordinary wrong-row control is independent of the optimized shader and must visibly differ.
      Matrix4[] correct = skins.get(1).bones;
      skins.get(1).bones = skins.get(0).bones;
      try {
        wrong = draw(false);
      } finally {
        skins.get(1).bones = correct;
      }
      int changed = 0;
      for (int y = 0; y < 80; y++)
        for (int x = 0; x < 128; x++)
          if (reference.getPixel(x, y) != wrong.getPixel(x, y)) changed++;
      return new Snapshot(
          identity,
          recoveries,
          Gdx.gl.glGetString(GL20.GL_VERSION),
          Gdx.gl.glGetString(GL20.GL_RENDERER),
          limit(GL20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS),
          limit(GL20.GL_MAX_TEXTURE_SIZE),
          actualSkinDraws,
          actualStaticDraws,
          palette,
          instance,
          uniquePalettes(),
          red,
          green,
          blue,
          changed,
          delta,
          Gdx.gl.glGetError() == GL20.GL_NO_ERROR);
    } finally {
      if (reference != null) reference.dispose();
      if (actual != null) actual.dispose();
      if (wrong != null) wrong.dispose();
    }
  }

  private int uniquePalettes() {
    Set<String> keys = new HashSet<>();
    for (Renderable r : skins) {
      StringBuilder key = new StringBuilder();
      for (Matrix4 m : r.bones)
        for (float f : m.val) key.append(Float.floatToIntBits(f)).append(',');
      keys.add(key.toString());
    }
    return keys.size();
  }

  private Pixmap draw(boolean optimized) {
    for (Renderable r : skins) r.shader = null;
    for (Renderable r : statics) r.shader = null;
    ordinaryBlue.shader = null;
    var viewport = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(4);
    Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewport);
    int framebuffer = limit(0x8CA6),
        pack = limit(GL20.GL_PACK_ALIGNMENT),
        active = limit(GL20.GL_ACTIVE_TEXTURE);
    var clear = com.badlogic.gdx.utils.BufferUtils.newFloatBuffer(4);
    Gdx.gl.glGetFloatv(GL20.GL_COLOR_CLEAR_VALUE, clear);
    boolean depth = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST),
        mask = limit(GL20.GL_DEPTH_WRITEMASK) != 0;
    target.begin();
    try {
      Gdx.gl.glViewport(0, 0, 128, 80);
      Gdx.gl.glClearColor(0, 0, 0, 1);
      Gdx.gl.glDepthMask(true);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      Array<Renderable> a = skins, b = statics;
      if (optimized) {
        calls.install();
        try {
          a = skin.render(skins, camera, environment);
          actualSkinDraws = calls.draws;
          b = rigid.render(statics, camera, environment);
          actualStaticDraws = calls.draws - actualSkinDraws;
        } finally {
          calls.uninstall();
        }
        if (a.notEmpty() || b.notEmpty())
          throw new AssertionError(
              "GLES instancing fallback " + skin.fallbackReasons() + rigid.fallbackReasons());
      }
      ordinary.begin(camera);
      try {
        for (Renderable r : a) ordinary.render(r);
        for (Renderable r : b) ordinary.render(r);
        ordinary.render(ordinaryBlue);
      } finally {
        ordinary.end();
      }
      return Pixmap.createFromFrameBuffer(0, 0, 128, 80);
    } finally {
      try {
        target.end();
      } finally {
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer);
        Gdx.gl.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
        Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, pack);
        Gdx.gl.glActiveTexture(active);
        Gdx.gl.glClearColor(clear.get(0), clear.get(1), clear.get(2), clear.get(3));
        Gdx.gl.glDepthMask(mask);
        if (depth) Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        else Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
      }
    }
  }

  Cleanup closeAndVerify() {
    int names =
        calls.buffers.size() + calls.programs.size() + calls.textures.size() + calls.shaders.size();
    skin.dispose();
    rigid.dispose();
    boolean deleted = names > 0;
    for (int id : calls.buffers) deleted &= !Gdx.gl.glIsBuffer(id);
    for (int id : calls.programs) deleted &= !Gdx.gl.glIsProgram(id);
    for (int id : calls.textures) deleted &= !Gdx.gl.glIsTexture(id);
    for (int id : calls.shaders) deleted &= !Gdx.gl.glIsShader(id);
    Pixmap borrowed = draw(false);
    int red = 0, green = 0, blue = 0;
    try {
      for (int y = 0; y < 80; y++)
        for (int x = 0; x < 128; x++) {
          int color = borrowed.getPixel(x, y),
              r = color >>> 24 & 255,
              g = color >>> 16 & 255,
              b = color >>> 8 & 255;
          if (r > 180 && g < 50 && b < 50) red++;
          if (g > 180 && r < 50 && b < 50) green++;
          if (b > 180 && r < 50 && g < 50) blue++;
        }
    } finally {
      borrowed.dispose();
    }
    boolean noError = Gdx.gl.glGetError() == GL20.GL_NO_ERROR;
    boolean renders = red > 400 && green > 300 && blue > 150 && noError;
    dispose();
    noError &= Gdx.gl.glGetError() == GL20.GL_NO_ERROR;
    return new Cleanup(deleted, renders && noError, names, red, green, blue, noError);
  }

  private static int limit(int name) {
    var value = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(1);
    Gdx.gl.glGetIntegerv(name, value);
    return value.get(0);
  }

  public void dispose() {
    if (closed) return;
    closed = true;
    Throwable failure = null;
    for (Disposable resource :
        new Disposable[] {skin, rigid, ordinary, target, skinnedMesh, staticMesh})
      if (resource != null)
        try {
          resource.dispose();
        } catch (RuntimeException | Error e) {
          if (failure == null) failure = e;
          else failure.addSuppressed(e);
        }
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }

  private void disposeAfterFailure(Throwable failure) {
    try {
      dispose();
    } catch (RuntimeException | Error cleanup) {
      failure.addSuppressed(cleanup);
    }
  }

  private static final class Calls implements InvocationHandler {
    GL20 old, old20;
    GL30 old30;
    long palette, instance, draws;
    int boundArray;
    final Set<Integer> dynamicBuffers = new HashSet<>(), shaders = new HashSet<>();
    final Set<Integer> buffers = new HashSet<>(),
        programs = new HashSet<>(),
        textures = new HashSet<>();

    void clear() {
      buffers.clear();
      programs.clear();
      textures.clear();
      dynamicBuffers.clear();
      shaders.clear();
    }

    void install() {
      old = Gdx.gl;
      old20 = Gdx.gl20;
      old30 = Gdx.gl30;
      palette = instance = draws = 0;
      GL30 proxy =
          (GL30)
              Proxy.newProxyInstance(GL30.class.getClassLoader(), new Class[] {GL30.class}, this);
      Gdx.gl = proxy;
      Gdx.gl20 = proxy;
      Gdx.gl30 = proxy;
    }

    void uninstall() {
      Gdx.gl = old;
      Gdx.gl20 = old20;
      Gdx.gl30 = old30;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String name = method.getName();
      if (name.equals("glDrawElementsInstanced") || name.equals("glDrawArraysInstanced")) {
        // Managed source copies may receive new native buffers before this probe's explicit
        // restoration hook. Capture the actual owned VBO/IBO at draw as well as new allocations.
        var value = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(1);
        old20.glGetIntegerv(GL20.GL_CURRENT_PROGRAM, value);
        int program = value.get(0);
        for (String attribute : new String[] {"a_position", "a_i0"}) {
          int location = old20.glGetAttribLocation(program, attribute);
          old20.glGetVertexAttribiv(location, 0x889F, value);
          int buffer = value.get(0);
          if (buffer > 0) buffers.add(buffer);
        }
        old20.glGetIntegerv(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING, value);
        if (value.get(0) > 0) buffers.add(value.get(0));
      }
      Object result;
      try {
        result = method.invoke(old30, args);
      } catch (InvocationTargetException failure) {
        throw failure.getCause();
      }
      if (name.equals("glDrawElementsInstanced") || name.equals("glDrawArraysInstanced")) draws++;
      if (name.equals("glTexSubImage2D") && (Integer) args[7] == GL20.GL_FLOAT)
        palette += (long) (Integer) args[4] * (Integer) args[5] * 16;
      if (name.equals("glBindBuffer") && (Integer) args[0] == GL20.GL_ARRAY_BUFFER)
        boundArray = (Integer) args[1];
      if (name.equals("glBufferData")
          && (Integer) args[0] == GL20.GL_ARRAY_BUFFER
          && (Integer) args[3] == GL20.GL_DYNAMIC_DRAW) dynamicBuffers.add(boundArray);
      if (name.equals("glBufferSubData")
          && (Integer) args[0] == GL20.GL_ARRAY_BUFFER
          && dynamicBuffers.contains(boundArray)) instance += ((Number) args[2]).longValue();
      if (name.equals("glCreateShader")) shaders.add((Integer) result);
      if (name.equals("glDeleteShader")) shaders.remove((Integer) args[0]);
      if (name.equals("glGenBuffer")) buffers.add((Integer) result);
      if (name.equals("glDeleteBuffer")) {
        buffers.remove((Integer) args[0]);
        dynamicBuffers.remove((Integer) args[0]);
      }
      if (name.equals("glCreateProgram")) programs.add((Integer) result);
      if (name.equals("glDeleteProgram")) programs.remove((Integer) args[0]);
      if (name.equals("glGenTexture")) textures.add((Integer) result);
      if (name.equals("glDeleteTexture")) textures.remove((Integer) args[0]);
      return result;
    }
  }
}
