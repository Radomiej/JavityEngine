package org.javity.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reusable SDF renderer for anti-aliased rectangles, circles, and joined polylines. */
public final class SmoothShapeRenderer implements Disposable {
  static final String VERTEX_SHADER =
      "#version 120\n"
          + "attribute vec4 a_position;\n"
          + "attribute vec4 a_color;\n"
          + "attribute vec2 a_texCoord0;\n"
          + "uniform mat4 u_projTrans;\n"
          + "varying vec4 v_color;\n"
          + "varying vec2 v_local;\n"
          + "varying vec2 v_texCoords;\n"
          + "void main(){ v_color=a_color; v_color.a*=255.0/254.0; v_local=a_position.xy;"
          + " v_texCoords=a_texCoord0; gl_Position=u_projTrans*a_position; }\n";

  static final String FRAGMENT_SHADER =
      "#version 120\n"
          + "uniform sampler2D u_texture;\n"
          + "uniform int u_kind;\n"
          + "uniform int u_pointCount;\n"
          + "uniform int u_cap;\n"
          + "uniform float u_halfWidth;\n"
          + "uniform float u_halfHeight;\n"
          + "uniform float u_radius;\n"
          + "uniform float u_cornerRadius;\n"
          + "uniform float u_halfStroke;\n"
          + "uniform float u_antiAlias;\n"
          + "uniform vec4 u_points[32];\n"
          + "uniform int u_dashCount;\n"
          + "uniform float u_dashPeriod;\n"
          + "uniform vec4 u_dashIntervals[2];\n"
          + "uniform vec4 u_dashStartPhase[16];\n"
          + "uniform vec2 u_shapeCenter;\n"
          + "uniform vec2 u_shapeBounds;\n"
          + "varying vec4 v_color;\n"
          + "varying vec2 v_local;\n"
          + "varying vec2 v_texCoords;\n"
          + "vec2 pointAt(int i){ vec4 p=u_points[i/2]; if(i-(i/2)*2==0) return p.xy; return p.zw;"
          + " }\n"
          + "float boxDistance(vec2 p, vec2 halfSize){ vec2 q=abs(p)-halfSize; return"
          + " length(max(q,vec2(0.0)))+min(max(q.x,q.y),0.0); }\n"
          + "float roundedBoxDistance(vec2 p){ vec2"
          + " q=abs(p)-vec2(u_halfWidth,u_halfHeight)+vec2(u_cornerRadius); return"
          + " length(max(q,vec2(0.0)))+min(max(q.x,q.y),0.0)-u_cornerRadius; }\n"
          + "float diskDistance(vec2 p, vec2 c){ return length(p-c)-u_halfStroke; }\n"
          + "vec4 shapeColor(vec2 local, vec4 tint){ return tint; }\n"
          + "float phaseAt(int i){ vec4 v=u_dashStartPhase[i/4]; int c=i-(i/4)*4; if(c==0)return"
          + " v.x; if(c==1)return v.y; if(c==2)return v.z; return v.w; }\n"
          + "float dashBoundary(int i){ vec4 v=u_dashIntervals[i/4]; int c=i-(i/4)*4;"
          + " if(c==0)return v.x; if(c==1)return v.y; if(c==2)return v.z; return v.w; }\n"
          + "bool dashInk(float x){ float r=x;"
          + " for(int k=0;k<4;k++){ if(k<u_dashCount/2 && r>=dashBoundary(k*2) &&"
          + " r<dashBoundary(k*2+1)) return true; } return false; }\n"
          + "bool dashContinues(float x){ float"
          + " r=x; for(int k=0;k<4;k++){"
          + " if(k<u_dashCount/2 && r>dashBoundary(k*2) && r<dashBoundary(k*2+1)) return true; }"
          + " return false; }\n"
          + "float clippedSegment(vec2 p,vec2 a,vec2 t,vec2 n,float len,float lo,float hi,bool"
          + " capLo,bool capHi,bool squareLo,bool squareHi){ vec2 rel=p-a; float x=dot(rel,t),"
          + " y=dot(rel,n); float before=(capLo&&squareLo)?u_halfStroke:0.0; float"
          + " after=(capHi&&squareHi)?u_halfStroke:0.0; float h=(hi-lo+before+after)*0.5; float"
          + " c=(lo+hi+after-before)*0.5; float d=boxDistance(vec2(x-c,y),vec2(h,u_halfStroke));"
          + " if(capLo&&!squareLo&&u_cap==1)d=min(d,diskDistance(p,a+t*lo));"
          + " if(capHi&&!squareHi&&u_cap==1)d=min(d,diskDistance(p,a+t*hi)); return d; }\n"
          + "float polylineDistance(vec2 p){\n"
          + " if(u_pointCount==1){ if(u_dashCount>0&&!dashInk(phaseAt(0)))return 1.0e20; return"
          + " u_cap==1?diskDistance(p,pointAt(0)):1.0e20; }\n"
          + " float d=1.0e20; for(int i=0;i<63;i++){ if(i<u_pointCount-1){ vec2 a=pointAt(i); vec2"
          + " b=pointAt(i+1); vec2 delta=b-a; float len=length(delta); if(len>0.0){ vec2"
          + " t=delta/len,n=vec2(-t.y,t.x); float x=dot(p-a,t); float ph=phaseAt(i);"
          + " if(u_dashCount==0){ float before=(i==0&&u_cap==2)?u_halfStroke:0.0; float"
          + " after=(i==u_pointCount-2&&u_cap==2)?u_halfStroke:0.0; float h=(len+before+after)*0.5;"
          + " float c=(len+after-before)*0.5;"
          + " d=min(d,boxDistance(vec2(x-c,dot(p-a,n)),vec2(h,u_halfStroke)));"
          + " if(i>0)d=min(d,diskDistance(p,a)); } else { for(int j=0;j<4;j++){"
          + " if(j<u_dashCount/2){ float start=dashBoundary(j*2),end=dashBoundary(j*2+1); float"
          + " base=floor((clamp(x,0.0,len)+ph-start)/u_dashPeriod); for(int q=0;q<2;q++){ float"
          + " cyc=base+float(q); float rawLo=cyc*u_dashPeriod+start-ph; float"
          + " rawHi=cyc*u_dashPeriod+end-ph; float lo=max(0.0,rawLo),hi=min(len,rawHi); if(hi>lo){"
          + " bool pathLo=i==0&&lo==0.0; bool pathHi=i==u_pointCount-2&&hi==len; bool"
          + " actualLo=pathLo||rawLo>=0.0; bool actualHi=pathHi||rawHi<=len;"
          + " d=min(d,clippedSegment(p,a,t,n,len,lo,hi,actualLo,actualHi,u_cap==2,u_cap==2)); } } }"
          + " } } } } }\n"
          + " if(u_dashCount>0){ for(int i=1;i<63;i++){ if(i<u_pointCount-1){ vec2 a=pointAt(i);"
          + " vec2 b=pointAt(i+1); float len=length(b-a); if(dashContinues(phaseAt(i))){"
          + " d=min(d,diskDistance(p,a)); } } } } else if(u_cap==1){"
          + " d=min(d,diskDistance(p,pointAt(0)));"
          + " d=min(d,diskDistance(p,pointAt(u_pointCount-1))); } return d; }\n"
          + "void main(){\n"
          + "  float d;\n"
          + "  if(u_kind==0){ d=roundedBoxDistance(v_local); if(u_halfStroke>0.0)"
          + " d=abs(d)-u_halfStroke; }\n"
          + "  else if(u_kind==1){ d=length(v_local)-u_radius; if(u_halfStroke>0.0)"
          + " d=abs(d)-u_halfStroke; }\n"
          + "  else { d=polylineDistance(v_local); }\n"
          + "  float coverage=u_antiAlias>0.5 ? clamp(0.5-d/max(fwidth(d),0.00001),0.0,1.0) :"
          + " step(d,0.0);\n"
          + "  vec4 texel=texture2D(u_texture,v_texCoords); vec4"
          + " materialColor=shapeColor(v_local,v_color);"
          + " gl_FragColor=texel*materialColor*vec4(1.0,1.0,1.0,coverage);\n"
          + "}\n";

