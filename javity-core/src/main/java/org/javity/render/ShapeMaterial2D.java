package org.javity.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** CPU-side SDF shape color function and its typed, per-material uniform values. */
public final class ShapeMaterial2D {
  public enum UniformType {
    FLOAT,
    VEC2,
    VEC4
  }

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Set<String> RESERVED =
      Set.of(
          "u_texture",
          "u_kind",
          "u_pointCount",
          "u_cap",
          "u_halfWidth",
          "u_halfHeight",
          "u_radius",
          "u_cornerRadius",
          "u_halfStroke",
          "u_antiAlias",
          "u_points",
          "u_dashCount",
          "u_dashPeriod",
          "u_dashIntervals",
          "u_dashStartPhase",
          "u_shapeCenter",
          "u_shapeBounds",
          "u_projTrans",
          "a_position",
          "a_color",
          "a_texCoord0",
          "v_color",
          "v_local",
          "v_texCoords");
  private static final Set<String> GLSL_KEYWORDS =
      Set.of(
          "attribute",
          "const",
          "uniform",
          "varying",
          "break",
          "continue",
          "do",
          "for",
          "if",
          "else",
          "in",
          "out",
          "inout",
          "float",
          "int",
          "void",
          "bool",
          "true",
          "false",
          "invariant",
          "discard",
          "return",
          "mat2",
          "mat3",
          "mat4",
          "vec2",
          "vec3",
          "vec4",
          "ivec2",
          "ivec3",
          "ivec4",
          "bvec2",
          "bvec3",
          "bvec4",
          "sampler1D",
          "sampler2D",
          "sampler3D",
          "samplerCube",
          "struct");
  private static final float MAX_PARAMETER = 10_000f;

  static final class UniformValue {
    final UniformType type;
    float x, y, z, w;

    UniformValue(UniformType type) {
      this.type = type;
    }
  }

  private final String colorFunction;
  private final Map<String, UniformType> uniformTypes;
  private final Map<String, UniformValue> values;
  private final String definitionKey;
  private final String uniformDeclarations;

  private ShapeMaterial2D(String colorFunction, Map<String, UniformType> schema) {
    this.colorFunction = validateFunction(colorFunction);
    Objects.requireNonNull(schema, "uniform schema");
    LinkedHashMap<String, UniformType> types = new LinkedHashMap<>();
    LinkedHashMap<String, UniformValue> initialValues = new LinkedHashMap<>();
    for (var entry : schema.entrySet()) {
      String name = entry.getKey();
      UniformType type = entry.getValue();
      validateUniformName(name);
      if (RESERVED.contains(name))
        throw new IllegalArgumentException(
            "Uniform name is reserved by the shape renderer: " + name);
      if (type == null) throw new IllegalArgumentException("Uniform type is required: " + name);
      if (types.putIfAbsent(name, type) != null)
        throw new IllegalArgumentException("Duplicate uniform name: " + name);
      initialValues.put(name, new UniformValue(type));
    }
    uniformTypes = Collections.unmodifiableMap(types);
    values = Collections.unmodifiableMap(initialValues);
    StringBuilder declarations = new StringBuilder();
    types.keySet().stream()
        .sorted()
        .forEach(
            name -> {
              declarations.append("uniform ");
              declarations.append(
                  switch (types.get(name)) {
                    case FLOAT -> "float ";
                    case VEC2 -> "vec2 ";
                    case VEC4 -> "vec4 ";
                  });
              declarations.append(name).append(";\n");
            });
    uniformDeclarations = declarations.toString();
    definitionKey = colorFunction + '\u0000' + uniformDeclarations;
  }

  /**
   * Creates a CPU-only custom material. The function is GLSL 1.20 and must declare {@code vec4
   * shapeColor(vec2 local, vec4 tint)}. Declared uniforms start at zero.
   */
  public static ShapeMaterial2D custom(String colorFunction, Map<String, UniformType> schema) {
    return new ShapeMaterial2D(colorFunction, schema);
  }

  /** Creates a linear gradient spanning the shape along {@code angleRadians}. */
  public static ShapeMaterial2D gradient(Color start, Color end, float angleRadians) {
    finiteColor(start, "start");
    finiteColor(end, "end");
    bounded(angleRadians, "angleRadians", -MAX_PARAMETER, MAX_PARAMETER);
    ShapeMaterial2D material =
        custom(
            "vec4 shapeColor(vec2 local, vec4 tint) { vec2"
                + " direction=vec2(cos(u_angle),sin(u_angle)); float"
                + " extent=dot(abs(direction),u_shapeBounds); float"
                + " t=clamp(0.5+dot(local-u_shapeCenter,direction)/(2.0*max(extent,0.000001)),0.0,1.0);"
                + " return mix(u_colorA,u_colorB,t)*tint; }",
            Map.of(
                "u_colorA", UniformType.VEC4,
                "u_colorB", UniformType.VEC4,
                "u_angle", UniformType.FLOAT));
    material
        .setColor("u_colorA", start)
        .setColor("u_colorB", end)
        .setFloat("u_angle", angleRadians);
    return material;
  }

