package org.javity.demo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Matrix4;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.javity.render.ShapeMaterial2D;
import org.javity.render.ShapeRenderer2D;
import org.javity.render.SmoothShapeRenderer;

/** Real framebuffer and driver-resource assertions, isolated from the interactive gallery. */
final class ShapeMaterialSmokeProbe {
  private ShapeMaterialSmokeProbe() {}

  static void run() {
    GL20 original = Gdx.gl;
    GL20 original20 = Gdx.gl20;
    List<Integer> programs = new ArrayList<>();
    List<Integer> shaderHandles = new ArrayList<>();
    List<String> driverErrors = new ArrayList<>();
    GL20 observed =
        (GL20)
            Proxy.newProxyInstance(
                GL20.class.getClassLoader(),
                new Class<?>[] {GL20.class},
                (proxy, method, arguments) -> {
                  try {
                    Object result = method.invoke(original20, arguments);
                    if (method.getName().equals("glCreateProgram")) programs.add((Integer) result);
                    if (method.getName().equals("glCreateShader"))
                      shaderHandles.add((Integer) result);
                    if (!method.getName().equals("glGetError")) {
                      int error = original20.glGetError();
                      if (error != GL20.GL_NO_ERROR)
                        driverErrors.add(
                            "GL error 0x"
                                + Integer.toHexString(error)
                                + " after "
                                + method.getName()
                                + " "
                                + java.util.Arrays.toString(arguments));
                    }
                    return result;
                  } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                  }
                });
    Gdx.gl = observed;
    Gdx.gl20 = observed;
    SmoothShapeRenderer renderer = null;
    try {
      renderer = new SmoothShapeRenderer();
      Matrix4 projection =
          new Matrix4().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
      String function =
          "vec4 shapeColor(vec2 local, vec4 tint) { return"
              + " vec4(u_color.rgb*u_gain+vec3(u_offset,0.0),u_color.a)*tint; }";
      Map<String, ShapeMaterial2D.UniformType> schema =
          Map.of(
              "u_color", ShapeMaterial2D.UniformType.VEC4,
              "u_gain", ShapeMaterial2D.UniformType.FLOAT,
              "u_offset", ShapeMaterial2D.UniformType.VEC2,
              "u_unused", ShapeMaterial2D.UniformType.FLOAT);
      ShapeMaterial2D red =
          ShapeMaterial2D.custom(function, schema)
              .setColor("u_color", new Color(.8f, 0, 0, .5f))
              .setFloat("u_gain", 1)
              .setVector2("u_offset", 0, 0);
      ShapeMaterial2D green =
          ShapeMaterial2D.custom(function, schema)
              .setColor("u_color", new Color(0, .8f, 0, 1))
              .setFloat("u_gain", 1)
              .setVector2("u_offset", 0, .1f);
      ShapeRenderer2D first = ShapeRenderer2D.rectangle(60, 60);
      ShapeRenderer2D second = ShapeRenderer2D.rectangle(60, 60);
      ShapeRenderer2D plain = ShapeRenderer2D.rectangle(60, 60);
      first.material = red;
      second.material = green;
      clear();
      draw(renderer, first, projection, 100, 100);
      int programsAfterFirstMaterial = programs.size();
      draw(renderer, second, projection, 200, 100);
      draw(renderer, plain, projection, 300, 100);
      require(
          programs.size() == programsAfterFirstMaterial,
          "Equal shader definitions were compiled more than once: " + programs.size());
      try (Pixels pixels = new Pixels()) {
        pixels.color(100, 100, .4f, 0, 0, "half-alpha custom color");
        pixels.color(200, 100, 0, .9f, 0, "independent vec2/vec4 uniforms");
        pixels.color(300, 100, 1, 1, 1, "default shader after custom draw");
      }
      red.setColor("u_color", Color.BLUE).setVector2("u_offset", .1f, 0).setFloat("u_gain", .5f);
      clear();
      draw(renderer, first, projection, 100, 100);
      draw(renderer, second, projection, 200, 100);
      try (Pixels pixels = new Pixels()) {
        pixels.color(100, 100, .1f, 0, .5f, "updated cached uniforms");
        pixels.color(200, 100, 0, .9f, 0, "no uniform leakage");
      }
      require(
          programs.size() == programsAfterFirstMaterial,
          "Updating uniform values recompiled the shader");

      ShapeRenderer2D gradient = ShapeRenderer2D.rectangle(160, 80);
      gradient.cornerRadius = 18;
      gradient.material = ShapeMaterial2D.gradient(Color.RED, Color.BLUE, 0);
      clear();
      draw(renderer, gradient, projection, 400, 200);
      try (Pixels pixels = new Pixels()) {
        Color left = pixels.at(350, 200), right = pixels.at(450, 200);
        require(
            left.r > left.b + .4f && right.b > right.r + .4f, "Gradient does not span the shape");
        pixels.color(321, 161, 0, 0, 0, "rounded corner keeps SDF mask");
        require(pixels.intermediate(315, 485, 155, 245) > 0, "Custom gradient lost antialiasing");
      }
      ShapeRenderer2D offsetLine =
          ShapeRenderer2D.polyline(
              List.of(
                  new com.badlogic.gdx.math.Vector2(50, 0),
                  new com.badlogic.gdx.math.Vector2(150, 0)),
              20);
      offsetLine.material = ShapeMaterial2D.gradient(Color.RED, Color.BLUE, (float) (Math.PI / 2));
      draw(renderer, offsetLine, projection, 700, 250);
      try (Pixels pixels = new Pixels()) {
        float lower = pixels.at(800, 245).b, upper = pixels.at(800, 255).b;
        require(
            lower > .15f && lower < .35f && upper > .65f && upper < .85f,
            "Gradient must span visible polyline thickness: lower=" + lower + ", upper=" + upper);
      }
      ShapeRenderer2D invalid = ShapeRenderer2D.circle(20);
      invalid.material =
          ShapeMaterial2D.custom(
              "vec4 shapeColor(vec2 local, vec4 tint) { return DOES_NOT_EXIST; }", Map.of());
      boolean rejected = false;
      try {
        draw(renderer, invalid, projection, 600, 100);
      } catch (IllegalArgumentException expected) {
        rejected = expected.getMessage().contains("compilation failed");
      }
      require(rejected, "Invalid custom GLSL was silently accepted");
      ShapeRenderer2D invalidLink = ShapeRenderer2D.circle(20);
      invalidLink.material =
          ShapeMaterial2D.custom(
              "varying vec3 missingVarying; vec4 shapeColor(vec2 local, vec4 tint) { return"
                  + " vec4(missingVarying,1.0)*tint; }",
              Map.of());
      boolean linkRejected = false;
      try {
        draw(renderer, invalidLink, projection, 600, 100);
      } catch (IllegalArgumentException expected) {
        linkRejected = expected.getMessage().contains("link");
      }
      require(linkRejected, "Custom GLSL link failure was not reported");
      draw(renderer, plain, projection, 300, 100);
      try (Pixels pixels = new Pixels()) {
        pixels.color(300, 100, 1, 1, 1, "draw recovers after compiler error");
      }
      ShapeRenderer2D transparent = ShapeRenderer2D.circle(20);
      transparent.material =
          ShapeMaterial2D.custom(
              "vec4 shapeColor(vec2 local, vec4 tint) { return vec4(0.0); }", Map.of());
      clear();
      draw(renderer, transparent, projection, 600, 100);
      try (Pixels pixels = new Pixels()) {
        pixels.color(600, 100, 0, 0, 0, "fully optimized transparent shader");
      }
      for (int i = 0; i < 29; i++) {
        ShapeRenderer2D cached = ShapeRenderer2D.circle(2);
        cached.material =
            ShapeMaterial2D.custom(
                "vec4 shapeColor(vec2 local, vec4 tint) { return tint; } /* cache variant "
                    + i
                    + " */",
                Map.of());
        draw(renderer, cached, projection, 700, 100);
      }
      ShapeRenderer2D overflow = ShapeRenderer2D.circle(2);
      overflow.material =
          ShapeMaterial2D.custom(
              "vec4 shapeColor(vec2 local, vec4 tint) { return tint; } /* cache overflow */",
              Map.of());
      boolean bounded = false;
      try {
        draw(renderer, overflow, projection, 700, 100);
      } catch (IllegalStateException expected) {
        bounded = expected.getMessage().contains("at most 32");
      }
      require(bounded, "Material program cache did not enforce its bound");
      clear();
      draw(renderer, first, projection, 100, 100);
      try (Pixels pixels = new Pixels()) {
        pixels.color(100, 100, .1f, 0, .5f, "cached material survives cache limit");
      }
      renderer.dispose();
      renderer.dispose();
      renderer = null;
      for (int program : programs)
        require(!original20.glIsProgram(program), "Renderer leaked GPU program " + program);
      for (int handle : shaderHandles)
        require(
            !original20.glIsShader(handle),
            "Renderer leaked GPU shader " + handle + "; driver errors: " + driverErrors);
      require(driverErrors.isEmpty(), "Driver errors during probe: " + driverErrors);
      require(original20.glGetError() == GL20.GL_NO_ERROR, "Material probe left a GL error");
      var proof = Gdx.files.local("artifacts/shape-materials/gpu-proof.json");
      proof.parent().mkdirs();
      proof.writeString(
          "{\"status\":\"PASS\",\"programHandlesChecked\":"
              + programs.size()
              + ",\"shaderHandlesChecked\":"
              + shaderHandles.size()
              + ",\"allHandlesReleased\":true,\"driverErrors\":0,\"cacheLimit\":32,\"uniformIsolation\":true,\"compileAndLinkRecovery\":true,\"polylineThicknessGradient\":true}\n",
          false);
      Gdx.app.log(
          "ShapeMaterials",
          "GPU PASS: typed uniforms, isolation, cache, alpha, AA, compile recovery and program"
              + " disposal");
    } finally {
      try {
        if (renderer != null) renderer.dispose();
      } finally {
        Gdx.gl = original;
        Gdx.gl20 = original20;
      }
    }
  }

  private static void draw(
      SmoothShapeRenderer renderer, ShapeRenderer2D shape, Matrix4 projection, float x, float y) {
    renderer.draw(
        shape,
        projection,
        new Matrix4().setToTranslation(x, y, 0),
        Gdx.graphics.getBackBufferWidth(),
        Gdx.graphics.getBackBufferHeight());
  }

  private static void clear() {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(0, 0, 0, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException("Material smoke: " + message);
  }

  private static final class Pixels implements AutoCloseable {
    private final Pixmap image =
        Pixmap.createFromFrameBuffer(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());

    Color at(int x, int y) {
      return new Color(image.getPixel(x, y));
    }

    void color(int x, int y, float r, float g, float b, String name) {
      Color value = at(x, y);
      require(
          Math.abs(value.r - r) < .025f
              && Math.abs(value.g - g) < .025f
              && Math.abs(value.b - b) < .025f,
          name + ": actual " + value + " expected " + r + "," + g + "," + b);
    }

    int intermediate(int x0, int x1, int y0, int y1) {
      int count = 0;
      for (int y = y0; y < y1; y++)
        for (int x = x0; x < x1; x++) {
          Color c = at(x, y);
          float brightness = c.r + c.b;
          if (brightness > .03f && brightness < .95f) count++;
        }
      return count;
    }

    @Override
    public void close() {
      image.dispose();
    }
  }
}
