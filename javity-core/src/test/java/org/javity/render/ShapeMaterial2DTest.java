package org.javity.render;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import java.util.Map;
import org.junit.Test;

public class ShapeMaterial2DTest {
  @Test
  public void customMaterialCopiesTypedUniformInputsAndRejectsUnknownTypes() {
    ShapeMaterial2D material =
        ShapeMaterial2D.custom(
            "vec4 shapeColor(vec2 local, vec4 tint) { return tint * u_tint; }",
            Map.of(
                "u_tint",
                ShapeMaterial2D.UniformType.VEC4,
                "u_offset",
                ShapeMaterial2D.UniformType.VEC2,
                "u_time",
                ShapeMaterial2D.UniformType.FLOAT));
    Color tint = new Color(.2f, .4f, .6f, .8f);
    Vector2 offset = new Vector2(2, 3);
    material.setColor("u_tint", tint).setVector2("u_offset", offset).setFloat("u_time", 4);
    tint.set(Color.RED);
    offset.set(9, 10);

    assertEquals(.2f, material.getColor("u_tint").r, 0);
    assertEquals(2, material.getVector2("u_offset").x, 0);
    assertEquals(4, material.getFloat("u_time"), 0);
    assertThrows(IllegalArgumentException.class, () -> material.setFloat("u_tint", 1));
    assertThrows(IllegalArgumentException.class, () -> material.setFloat("u_missing", 1));
    Color returned = material.getColor("u_tint");
    returned.set(Color.BLACK);
    assertEquals(.2f, material.getColor("u_tint").r, 0);
    Vector2 returnedOffset = material.getVector2("u_offset");
    returnedOffset.setZero();
    assertEquals(2, material.getVector2("u_offset").x, 0);
    try {
      material.uniformTypes().clear();
      fail("uniform schema must be immutable");
    } catch (UnsupportedOperationException expected) {
      assertEquals(3, material.uniformTypes().size());
    }
  }

  @Test
  public void shaderSchemaAndSourcesRejectUnsafeOrReservedNames() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ShapeMaterial2D.custom(
                "vec4 shapeColor(vec2 local, vec4 tint) { return tint; }",
                Map.of("u_kind", ShapeMaterial2D.UniformType.FLOAT)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ShapeMaterial2D.custom(
                "vec4 shapeColor(vec2 local, vec4 tint) { return tint; }",
                Map.of("uniform", ShapeMaterial2D.UniformType.FLOAT)));
    assertThrows(
        IllegalArgumentException.class,
        () -> ShapeMaterial2D.custom("void main(){ gl_FragColor=vec4(1.0); }", Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ShapeMaterial2D.custom(
                    "vec4 shapeColor(vec2 local, vec4 tint) { return tint; }",
                    Map.of("u_time", ShapeMaterial2D.UniformType.FLOAT))
                .setFloat("u_time", Float.NaN));
  }

  @Test
  public void schemaRejectsProjectionAttributesAndVaryingsOwnedByEngineShader() {
    for (String name :
        new String[] {
          "u_projTrans", "a_position", "a_color", "a_texCoord0", "v_color", "v_local", "v_texCoords"
        }) {
      assertThrows(
          name,
          IllegalArgumentException.class,
          () ->
              ShapeMaterial2D.custom(
                  "vec4 shapeColor(vec2 local, vec4 tint) { return tint; }",
                  Map.of(name, ShapeMaterial2D.UniformType.FLOAT)));
    }
  }

  @Test
  public void dashUniformsRemainReservedFromMaterialSchemas() {
    for (String name :
        new String[] {"u_dashCount", "u_dashPeriod", "u_dashIntervals", "u_dashStartPhase"})
      assertThrows(
          name,
          IllegalArgumentException.class,
          () ->
              ShapeMaterial2D.custom(
                  "vec4 shapeColor(vec2 local, vec4 tint) { return tint; }",
                  Map.of(name, ShapeMaterial2D.UniformType.FLOAT)));
  }

  @Test
  public void builtinMaterialsExposeTheirAnimatedFloatUniform() {
    ShapeMaterial2D gradient = ShapeMaterial2D.gradient(Color.RED, Color.BLUE, .5f);
    ShapeMaterial2D pulse = ShapeMaterial2D.pulse(Color.WHITE, Color.RED, 2);
    ShapeMaterial2D stripes = ShapeMaterial2D.stripes(Color.WHITE, Color.BLACK, 8, 3);
    assertEquals(.5f, gradient.getFloat("u_angle"), 0);
    for (ShapeMaterial2D material : new ShapeMaterial2D[] {pulse, stripes}) {
      assertEquals(0, material.getFloat("u_time"), 0);
      material.setFloat("u_time", 1.25f);
      assertEquals(1.25f, material.getFloat("u_time"), 0);
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> ShapeMaterial2D.stripes(Color.WHITE, Color.BLACK, Float.POSITIVE_INFINITY, 1));
  }
}
