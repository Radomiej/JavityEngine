package org.javity.render;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.Test;

public class SmoothShapeShaderDialectTest {
  @Test
  public void whiteTextureDataIsManagedAndRetainsItsPixmapForReload() {
    GdxNativesLoader.load();
    Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    try {
      var data = SmoothShapeRenderer.managedWhiteTextureData(pixel);
      assertTrue(data.isManaged());
      assertFalse(data.disposePixmap());
      assertSame(pixel, data.consumePixmap());
    } finally {
      pixel.dispose();
    }
  }

  @Test
  public void desktopKeepsTheExistingGlsl120SourceByteForByte() {
    assertSame(
        SmoothShapeRenderer.VERTEX_SHADER,
        SmoothShapeRenderer.shaderSource(SmoothShapeRenderer.VERTEX_SHADER, true, false));
    assertSame(
        SmoothShapeRenderer.FRAGMENT_SHADER,
        SmoothShapeRenderer.shaderSource(SmoothShapeRenderer.FRAGMENT_SHADER, false, false));
  }

  @Test
  public void gles300ConvertsStageInputsAndFragmentOutput() {
    String vertex =
        SmoothShapeRenderer.shaderSource(SmoothShapeRenderer.VERTEX_SHADER, true, true);
    String fragment =
        SmoothShapeRenderer.shaderSource(SmoothShapeRenderer.FRAGMENT_SHADER, false, true);

    assertTrue(vertex.startsWith("#version 300 es\n"));
    assertTrue(vertex.contains("in vec4 a_position;"));
    assertTrue(vertex.contains("out vec4 v_color;"));
    assertFalse(vertex.contains("attribute "));
    assertTrue(fragment.startsWith("#version 300 es\nprecision highp float;\nout vec4 javityFragColor;"));
    assertTrue(fragment.contains("in vec4 v_color;"));
    assertTrue(fragment.contains("texture(u_texture,v_texCoords)"));
    assertTrue(fragment.contains("javityFragColor=texel*materialColor"));
    assertFalse(fragment.contains("varying "));
    assertFalse(fragment.contains("texture2D("));
    assertFalse(fragment.contains("gl_FragColor"));
  }

  @Test
  public void glesKeepsTheValidatedBoundedShapeAndDashCapacities() {
    String fragment =
        SmoothShapeRenderer.shaderSource(SmoothShapeRenderer.FRAGMENT_SHADER, false, true);
    assertTrue(fragment.contains("uniform vec4 u_points[32]"));
    assertTrue(fragment.contains("uniform vec4 u_dashIntervals[2]"));
    assertTrue(fragment.contains("uniform vec4 u_dashStartPhase[16]"));
    assertTrue(fragment.contains("for(int i=0;i<63;i++)"));
    assertTrue(fragment.contains("for(int j=0;j<4;j++)"));
    assertTrue(fragment.contains("for(int q=0;q<2;q++)"));
  }

  @Test
  public void customMaterialTextureCallsConvertAcrossWhitespaceAndComments() {
    String[][] spellings = {
      {"texture2D(u_texture,v_texCoords)", "texture(u_texture,v_texCoords)"},
      {"texture2D (u_texture,v_texCoords)", "texture (u_texture,v_texCoords)"},
      {"texture2D/* keep */\n (u_texture,v_texCoords)", "texture/* keep */\n (u_texture,v_texCoords)"}
    };
    for (String[] spelling : spellings) {
      String materialFragment =
          SmoothShapeRenderer.FRAGMENT_SHADER.replace(
              "vec4 shapeColor(vec2 local, vec4 tint){ return tint; }",
              "// keep texture2D in comments and texture2DExtra as an unrelated identifier\n"
                  + "uniform float texture2DExtra;\n"
                  + "vec4 shapeColor(vec2 local, vec4 tint){ return "
                  + spelling[0]
                  + "*tint; }");
      String gles = SmoothShapeRenderer.shaderSource(materialFragment, false, true);
      assertTrue(gles.contains(spelling[1]));
      assertTrue(gles.contains("// keep texture2D in comments and texture2DExtra"));
      assertTrue(gles.contains("uniform float texture2DExtra;"));
      assertFalse(gles.contains("texture2D(u_texture"));
      assertFalse(gles.contains("texture2D (u_texture"));
    }
  }
}