  private final ShaderProgram shader;
  private final Texture whiteTexture;
  private final Pixmap whitePixel;
  private final boolean gles300;
  private final SpriteBatch batch;
  private final Map<String, ShaderProgram> materialShaders = new LinkedHashMap<>();
  private final float[] packedPoints = new float[32 * 4];
  private final float[] packedDashIntervals = new float[2 * 4];
  private final float[] packedDashPhases = new float[16 * 4];
  private boolean disposed;
  private static final int MAX_MATERIAL_SHADERS = 32;

  /** Creates GPU resources. Must be called while an OpenGL context is current. */
  public SmoothShapeRenderer() {
    if (Gdx.app == null)
      throw new IllegalStateException("SmoothShapeRenderer requires a libGDX app for managed texture recovery");
    gles300 = isGles300Context();
    String vertexSource = shaderSource(VERTEX_SHADER, true, gles300);
    String fragmentSource = shaderSource(FRAGMENT_SHADER, false, gles300);
    requireUniformBudget(0);
    preflightShader(vertexSource, fragmentSource);
    ShaderProgram createdShader = new ShaderProgram(vertexSource, fragmentSource);
    Texture createdTexture = null;
    SpriteBatch createdBatch = null;
    Pixmap pixel = null;
    Pixmap retainedPixel = null;
    try {
      if (!createdShader.isCompiled())
        throw new IllegalStateException(
            "SmoothShapeRenderer shader compilation failed: " + createdShader.getLog());
      pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
      pixel.setColor(Color.WHITE);
      pixel.fill();
      createdTexture = new Texture(managedWhiteTextureData(pixel));
      createdTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
      retainedPixel = pixel;
      pixel = null;
      createdBatch = new OptionalUniformSpriteBatch(1, createdShader);
    } catch (RuntimeException | Error failure) {
      disposeAfterFailure(createdBatch, failure);
      disposeAfterFailure(createdTexture, failure);
      disposeAfterFailure(pixel, failure);
      disposeAfterFailure(retainedPixel, failure);
      disposeAfterFailure(createdShader, failure);
      throw failure;
    }
    shader = createdShader;
    whiteTexture = createdTexture;
    whitePixel = retainedPixel;
    batch = createdBatch;
  }

