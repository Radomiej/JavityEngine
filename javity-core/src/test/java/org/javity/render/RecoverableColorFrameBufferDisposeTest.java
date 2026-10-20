package org.javity.render;

import static org.junit.Assert.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

public class RecoverableColorFrameBufferDisposeTest {
  private final Application previousApp = Gdx.app;
  private final Graphics previousGraphics = Gdx.graphics;
  private final GL20 previousGl = Gdx.gl, previousGl20 = Gdx.gl20;
  private final GL30 previousGl30 = Gdx.gl30;

  @After
  public void restore() {
    if (Gdx.app != previousApp) GLFrameBuffer.clearAllFrameBuffers(Gdx.app);
    Gdx.app = previousApp;
    Gdx.graphics = previousGraphics;
    Gdx.gl = previousGl;
    Gdx.gl20 = previousGl20;
    Gdx.gl30 = previousGl30;
  }

  @Test
  public void everyDeleteFailureRetiresOwnershipAndContinuesRemainingCleanup() {
    for (String failingDelete :
        new String[] {"glDeleteTexture", "glDeleteRenderbuffer", "glDeleteFramebuffer"}) {
      Rig rig = new Rig();
      rig.owner = new Probe();
      Texture color = rig.owner.getColorBufferTexture();
      int texture = color.getTextureObjectHandle(),
          depth = rig.owner.getDepthBufferHandle(),
          frame = rig.owner.getFramebufferHandle();
      RuntimeException sentinel = new RuntimeException(failingDelete);
      rig.failures.put(failingDelete, sentinel);
      assertSame(sentinel, assertThrows(RuntimeException.class, rig.owner::dispose));
      assertEquals(1, rig.count("glDeleteTexture", texture));
      assertEquals(1, rig.count("glDeleteRenderbuffer", depth));
      assertEquals(1, rig.count("glDeleteFramebuffer", frame));
      assertEquals(0, color.getTextureObjectHandle());
      int deleted = rig.deletions.size();
      // The old names now belong to unrelated resources. Neither repeated owner nor wrapper
      // disposal may touch them.
      rig.forced.put("glGenTexture", texture);
      rig.forced.put("glGenRenderbuffer", depth);
      rig.forced.put("glGenFramebuffer", frame);
      assertEquals(texture, rig.gl.glGenTexture());
      assertEquals(depth, rig.gl.glGenRenderbuffer());
      assertEquals(frame, rig.gl.glGenFramebuffer());
      rig.owner.dispose();
      color.dispose();
      assertEquals(deleted, rig.deletions.size());
      int allocations = rig.allocations;
      assertThrows(IllegalStateException.class, rig.owner::rebuild);
      assertEquals(allocations, rig.allocations);
    }
  }

  @Test
  public void cleanupPreservesFirstFailureAndOnlyDifferentSuppressedErrors() {
    for (boolean repeatPrimary : new boolean[] {true, false}) {
      Rig rig = new Rig();
      rig.owner = new Probe();
      RuntimeException first = new RuntimeException("texture observer");
      Error last = new AssertionError("distinct cleanup observer");
      rig.failures.put("glDeleteTexture", first);
      rig.failures.put("glDeleteRenderbuffer", repeatPrimary ? first : last);
      rig.failures.put("glDeleteFramebuffer", last);
      assertSame(first, assertThrows(RuntimeException.class, rig.owner::dispose));
      assertArrayEquals(new Throwable[] {last}, first.getSuppressed());
      assertEquals(3, rig.deletions.size());
      rig.owner.dispose();
      assertEquals(3, rig.deletions.size());
    }
  }

  private static final class Probe extends RecoverableColorFrameBuffer {
    Probe() {
      super(builder());
    }

    private static FrameBufferBuilder builder() {
      FrameBufferBuilder b = new FrameBufferBuilder(16, 16);
      b.addBasicColorTextureAttachment(Pixmap.Format.RGBA8888);
      b.addBasicDepthRenderBuffer();
      return b;
    }

    void rebuild() {
      build();
    }
  }

  private static final class Rig {
    Probe owner;
    int next = 10, allocations;
    final Map<String, Throwable> failures = new HashMap<>();
    final Map<String, Integer> forced = new HashMap<>();
    final List<String> deletions = new ArrayList<>();
    final GL20 gl;

    Rig() {
      Application app =
          (Application)
              Proxy.newProxyInstance(
                  Application.class.getClassLoader(),
                  new Class<?>[] {Application.class},
                  (proxy, method, args) ->
                      method.getName().equals("getType")
                          ? Application.ApplicationType.Desktop
                          : defaultValue(method.getReturnType()));
      Graphics graphics =
          (Graphics)
              Proxy.newProxyInstance(
                  Graphics.class.getClassLoader(),
                  new Class<?>[] {Graphics.class},
                  (proxy, method, args) -> defaultValue(method.getReturnType()));
      gl =
          (GL20)
              Proxy.newProxyInstance(
                  GL20.class.getClassLoader(),
                  new Class<?>[] {GL20.class},
                  (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("glGenTexture")
                        || name.equals("glGenRenderbuffer")
                        || name.equals("glGenFramebuffer")) {
                      allocations++;
                      Integer reused = forced.remove(name);
                      return reused == null ? ++next : reused;
                    }
                    if (name.equals("glCheckFramebufferStatus"))
                      return GL20.GL_FRAMEBUFFER_COMPLETE;
                    if (name.equals("glDeleteTexture")
                        || name.equals("glDeleteRenderbuffer")
                        || name.equals("glDeleteFramebuffer")) {
                      int handle = (Integer) args[0];
                      if (handle == 0) return null;
                      assertEquals(0, owner.getFramebufferHandle());
                      assertEquals(0, owner.getDepthBufferHandle());
                      assertEquals(0, owner.getTextureAttachments().size);
                      assertEquals(
                          0,
                          RecoverableColorFrameBuffer.managedFramebufferIdentityCount(
                              Gdx.app, owner));
                      deletions.add(name + ":" + handle);
                      Throwable failure = failures.get(name);
                      if (failure != null) throw failure;
                    }
                    return defaultValue(method.getReturnType());
                  });
      Gdx.app = app;
      Gdx.graphics = graphics;
      Gdx.gl = gl;
      Gdx.gl20 = gl;
      Gdx.gl30 = null;
    }

    int count(String kind, int id) {
      int count = 0;
      for (String deletion : deletions) if (deletion.equals(kind + ":" + id)) count++;
      return count;
    }
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive() || type == void.class) return null;
    if (type == boolean.class) return false;
    if (type == int.class) return 0;
    if (type == float.class) return 0f;
    if (type == long.class) return 0L;
    if (type == double.class) return 0d;
    if (type == byte.class) return (byte) 0;
    if (type == short.class) return (short) 0;
    if (type == char.class) return '\0';
    throw new AssertionError(type);
  }
}
