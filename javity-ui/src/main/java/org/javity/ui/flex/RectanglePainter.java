package org.javity.ui.flex;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

/** One reusable signed-distance shader for rectangles and composited image widgets. */
final class RectanglePainter implements Disposable {
  private final ShaderProgram shader;
  private static final String VERTEX =
      """
      attribute vec4 a_position;
      attribute vec4 a_color;
      attribute vec2 a_texCoord0;
      uniform mat4 u_projTrans;
      varying vec4 v_color;
      varying vec2 v_uv;
      void main() {
          v_color=a_color;
          v_color.a *= 255.0/254.0;
          v_uv=a_texCoord0;
          gl_Position=u_projTrans*a_position;
      }
      """;
  private static final String FRAGMENT =
      """
      #ifdef GL_ES
      precision mediump float;
      #endif
      varying vec4 v_color;
      varying vec2 v_uv;
      uniform sampler2D u_texture;
      uniform vec2 u_size;
      uniform vec4 u_baseUv;
      uniform vec4 u_sampleUv;
      uniform vec4 u_imageRect;
      uniform vec4 u_clipRect;
      uniform vec2 u_clipSize;
      uniform float u_radius;
      uniform float u_clipRadius;
      uniform float u_border;
      uniform float u_imageEnabled;
      uniform vec4 u_fill;
      uniform vec4 u_borderColor;
      float distanceToBox(vec2 p,vec2 halfSize,float radius) {
          vec2 q=abs(p)-halfSize+vec2(radius);
          return length(max(q,vec2(0.0)))+min(max(q.x,q.y),0.0)-radius;
      }
      void main() {
          vec2 local=(v_uv-u_baseUv.xy)/u_baseUv.zw;
          vec2 p=(local-vec2(0.5))*u_size;
          float outer=distanceToBox(p,u_size*0.5,u_radius);
          float outerCoverage=1.0-smoothstep(-0.5,0.5,outer);
          vec4 under=u_fill;
          if (u_border>0.0) {
              float inner=distanceToBox(p,max(u_size*0.5-vec2(u_border),vec2(0.0)),max(u_radius-u_border,0.0));
              float inside=1.0-smoothstep(-0.5,0.5,inner);
              if (u_border>=min(u_size.x,u_size.y)*0.5) inside=0.0;
              under=mix(u_borderColor,u_fill,inside);
          }
          under.a *= outerCoverage;
          if (u_imageEnabled>0.5) {
              vec2 sampled=vec2(0.0);
              float inImage=step(u_imageRect.x,local.x)*step(u_imageRect.y,local.y)
                  *step(local.x,u_imageRect.x+u_imageRect.z)*step(local.y,u_imageRect.y+u_imageRect.w);
              vec2 imageLocal=(local-u_imageRect.xy)/u_imageRect.zw;
              sampled=u_sampleUv.xy+imageLocal*u_sampleUv.zw;
              vec4 image=texture2D(u_texture,sampled);
              vec2 clipLocal=(local-u_clipRect.xy)/u_clipRect.zw;
              vec2 clipP=(clipLocal-vec2(0.5))*u_clipSize;
              float clipDistance=distanceToBox(clipP,u_clipSize*0.5,u_clipRadius);
              float clipCoverage=1.0-smoothstep(-0.5,0.5,clipDistance);
              float imageAlpha=image.a*inImage*clipCoverage;
              float outAlpha=imageAlpha+under.a*(1.0-imageAlpha);
              vec3 outRgb=outAlpha>0.0
                  ? (image.rgb*imageAlpha+under.rgb*under.a*(1.0-imageAlpha))/outAlpha
                  : vec3(0.0);
              gl_FragColor=vec4(outRgb,outAlpha)*v_color;
          } else {
              gl_FragColor=under*v_color*texture2D(u_texture,v_uv);
          }
      }
      """;

  RectanglePainter() {
    shader = new ShaderProgram(VERTEX, FRAGMENT);
    if (!shader.isCompiled()) {
      String log = shader.getLog();
      shader.dispose();
      throw new GdxRuntimeException("UI rectangle shader: " + log);
    }
  }

  void draw(
      Batch batch,
      Texture white,
      float x,
      float y,
      float width,
      float height,
      UtilityStyles.Visual style) {
    if (width <= 0 || height <= 0) return;
    ShaderProgram previous = batch.getShader();
    batch.setShader(shader);
    try {
      shader.setUniformf("u_baseUv", 0f, 0f, 1f, 1f);
      shader.setUniformf("u_sampleUv", 0f, 0f, 1f, 1f);
      shader.setUniformf("u_imageRect", 0f, 0f, 1f, 1f);
      shader.setUniformf("u_clipRect", 0f, 0f, 1f, 1f);
      shader.setUniformf("u_clipSize", width, height);
      shader.setUniformf("u_size", width, height);
      shader.setUniformf("u_radius", style.radiusFor(width, height));
      shader.setUniformf("u_clipRadius", 0f);
      shader.setUniformf("u_border", style.borderFor(width, height));
      shader.setUniformf("u_imageEnabled", 0f);
      shader.setUniformf("u_fill", style.background() == null ? Color.CLEAR : style.background());
      shader.setUniformf("u_borderColor", style.borderColor());
      batch.draw(white, x, y, width, height);
      batch.flush();
    } finally {
      batch.setShader(previous);
    }
  }

  void drawImage(
      Batch batch,
      TextureRegion region,
      float x,
      float y,
      float width,
      float height,
      UtilityStyles.Visual style,
      float clipX,
      float clipY,
      float clipWidth,
      float clipHeight,
      float clipRadius,
      float imageX,
      float imageY,
      float imageWidth,
      float imageHeight,
      float sampleU,
      float sampleV,
      float sampleU2,
      float sampleV2) {
    if (width <= 0 || height <= 0) return;
    ShaderProgram previous = batch.getShader();
    batch.setShader(shader);
    try {
      float u = region.getU(), v = region.getV(), u2 = region.getU2(), v2 = region.getV2();
      shader.setUniformf("u_baseUv", u, v, u2 - u, v2 - v);
      shader.setUniformf("u_sampleUv", sampleU, sampleV, sampleU2 - sampleU, sampleV2 - sampleV);
      shader.setUniformf("u_imageRect", imageX, imageY, imageWidth, imageHeight);
      shader.setUniformf("u_clipRect", clipX, clipY, clipWidth, clipHeight);
      shader.setUniformf("u_clipSize", width * clipWidth, height * clipHeight);
      shader.setUniformf("u_size", width, height);
      shader.setUniformf("u_radius", style.radiusFor(width, height));
      shader.setUniformf("u_clipRadius", Math.max(0, clipRadius));
      shader.setUniformf("u_border", style.borderFor(width, height));
      shader.setUniformf("u_imageEnabled", 1f);
      shader.setUniformf("u_fill", style.background() == null ? Color.CLEAR : style.background());
      shader.setUniformf("u_borderColor", style.borderColor());
      batch.draw(region.getTexture(), x, y, width, height, u, v, u2, v2);
      batch.flush();
    } finally {
      batch.setShader(previous);
    }
  }

  @Override
  public void dispose() {
    shader.dispose();
  }
}
