package org.javity.render;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.utils.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs actual libGDX 1.14.2 FrameBuffer code against deterministic GL calls; no Android/Gradle. */
public final class RecoverableFrameBufferTest {
  private final Application previousApp = Gdx.app;
  private final Graphics previousGraphics = Gdx.graphics;
  private final GL20 previousGl = Gdx.gl;
  private final GL20 previousGl20 = Gdx.gl20;
  private final com.badlogic.gdx.graphics.GL30 previousGl30 = Gdx.gl30;

  @org.junit.After
  public void restoreGlobals() {
    try {
      if (Gdx.app != previousApp) {
        for (GLFrameBuffer<?> buffer : RecoverableFrameBuffer.managedSnapshot(Gdx.app))
          buffer.dispose();
        GLFrameBuffer.clearAllFrameBuffers(Gdx.app);
      }
    } finally {
      Gdx.app = previousApp;
      Gdx.graphics = previousGraphics;
      Gdx.gl = previousGl;
      Gdx.gl20 = previousGl20;
      Gdx.gl30 = previousGl30;
    }
  }

  private static final int FRAMEBUFFER_COMPLETE = 0x8CD5;
  private static final int FRAMEBUFFER_INCOMPLETE_ATTACHMENT = 0x8CD6;

  @org.junit.Test
  public void rebuildsEachOwnerOnceAndDoesNotDeleteReusedNamesAfterFailure() {
    Rig rig = new Rig();
    successfulMultiOwnerInvalidation(rig);
    failedRebuildDoesNotDeleteReusedNames(rig);
    failedRebuildCanRetry(rig);
    System.out.println(
        "PASS: managed FrameBuffer success, failed-dispose ABA, failed-retry, "
            + "depth attachment and registry invariants verified against libGDX 1.14.2.");
  }

  @org.junit.Test
  public void unexpectedAllocationExceptionsDeleteEveryNewOwnedTokenExactlyOnce() {
    for (String stage :
        new String[] {
          "glBindFramebuffer",
          "glRenderbufferStorage",
          "glTexImage2D",
          "glTexParameteri",
          "glFramebufferTexture2D",
          "glFramebufferRenderbuffer",
          "glCheckFramebufferStatus"
        }) {
      Rig rig = new Rig();
      RuntimeException original = new RuntimeException("injected " + stage);
      rig.throwAt = stage;
      rig.allocationFailure = original;
      try {
        rig.newFramebuffer(32, 24, true);
        throw new AssertionError("allocation unexpectedly succeeded at " + stage);
      } catch (RuntimeException observed) {
        require(observed == original, "original allocation failure is retained");
      }
      for (int handle : rig.generatedTextures)
        require(
            rig.count(rig.deletedTextures, handle) == 1,
            stage + " deletes new texture exactly once");
      for (int handle : rig.generatedRenderbuffers)
        require(
            rig.count(rig.deletedRenderbuffers, handle) == 1,
            stage + " deletes new renderbuffer exactly once");
      for (int handle : rig.generatedFramebuffers)
        require(
            rig.count(rig.deletedFramebuffers, handle) == 1,
            stage + " deletes new framebuffer exactly once");
      require(
          RecoverableFrameBuffer.managedSnapshot(rig.app).size == 0,
          stage + " registers no failed owner");
    }
  }

  @org.junit.Test
  public void unexpectedRebuildFailureDoesNotDeleteDestroyedContextOrReusedNames() {
    Rig rig = new Rig();
    RecoverableFrameBuffer owner = rig.newFramebuffer(32, 24, true);
    int oldFbo = owner.getFramebufferHandle(),
        oldTexture = owner.getColorBufferTexture().getTextureObjectHandle();
    int oldDepth = owner.getDepthBufferHandle();
    RuntimeException original = new RuntimeException("check failed during rebuild");
    rig.throwAt = "glCheckFramebufferStatus";
    rig.allocationFailure = original;
    try {
      GLFrameBuffer.invalidateAllFrameBuffers(rig.app);
      throw new AssertionError("rebuild unexpectedly succeeded");
    } catch (RuntimeException observed) {
      require(observed == original, "rebuild preserves original failure");
    }
    require(rig.count(rig.deletedFramebuffers, oldFbo) == 0, "old context FBO name is not deleted");
    require(
        rig.count(rig.deletedTextures, oldTexture) == 0, "old context texture name is not deleted");
    require(
        rig.count(rig.deletedRenderbuffers, oldDepth) == 0,
        "old context depth name is not deleted");
    int failedFbo = rig.lastFramebufferName.get();
    require(
        rig.count(rig.deletedFramebuffers, failedFbo) == 1, "failed rebuild deletes its new FBO");
    rig.forcedFramebufferName.set(failedFbo);
    int sentinel = rig.gl20.glGenFramebuffer();
    int deletes = rig.count(rig.deletedFramebuffers, sentinel);
    owner.dispose();
    require(
        rig.count(rig.deletedFramebuffers, sentinel) == deletes,
        "failed owner cannot delete reused FBO name");
    assertNoOwner(rig, owner);
  }