  private static void disposeAfterFailure(Disposable resource, Throwable failure) {
    if (resource == null) return;
    try {
      resource.dispose();
    } catch (RuntimeException | Error cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    }
  }

  /** SpriteBatch's default setup uses pedantic named setters for uniforms GLSL may optimize out. */
  private static final class OptionalUniformSpriteBatch extends SpriteBatch {
    private final Matrix4 projectionTransform = new Matrix4();

    OptionalUniformSpriteBatch(int size, ShaderProgram shader) {
      super(size, shader);
    }

    @Override
    protected void setupMatrices() {
      ShaderProgram shader = getShader();
      if (shader == null) return;
      projectionTransform.set(getProjectionMatrix()).mul(getTransformMatrix());
      int projectionLocation = shader.fetchUniformLocation("u_projTrans", false);
      if (projectionLocation >= 0) shader.setUniformMatrix(projectionLocation, projectionTransform);
      int textureLocation = shader.fetchUniformLocation("u_texture", false);
      if (textureLocation >= 0) shader.setUniformi(textureLocation, 0);
    }
  }

  /** Draws one supported shape. Caller must not have another SpriteBatch active. */
  public void draw(
      ShapeRenderer2D shape,
      Matrix4 projection,
      Matrix4 transform,
      int viewportWidth,
      int viewportHeight) {
    if (disposed) throw new IllegalStateException("Renderer is disposed");
    SmoothShapeMath.Prepared prepared =
        SmoothShapeMath.prepare(shape, projection, transform, viewportWidth, viewportHeight);
    if (prepared == null || !prepared.hasArea) return;

    for (int i = 0; i < packedPoints.length; i++) packedPoints[i] = 0;
    java.util.Arrays.fill(packedDashIntervals, 0f);
    java.util.Arrays.fill(packedDashPhases, 0f);
    for (int i = 0; i < prepared.pointCount; i++) {
      int packed = (i / 2) * 4 + ((i & 1) * 2);
      packedPoints[packed] = prepared.points[i * 2];
      packedPoints[packed + 1] = prepared.points[i * 2 + 1];
    }
    double dashCursor = 0;
    for (int i = 0; i < prepared.dashCount; i++) {
      packedDashIntervals[i] = (float) dashCursor;
      dashCursor += prepared.dashLengths[i];
    }
    if (prepared.dashCount > 0)
      for (int i = 0; i < prepared.pointCount; i++)
        packedDashPhases[i] = packDashPhase(prepared.dashStartPhases[i], prepared);

    int kind =
        switch (prepared.kind) {
          case RECTANGLE -> 0;
          case CIRCLE -> 1;
          case POLYLINE -> 2;
          case POLYGON ->
              throw new IllegalArgumentException("Polygon is handled by the legacy renderer");
        };
    int cap =
        switch (prepared.cap) {
          case BUTT -> 0;
          case ROUND -> 1;
          case SQUARE -> 2;
        };

    ShapeMaterial2D material = shape.material;
    if (material != null && prepared.kind == ShapeRenderer2D.Kind.POLYGON)
      throw new UnsupportedOperationException(
          "Polygon materials are not supported by the legacy renderer");
    ShaderProgram drawShader = material == null ? shader : shaderFor(material);
    float centerX = 0, centerY = 0, boundsX, boundsY;
    if (prepared.kind == ShapeRenderer2D.Kind.CIRCLE) {
      boundsX = boundsY = prepared.radius;
    } else if (prepared.kind == ShapeRenderer2D.Kind.POLYLINE) {
      float minX = prepared.points[0], maxX = minX;
      float minY = prepared.points[1], maxY = minY;
      for (int i = 1; i < prepared.pointCount; i++) {
        minX = Math.min(minX, prepared.points[i * 2]);
        maxX = Math.max(maxX, prepared.points[i * 2]);
        minY = Math.min(minY, prepared.points[i * 2 + 1]);
        maxY = Math.max(maxY, prepared.points[i * 2 + 1]);
      }
      centerX = minX * .5f + maxX * .5f;
      centerY = minY * .5f + maxY * .5f;
      float strokeExtent = prepared.halfStroke;
      if (prepared.cap == ShapeRenderer2D.LineCap.SQUARE) strokeExtent *= (float) Math.sqrt(2);
      boundsX = (maxX - minX) * .5f + strokeExtent;
      boundsY = (maxY - minY) * .5f + strokeExtent;
    } else {
      boundsX = prepared.halfWidth;
      boundsY = prepared.halfHeight;
    }

    batch.setProjectionMatrix(projection);
    batch.setTransformMatrix(transform);
    batch.setColor(shape.color);
    batch.setShader(drawShader);
    boolean begun = false;
    try {
      batch.begin();
      begun = true;
      uniformi(drawShader, "u_texture", 0);
      uniformi(drawShader, "u_kind", kind);
      uniformi(drawShader, "u_pointCount", prepared.pointCount);
      uniformi(drawShader, "u_cap", cap);
      uniformf(drawShader, "u_halfWidth", prepared.halfWidth);
      uniformf(drawShader, "u_halfHeight", prepared.halfHeight);
      uniformf(drawShader, "u_radius", prepared.radius);
      uniformf(drawShader, "u_cornerRadius", prepared.cornerRadius);
      uniformf(drawShader, "u_halfStroke", prepared.halfStroke);
      uniformf(drawShader, "u_antiAlias", shape.antiAlias ? 1f : 0f);
      uniformi(drawShader, "u_dashCount", prepared.dashCount);
      uniformf(drawShader, "u_dashPeriod", (float) prepared.dashPeriod);
      int pointsLocation = uniformLocation(drawShader, "u_points[0]");
      if (pointsLocation >= 0)
        drawShader.setUniform4fv(pointsLocation, packedPoints, 0, packedPoints.length);
      int dashIntervalsLocation = uniformLocation(drawShader, "u_dashIntervals[0]");
      if (dashIntervalsLocation >= 0)
        drawShader.setUniform4fv(
            dashIntervalsLocation, packedDashIntervals, 0, packedDashIntervals.length);
      int dashPhasesLocation = uniformLocation(drawShader, "u_dashStartPhase[0]");
      if (dashPhasesLocation >= 0)
        drawShader.setUniform4fv(dashPhasesLocation, packedDashPhases, 0, packedDashPhases.length);
      uniformf(drawShader, "u_shapeCenter", centerX, centerY);
      uniformf(drawShader, "u_shapeBounds", boundsX, boundsY);
      if (material != null) uploadMaterial(drawShader, material);
      batch.draw(
          whiteTexture,
          prepared.quadLeft,
          prepared.quadBottom,
          prepared.quadRight - prepared.quadLeft,
          prepared.quadTop - prepared.quadBottom);
    } finally {
      try {
        if (begun) batch.end();
      } finally {
        batch.setColor(Color.WHITE);
        batch.setShader(shader);
      }
    }
  }