  /** Creates a sinusoidal color blend; {@code rateHz} is cycles per second. */
  public static ShapeMaterial2D pulse(Color base, Color pulse, float rateHz) {
    finiteColor(base, "base");
    finiteColor(pulse, "pulse");
    bounded(rateHz, "rateHz", 0, MAX_PARAMETER);
    if (rateHz == 0) throw new IllegalArgumentException("rateHz must be positive");
    ShapeMaterial2D material =
        custom(
            "vec4 shapeColor(vec2 local, vec4 tint) {"
                + " float amount=0.5+0.5*sin(6.2831853*u_time*u_rate);"
                + " return mix(u_colorA,u_colorB,amount)*tint; }",
            Map.of(
                "u_colorA", UniformType.VEC4,
                "u_colorB", UniformType.VEC4,
                "u_time", UniformType.FLOAT,
                "u_rate", UniformType.FLOAT));
    material.setColor("u_colorA", base).setColor("u_colorB", pulse).setFloat("u_rate", rateHz);
    return material;
  }

  /** Creates moving vertical bands; frequency is cycles/local unit and speed is cycles/second. */
  public static ShapeMaterial2D stripes(Color a, Color b, float frequency, float speed) {
    finiteColor(a, "a");
    finiteColor(b, "b");
    bounded(frequency, "frequency", 0, MAX_PARAMETER);
    if (frequency == 0) throw new IllegalArgumentException("frequency must be positive");
    bounded(speed, "speed", -MAX_PARAMETER, MAX_PARAMETER);
    ShapeMaterial2D material =
        custom(
            "vec4 shapeColor(vec2 local, vec4 tint) {"
                + " float band=step(0.5,fract(local.x*u_frequency+u_time*u_speed));"
                + " return mix(u_colorA,u_colorB,band)*tint; }",
            Map.of(
                "u_colorA", UniformType.VEC4,
                "u_colorB", UniformType.VEC4,
                "u_frequency", UniformType.FLOAT,
                "u_speed", UniformType.FLOAT,
                "u_time", UniformType.FLOAT));
    material
        .setColor("u_colorA", a)
        .setColor("u_colorB", b)
        .setFloat("u_frequency", frequency)
        .setFloat("u_speed", speed);
    return material;
  }

  public ShapeMaterial2D setFloat(String name, float value) {
    finite(value, name);
    require(name, UniformType.FLOAT).x = value;
    return this;
  }

  public ShapeMaterial2D setVector2(String name, Vector2 value) {
    Objects.requireNonNull(value, "value");
    return setVector2(name, value.x, value.y);
  }

  public ShapeMaterial2D setVector2(String name, float x, float y) {
    finite(x, name);
    finite(y, name);
    UniformValue value = require(name, UniformType.VEC2);
    value.x = x;
    value.y = y;
    return this;
  }

  public ShapeMaterial2D setColor(String name, Color color) {
    finiteColor(color, name);
    UniformValue value = require(name, UniformType.VEC4);
    value.x = color.r;
    value.y = color.g;
    value.z = color.b;
    value.w = color.a;
    return this;
  }

  /** Returns an immutable view of the uniform schema. */
  public Map<String, UniformType> uniformTypes() {
    return uniformTypes;
  }

  public UniformType uniformType(String name) {
    UniformType type = uniformTypes.get(name);
    if (type == null) throw new IllegalArgumentException("Unknown material uniform: " + name);
    return type;
  }

  public float getFloat(String name) {
    return require(name, UniformType.FLOAT).x;
  }

  public Vector2 getVector2(String name) {
    UniformValue value = require(name, UniformType.VEC2);
    return new Vector2(value.x, value.y);
  }

  public Color getColor(String name) {
    UniformValue value = require(name, UniformType.VEC4);
    return new Color(value.x, value.y, value.z, value.w);
  }

  String colorFunction() {
    return colorFunction;
  }

  Map<String, UniformValue> values() {
    return values;
  }

  String definitionKey() {
    return definitionKey;
  }

  String uniformDeclarations() {
    return uniformDeclarations;
  }

  private UniformValue require(String name, UniformType expected) {
    UniformValue value = values.get(name);
    if (value == null) throw new IllegalArgumentException("Unknown material uniform: " + name);
    if (value.type != expected)
      throw new IllegalArgumentException(
          "Uniform " + name + " has type " + value.type + ", expected " + expected);
    return value;
  }

  private static String validateFunction(String source) {
    Objects.requireNonNull(source, "colorFunction");
    String value = source.trim();
    if (value.isEmpty()
        || value.contains("#version")
        || value.contains("void main")
        || value.matches("(?s).*\\buniform\\b.*")
        || !value.matches(
            "(?s).*\\bvec4\\s+shapeColor\\s*\\(\\s*vec2\\s+local\\s*,\\s*vec4\\s+tint\\s*\\).*"))
      throw new IllegalArgumentException(
          "Shape material must define vec4 shapeColor(vec2 local, vec4 tint) in GLSL 1.20 context");
    return value;
  }

  private static void validateUniformName(String name) {
    if (name == null
        || !IDENTIFIER.matcher(name).matches()
        || GLSL_KEYWORDS.contains(name)
        || name.startsWith("gl_"))
      throw new IllegalArgumentException("Invalid GLSL uniform identifier: " + name);
  }

  private static void finiteColor(Color color, String name) {
    Objects.requireNonNull(color, name);
    finite(color.r, name);
    finite(color.g, name);
    finite(color.b, name);
    finite(color.a, name);
  }

  private static void bounded(float value, String name, float min, float max) {
    finite(value, name);
    if (value < min || value > max)
      throw new IllegalArgumentException(name + " must be in [" + min + ", " + max + "]");
  }

  private static void finite(float value, String name) {
    if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
  }
}