  private static void successfulMultiOwnerInvalidation(Rig rig) {
    RecoverableFrameBuffer first = rig.newFramebuffer(128, 80, false);
    RecoverableFrameBuffer second = rig.newFramebuffer(32, 24, true);
    Array<GLFrameBuffer> before = RecoverableFrameBuffer.managedSnapshot(rig.app);
    require(
        before.size == 2 && before.get(0) == first && before.get(1) == second,
        "owners register once in order");
    require(
        first.getTextureAttachments().size == 1 && second.getTextureAttachments().size == 1,
        "initial attachments exist");
    Texture oldFirst = first.getColorBufferTexture();
    Texture oldSecond = second.getColorBufferTexture();
    int uploads = rig.textureUploads.get();
    int attaches = rig.colorAttachCalls.get();
    int deletes = rig.nonzeroDeletes();
    rig.allocationCeiling.set(uploads + 2);

    for (int pass = 1; pass <= 4; pass++) {
      GLFrameBuffer.invalidateAllFrameBuffers(rig.app);
      require(
          rig.textureUploads.get() == uploads + 2,
          "pass " + pass + " rebuilds each owner once before allocation guard");
      require(
          rig.colorAttachCalls.get() == attaches + 2,
          "pass " + pass + " attaches one current color texture per owner");
      assertRegistry(rig, first, second, "pass " + pass);
      require(
          first.getTextureAttachments().size == 1 && second.getTextureAttachments().size == 1,
          "pass " + pass + " does not retain stale attachment wrappers");
      require(
          first.getColorBufferTexture() != oldFirst && second.getColorBufferTexture() != oldSecond,
          "pass " + pass + " creates fresh attachment wrappers");
      require(
          rig.nonzeroDeletes() == deletes,
          "pass " + pass + " does not GL-delete stale names from the destroyed context");
      assertLastAttached(rig, first, second, 2, "pass " + pass);
      first.begin();
      first.end();
      second.begin();
      second.end();
      oldFirst = first.getColorBufferTexture();
      oldSecond = second.getColorBufferTexture();
      uploads += 2;
      attaches += 2;
      rig.allocationCeiling.set(uploads + 2);
    }

    first.dispose();
    second.dispose();
    require(rig.nonzeroDeletes() > deletes, "terminal disposal releases current resources");
    assertNoOwner(rig, first);
    assertNoOwner(rig, second);
  }

  private static void failedRebuildDoesNotDeleteReusedNames(Rig rig) {
    RecoverableFrameBuffer failed = rig.newFramebuffer(48, 40, true);
    int textureDeletesBefore = rig.deletedTextures.size();
    int fboDeletesBefore = rig.deletedFramebuffers.size();
    int rbDeletesBefore = rig.deletedRenderbuffers.size();
    rig.failNextFramebufferCheck.set(1);
    expectIncomplete(rig);
    int failedFramebufferName = rig.lastFramebufferName.get();
    require(
        rig.deletedTextures.size() == textureDeletesBefore + 1,
        "libGDX failure branch deletes its partial color texture exactly once");
    require(
        rig.deletedFramebuffers.size() == fboDeletesBefore + 1
            && rig.deletedFramebuffers.get(rig.deletedFramebuffers.size() - 1)
                == failedFramebufferName,
        "libGDX failure branch deletes its partial framebuffer");
    require(
        rig.deletedRenderbuffers.size() == rbDeletesBefore + 1,
        "depth-enabled failed owner deletes its partial renderbuffer");
    require(
        failed.getTextureAttachments().size == 0,
        "failed rebuild exposes no already-deleted attachment wrapper");
    require(
        RecoverableFrameBuffer.managedIdentityCount(rig.app, failed) == 1,
        "failed rebuild keeps the original registry entry only");

    // Force a new resource to reuse the just-deleted FBO name before disposing the failed owner.
    rig.forcedFramebufferName.set(failedFramebufferName);
    int sentinel = rig.gl20.glGenFramebuffer();
    require(
        sentinel == failedFramebufferName, "fake GL reuses the deleted name for a live sentinel");
    int deletesOfSentinelBeforeDispose = rig.count(rig.deletedFramebuffers, sentinel);
    failed.dispose();
    require(
        rig.count(rig.deletedFramebuffers, sentinel) == deletesOfSentinelBeforeDispose,
        "disposing failed owner does not delete the live reused sentinel name");
    assertNoOwner(rig, failed);
  }