  static float packDashPhase(double phase, SmoothShapeMath.Prepared prepared) {
    double cursor = 0;
    int interval = -1;
    double intervalStart = 0;
    double intervalEnd = 0;
    for (int i = 0; i < prepared.dashCount; i++) {
      double end = cursor + prepared.dashLengths[i];
      if (phase >= cursor && phase < end) {
        interval = i;
        intervalStart = cursor;
        intervalEnd = end;
        break;
      }
      cursor = end;
    }
    if (interval < 0)
      throw new IllegalArgumentException("Dash phase is outside its normalized pattern period");

    float start = (float) intervalStart;
    float end = (float) intervalEnd;
    if (!(start < end))
      throw new IllegalArgumentException("Dash interval cannot be represented by shader floats");

    boolean strictInterior = phase > intervalStart;
    float packed = (float) phase;
    if (strictInterior) {
      if (packed <= start) packed = Math.nextUp(start);
      if (packed >= end) packed = Math.nextDown(end);
      if (!(packed > start && packed < end))
        throw new IllegalArgumentException(
            "Dash interval interior cannot be represented by shader floats");
    } else {
      // Keep exact transitions on the same half-open boundary after float conversion.
      packed = start;
    }

    if (dashIntervalFloat(packed, prepared) != interval)
      throw new IllegalArgumentException(
          "Dash phase cannot be represented by shader float uniforms");
    return packed;
  }

