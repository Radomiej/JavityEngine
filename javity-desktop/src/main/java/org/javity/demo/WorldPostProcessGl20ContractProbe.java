package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.javity.postprocessing.WorldPostProcess;

/**
 * Bounded GL20 proof for eligible repeated capture, prebound rejection and element-buffer state.
 */
final class WorldPostProcessGl20ContractProbe extends ApplicationAdapter {
  private Throwable failure;

  @Override
  public void create() {
    WorldPostProcess post = null;
    int elementBuffer = 0;
    int callerBuffer = 0;
    try {
      post = new WorldPostProcess(32, 32);
      elementBuffer = Gdx.gl.glGenBuffer();
      Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
      Gdx.gl.glBufferData(
          GL20.GL_ELEMENT_ARRAY_BUFFER, 12, BufferUtils.newByteBuffer(12), GL20.GL_STATIC_DRAW);
      int entryElementBuffer = getInt(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING);
      int[] callbackCount = {0};
      post.renderWorld(
          () -> {
            callbackCount[0]++;
            clear(.2f);
          });
      require(
          getInt(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING) == entryElementBuffer,
          "eligible GL20 pass changed caller element-array binding");
      post.renderWorld(
          () -> {
            callbackCount[0]++;
            clear(.4f);
          });
      require(callbackCount[0] == 2, "eligible GL20 repeated pass did not invoke both callbacks");
      require(
          getInt(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING) == entryElementBuffer,
          "repeated GL20 pass changed caller element-array binding");
      ByteBuffer observed = BufferUtils.newByteBuffer(4);
      Gdx.gl.glReadPixels(16, 16, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, observed);
      require((observed.get(0) & 255) >= 100, "eligible GL20 repeat produced no expected color");

      int position = Gdx.gl.glGetAttribLocation(post.resourceIds().shaderProgram(), "a_position");
      callerBuffer = Gdx.gl.glGenBuffer();
      Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, callerBuffer);
      Gdx.gl.glBufferData(
          GL20.GL_ARRAY_BUFFER, 32, BufferUtils.newByteBuffer(32), GL20.GL_STATIC_DRAW);
      Gdx.gl.glEnableVertexAttribArray(position);
      Gdx.gl.glVertexAttribPointer(position, 2, GL20.GL_FLOAT, false, 0, 0);
      int entryFramebuffer = getInt(GL20.GL_FRAMEBUFFER_BINDING);
      int[] entryViewport = getFour(GL20.GL_VIEWPORT);
      int entryArrayBuffer = getInt(GL20.GL_ARRAY_BUFFER_BINDING);
      boolean[] invoked = {false};
      try {
        post.renderWorld(
            () -> {
              invoked[0] = true;
              clear(.8f);
            });
        throw new AssertionError("GL20 pass accepted a prebound caller attribute");
      } catch (IllegalStateException expected) {
        require(
            expected.getMessage().contains("rejects prebound"),
            "unexpected GL20 rejection: " + expected.getMessage());
      }
      require(!invoked[0], "GL20 rejection happened after callback mutation");
      require(
          getInt(GL20.GL_FRAMEBUFFER_BINDING) == entryFramebuffer,
          "prebound rejection changed framebuffer");
      require(
          java.util.Arrays.equals(entryViewport, getFour(GL20.GL_VIEWPORT)),
          "prebound rejection changed viewport");
      require(
          getInt(GL20.GL_ARRAY_BUFFER_BINDING) == entryArrayBuffer,
          "prebound rejection changed caller array buffer");
      require(
          getInt(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING) == entryElementBuffer,
          "prebound rejection changed caller element-array buffer");

      Gdx.gl.glDisableVertexAttribArray(position);
      Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, 0);
      Gdx.gl.glVertexAttribPointer(position, 4, GL20.GL_FLOAT, false, 0, 0);
      post.renderWorld(() -> clear(.6f));
      post.renderWorld(() -> clear(.3f));
      require(
          getInt(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING) == entryElementBuffer,
          "eligible GL20 pass after rejection changed element-array binding");
      require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL20 contract probe left GL error");
      System.out.println(
          "worldPostProcessGl20Contract: PASS (repeated eligible capture, prebound rejection,"
              + " element buffer)");
    } catch (Throwable problem) {
      failure = problem;
      problem.printStackTrace();
    } finally {
      if (post != null) post.dispose();
      if (callerBuffer != 0) Gdx.gl.glDeleteBuffer(callerBuffer);
      if (elementBuffer != 0) Gdx.gl.glDeleteBuffer(elementBuffer);
      Gdx.app.exit();
    }
  }

  void assertSuccess() {
    if (failure != null) throw new IllegalStateException("GL20 contract probe failed", failure);
  }

  private static int getInt(int parameter) {
    IntBuffer value = BufferUtils.newIntBuffer(1);
    Gdx.gl.glGetIntegerv(parameter, value);
    return value.get(0);
  }

  private static int[] getFour(int parameter) {
    IntBuffer value = BufferUtils.newIntBuffer(4);
    Gdx.gl.glGetIntegerv(parameter, value);
    return new int[] {value.get(0), value.get(1), value.get(2), value.get(3)};
  }

  private static void clear(float value) {
    Gdx.gl.glClearColor(value, value, value, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