  private static void failedRebuildCanRetry(Rig rig) {
    RecoverableFrameBuffer retry = rig.newFramebuffer(40, 28, true);
    Texture oldAttachment = retry.getColorBufferTexture();
    rig.failNextFramebufferCheck.set(1);
    expectIncomplete(rig);
    require(retry.getTextureAttachments().size == 0, "failed build state is neutral before retry");

    int uploadsBeforeRetry = rig.textureUploads.get();
    GLFrameBuffer.invalidateAllFrameBuffers(rig.app);
    require(
        rig.textureUploads.get() == uploadsBeforeRetry + 1,
        "retry allocates a single fresh attachment");
    require(
        retry.getTextureAttachments().size == 1 && retry.getColorBufferTexture() != oldAttachment,
        "retry installs exactly one fresh attachment wrapper");
    require(
        RecoverableFrameBuffer.managedIdentityCount(rig.app, retry) == 1,
        "retry leaves one managed registration");
    retry.begin();
    retry.end();
    retry.dispose();
    assertNoOwner(rig, retry);
  }

  private static void expectIncomplete(Rig rig) {
    boolean threw = false;
    try {
      GLFrameBuffer.invalidateAllFrameBuffers(rig.app);
    } catch (IllegalStateException expected) {
      threw = true;
    }
    require(threw, "fake GL must force libGDX's incomplete-framebuffer failure path");
  }

  private static void assertRegistry(
      Rig rig, RecoverableFrameBuffer first, RecoverableFrameBuffer second, String phase) {
    Array<GLFrameBuffer> owners = RecoverableFrameBuffer.managedSnapshot(rig.app);
    require(
        owners.size == 2 && owners.get(0) == first && owners.get(1) == second,
        phase + " preserves both owners and order");
    require(
        RecoverableFrameBuffer.managedIdentityCount(rig.app, first) == 1
            && RecoverableFrameBuffer.managedIdentityCount(rig.app, second) == 1,
        phase + " has exactly one registry identity per owner");
  }

  private static void assertLastAttached(
      Rig rig,
      RecoverableFrameBuffer first,
      RecoverableFrameBuffer second,
      int count,
      String phase) {
    for (int i = rig.attachedTextures.size() - count; i < rig.attachedTextures.size(); i++) {
      int attached = rig.attachedTextures.get(i);
      require(
          attached == first.getColorBufferTexture().getTextureObjectHandle()
              || attached == second.getColorBufferTexture().getTextureObjectHandle(),
          phase + " attaches only current owner textures");
    }
  }