  private static int dashIntervalFloat(float phase, SmoothShapeMath.Prepared prepared) {
    double cursor = 0;
    for (int i = 0; i < prepared.dashCount; i++) {
      float start = (float) cursor;
      cursor += prepared.dashLengths[i];
      float end = (float) cursor;
      if (phase >= start && phase < end) return i;
    }
    return -1;
  }

  private ShaderProgram shaderFor(ShapeMaterial2D material) {
    String key = material.definitionKey();
    ShaderProgram cached = materialShaders.get(key);
    if (cached != null) return cached;
    if (materialShaders.size() >= MAX_MATERIAL_SHADERS)
      throw new IllegalStateException(
          "SmoothShapeRenderer supports at most "
              + MAX_MATERIAL_SHADERS
              + " distinct material shader definitions");
    requireUniformBudget(material.uniformTypes().size());
    String marker = "vec4 shapeColor(vec2 local, vec4 tint){ return tint; }";
    String replacement = material.uniformDeclarations() + material.colorFunction();
    String fragment = FRAGMENT_SHADER.replace(marker, replacement);
    if (fragment.equals(FRAGMENT_SHADER))
      throw new IllegalStateException("Internal shape material shader insertion point is missing");
    String vertexSource = shaderSource(VERTEX_SHADER, true, gles300);
    fragment = shaderSource(fragment, false, gles300);
    preflightShader(vertexSource, fragment);
    ShaderProgram created = new ShaderProgram(vertexSource, fragment);
    if (!created.isCompiled()) {
      String log = created.getLog();
      try {
        created.dispose();
      } catch (RuntimeException | Error cleanupFailure) {
        throw new IllegalArgumentException(
            "Shape material shader compilation failed: " + log, cleanupFailure);
      }
      throw new IllegalArgumentException("Shape material shader compilation failed: " + log);
    }
    materialShaders.put(key, created);
    return created;
  }

  static PixmapTextureData managedWhiteTextureData(Pixmap pixel) {
    return new PixmapTextureData(pixel, pixel.getFormat(), false, false, true);
  }

  private static boolean isGles300Context() {
    if (Gdx.graphics == null || Gdx.graphics.getGLVersion() == null) return false;
    GLVersion version = Gdx.graphics.getGLVersion();
    if (version.getType() != GLVersion.Type.GLES) return false;
    if (version.getMajorVersion() < 3)
      throw new IllegalStateException("SmoothShapeRenderer requires OpenGL ES 3.0 or newer");
    return true;
  }

  /** Converts this renderer's GLSL 1.20 source to the GLES 3.0 shader dialect. */
  static String shaderSource(String source, boolean vertex, boolean gles300) {
    if (!gles300) return source;
    String converted = source.replaceFirst("^#version 120\\s*", "");
    converted = converted.replace("attribute ", "in ");
    converted = converted.replace("varying ", vertex ? "out " : "in ");
    if (!vertex) {
      converted = replaceGlslIdentifier(converted, "texture2D", "texture");
      converted = converted.replace("gl_FragColor", "javityFragColor");
      return "#version 300 es\nprecision highp float;\nout vec4 javityFragColor;\n" + converted;
    }
    return "#version 300 es\n" + converted;
  }

  /** Renames one GLSL identifier while preserving comments and all intervening whitespace. */
  private static String replaceGlslIdentifier(String source, String identifier, String replacement) {
    StringBuilder converted = new StringBuilder(source.length());
    for (int index = 0; index < source.length(); ) {
      char current = source.charAt(index);
      if (current == '/' && index + 1 < source.length()) {
        char next = source.charAt(index + 1);
        if (next == '/') {
          int end = source.indexOf('\n', index + 2);
          if (end < 0) end = source.length();
          converted.append(source, index, end);
          index = end;
          continue;
        }
        if (next == '*') {
          int close = source.indexOf("*/", index + 2);
          int end = close < 0 ? source.length() : close + 2;
          converted.append(source, index, end);
          index = end;
          continue;
        }
      }
      if (Character.isLetter(current) || current == '_') {
        int end = index + 1;
        while (end < source.length()) {
          char character = source.charAt(end);
          if (!Character.isLetterOrDigit(character) && character != '_') break;
          end++;
        }
        String token = source.substring(index, end);
        converted.append(token.equals(identifier) ? replacement : token);
        index = end;
        continue;
      }
      converted.append(current);
      index++;
    }
    return converted.toString();
  }

  /** The fixed SDF arrays consume a substantial fragment uniform budget on small GL drivers. */
  private static void requireUniformBudget(int customUniforms) {
    IntBuffer limit = BufferUtils.newIntBuffer(1);
    // OpenGL 2.0 names this token, but libGDX's common GL20 interface omits it.
    Gdx.gl20.glGetIntegerv(0x8B49, limit); // GL_MAX_FRAGMENT_UNIFORM_COMPONENTS
    int required = 224 + customUniforms * 4;
    if (limit.get(0) < required)
      throw new IllegalStateException(
          "SmoothShapeRenderer requires at least "
              + required
              + " fragment uniform components; driver reports "
              + limit.get(0));
  }

  /** Validates a new definition before libGDX's ShaderProgram can lose failed shader handles. */
  private static void preflightShader(String vertexSource, String fragmentSource) {
    GL20 gl = Gdx.gl20;
    if (gl == null)
      throw new IllegalStateException("Shape materials require an active OpenGL context");
    String vertexCode = prepend(ShaderProgram.prependVertexCode, vertexSource);
    String fragmentCode = prepend(ShaderProgram.prependFragmentCode, fragmentSource);
    int vertex = 0, fragment = 0, program = 0;
    Throwable failure = null;
    try {
      vertex = compile(gl, GL20.GL_VERTEX_SHADER, vertexCode, "vertex");
      fragment = compile(gl, GL20.GL_FRAGMENT_SHADER, fragmentCode, "fragment");
      program = gl.glCreateProgram();
      if (program <= 0)
        throw new IllegalStateException("OpenGL could not create a shape material program");
      gl.glAttachShader(program, vertex);
      gl.glAttachShader(program, fragment);
      gl.glLinkProgram(program);
      IntBuffer status = BufferUtils.newIntBuffer(1);
      gl.glGetProgramiv(program, GL20.GL_LINK_STATUS, status);
      if (status.get(0) == 0)
        throw new IllegalArgumentException(
            "Shape material shader link failed: " + gl.glGetProgramInfoLog(program));
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      Throwable cleanupFailure = null;
      cleanupFailure = deleteProgram(gl, program, cleanupFailure);
      cleanupFailure = deleteShader(gl, fragment, cleanupFailure);
      cleanupFailure = deleteShader(gl, vertex, cleanupFailure);
      if (cleanupFailure != null) {
        if (failure != null) failure.addSuppressed(cleanupFailure);
        else if (cleanupFailure instanceof RuntimeException e) throw e;
        else if (cleanupFailure instanceof Error e) throw e;
      }
    }
  }