  private static void assertNoOwner(Rig rig, RecoverableFrameBuffer owner) {
    require(
        RecoverableFrameBuffer.managedIdentityCount(rig.app, owner) == 0,
        "terminal dispose unregisters owner");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive() || type == void.class) return null;
    if (type == boolean.class) return false;
    if (type == char.class) return '\0';
    if (type == byte.class) return (byte) 0;
    if (type == short.class) return (short) 0;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == float.class) return 0f;
    if (type == double.class) return 0d;
    throw new AssertionError(type);
  }

  private static final class Rig {
    final AtomicInteger nextHandle = new AtomicInteger(10);
    final AtomicInteger forcedFramebufferName = new AtomicInteger(-1);
    final AtomicInteger lastFramebufferName = new AtomicInteger();
    final AtomicInteger textureUploads = new AtomicInteger();
    final AtomicInteger colorAttachCalls = new AtomicInteger();
    final AtomicInteger bindCalls = new AtomicInteger();
    final AtomicInteger allocationCeiling = new AtomicInteger(Integer.MAX_VALUE);
    final AtomicInteger failNextFramebufferCheck = new AtomicInteger();
    final List<Integer> attachedTextures = new ArrayList<>();
    final List<Integer> deletedTextures = new ArrayList<>();
    final List<Integer> deletedFramebuffers = new ArrayList<>();
    final List<Integer> deletedRenderbuffers = new ArrayList<>();
    final List<Integer> generatedFramebuffers = new ArrayList<>(),
        generatedTextures = new ArrayList<>(),
        generatedRenderbuffers = new ArrayList<>();
    String throwAt;
    RuntimeException allocationFailure;
    final Application app;
    final GL20 gl20;

    Rig() {
      InvocationHandler glHandler =
          (proxy, method, arguments) -> {
            String name = method.getName();
            if (name.equals(throwAt)) {
              throwAt = null;
              throw allocationFailure;
            }
            if (name.equals("glGenFramebuffer")) {
              int forced = forcedFramebufferName.getAndSet(-1);
              int handle = forced >= 0 ? forced : nextHandle.incrementAndGet();
              lastFramebufferName.set(handle);
              generatedFramebuffers.add(handle);
              return handle;
            }
            if (name.equals("glGenTexture") || name.equals("glGenRenderbuffer")) {
              int handle = nextHandle.incrementAndGet();
              (name.equals("glGenTexture") ? generatedTextures : generatedRenderbuffers)
                  .add(handle);
              return handle;
            }
            if (name.equals("glCheckFramebufferStatus")) {
              if (failNextFramebufferCheck.getAndSet(0) > 0)
                return FRAMEBUFFER_INCOMPLETE_ATTACHMENT;
              return FRAMEBUFFER_COMPLETE;
            }
            if (name.equals("glTexImage2D")) {
              int count = textureUploads.incrementAndGet();
              if (count > allocationCeiling.get())
                throw new AssertionError("allocation guard exceeded: managed FBO registry grew");
            }
            if (name.equals("glFramebufferTexture2D")) {
              colorAttachCalls.incrementAndGet();
              attachedTextures.add((Integer) arguments[3]);
            }
            if (name.equals("glBindFramebuffer")) bindCalls.incrementAndGet();
            if (name.equals("glDeleteTexture")) deletedTextures.add((Integer) arguments[0]);
            if (name.equals("glDeleteFramebuffer")) deletedFramebuffers.add((Integer) arguments[0]);
            if (name.equals("glDeleteRenderbuffer"))
              deletedRenderbuffers.add((Integer) arguments[0]);
            return defaultValue(method.getReturnType());
          };
      gl20 =
          (GL20)
              Proxy.newProxyInstance(
                  GL20.class.getClassLoader(), new Class<?>[] {GL20.class}, glHandler);
      app =
          (Application)
              Proxy.newProxyInstance(
                  Application.class.getClassLoader(),
                  new Class<?>[] {Application.class},
                  (proxy, method, arguments) -> {
                    if (method.getName().equals("getType"))
                      return Application.ApplicationType.Desktop;
                    return defaultValue(method.getReturnType());
                  });
      Graphics graphics =
          (Graphics)
              Proxy.newProxyInstance(
                  Graphics.class.getClassLoader(),
                  new Class<?>[] {Graphics.class},
                  (proxy, method, arguments) -> {
                    if (method.getName().equals("isGL30Available")) return false;
                    if (method.getName().equals("supportsExtension")) return false;
                    return defaultValue(method.getReturnType());
                  });
      Gdx.app = app;
      Gdx.graphics = graphics;
      Gdx.gl = gl20;
      Gdx.gl20 = gl20;
      Gdx.gl30 = null;
    }

    RecoverableFrameBuffer newFramebuffer(int width, int height, boolean depth) {
      allocationCeiling.set(Integer.MAX_VALUE);
      return new RecoverableFrameBuffer(Pixmap.Format.RGBA8888, width, height, depth);
    }

    int count(List<Integer> values, int target) {
      int matches = 0;
      for (int value : values) if (value == target) matches++;
      return matches;
    }

    int nonzeroDeletes() {
      int total = 0;
      for (int value : deletedTextures) if (value != 0) total++;
      for (int value : deletedFramebuffers) if (value != 0) total++;
      for (int value : deletedRenderbuffers) if (value != 0) total++;
      return total;
    }
  }
}