  private static int compile(GL20 gl, int type, String source, String stage) {
    int shader = gl.glCreateShader(type);
    if (shader <= 0)
      throw new IllegalStateException(
          "OpenGL could not create shape material " + stage + " shader");
    try {
      gl.glShaderSource(shader, source);
      gl.glCompileShader(shader);
      IntBuffer status = BufferUtils.newIntBuffer(1);
      gl.glGetShaderiv(shader, GL20.GL_COMPILE_STATUS, status);
      if (status.get(0) == 0)
        throw new IllegalArgumentException(
            "Shape material "
                + stage
                + " shader compilation failed: "
                + gl.glGetShaderInfoLog(shader));
      return shader;
    } catch (RuntimeException | Error e) {
      try {
        gl.glDeleteShader(shader);
      } catch (RuntimeException | Error cleanupFailure) {
        e.addSuppressed(cleanupFailure);
      }
      throw e;
    }
  }

  private static String prepend(String prefix, String source) {
    return prefix == null || prefix.isEmpty() ? source : prefix + source;
  }

  private static Throwable deleteProgram(GL20 gl, int program, Throwable failure) {
    if (program <= 0) return failure;
    try {
      gl.glDeleteProgram(program);
    } catch (RuntimeException | Error e) {
      failure = accumulate(failure, e);
    }
    return failure;
  }

  private static Throwable deleteShader(GL20 gl, int shader, Throwable failure) {
    if (shader <= 0) return failure;
    try {
      gl.glDeleteShader(shader);
    } catch (RuntimeException | Error e) {
      failure = accumulate(failure, e);
    }
    return failure;
  }

  private static Throwable accumulate(Throwable failure, Throwable next) {
    if (failure == null) return next;
    failure.addSuppressed(next);
    return failure;
  }

  private static void uploadMaterial(ShaderProgram shader, ShapeMaterial2D material) {
    for (var entry : material.values().entrySet()) {
      int location = uniformLocation(shader, entry.getKey());
      if (location < 0) continue;
      ShapeMaterial2D.UniformValue value = entry.getValue();
      switch (value.type) {
        case FLOAT -> shader.setUniformf(location, value.x);
        case VEC2 -> shader.setUniformf(location, value.x, value.y);
        case VEC4 -> shader.setUniformf(location, value.x, value.y, value.z, value.w);
      }
    }
  }

  private static int uniformLocation(ShaderProgram shader, String name) {
    return shader.fetchUniformLocation(name, false);
  }

  private static void uniformi(ShaderProgram shader, String name, int value) {
    int location = uniformLocation(shader, name);
    if (location >= 0) shader.setUniformi(location, value);
  }

  private static void uniformf(ShaderProgram shader, String name, float value) {
    int location = uniformLocation(shader, name);
    if (location >= 0) shader.setUniformf(location, value);
  }

  private static void uniformf(ShaderProgram shader, String name, float x, float y) {
    int location = uniformLocation(shader, name);
    if (location >= 0) shader.setUniformf(location, x, y);
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    Throwable failure = null;
    failure = dispose(batch, failure);
    failure = dispose(whiteTexture, failure);
    failure = dispose(whitePixel, failure);
    failure = dispose(shader, failure);
    for (ShaderProgram materialShader : materialShaders.values())
      failure = dispose(materialShader, failure);
    materialShaders.clear();
    if (failure instanceof RuntimeException e) throw e;
    if (failure instanceof Error e) throw e;
  }

  private static Throwable dispose(Disposable resource, Throwable failure) {
    try {
      resource.dispose();
    } catch (RuntimeException | Error e) {
      if (failure == null) return e;
      failure.addSuppressed(e);
    }
    return failure;
  }
}
