package org.javity.render;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Matrix4;
import java.util.*;

/** Exact immutable snapshots. Names and hash collisions never determine compatibility. */
final class InstanceGroupKey {
  private final List<Object> values;

  private InstanceGroupKey(List<Object> values) {
    this.values = List.copyOf(values);
  }

  static InstanceGroupKey from(Renderable r, Environment environment) {
    String reason = rejectionReason(r, environment);
    if (reason != null) throw new IllegalArgumentException(reason);
    List<Object> values = new ArrayList<>();
    values.add(r.meshPart.mesh);
    values.add(r.meshPart.offset);
    values.add(r.meshPart.size);
    values.add(r.meshPart.primitiveType);
    for (VertexAttribute a : r.meshPart.mesh.getVertexAttributes())
      values.add(
          List.of(a.usage, a.numComponents, a.type, a.normalized, a.offset, a.unit, a.alias));
    snapshot(r.material, values);
    if (environment != null) snapshot(environment, values);
    return new InstanceGroupKey(values);
  }

  static String rejectionReason(Renderable r, Environment e) {
    if (r.shader != null) return "CUSTOM_SHADER";
    if (r.bones != null && r.bones.length > 0) return "SKINNED_GEOMETRY";
    if (r.meshPart.mesh == null
        || r.meshPart.size <= 0
        || r.meshPart.primitiveType != GL20.GL_TRIANGLES) return "UNSUPPORTED_GEOMETRY";
    String transform = transformRejection(r.worldTransform);
    if (transform != null) return transform;
    boolean position = false, normal = false, uv = false;
    for (VertexAttribute a : r.meshPart.mesh.getVertexAttributes()) {
      if (a.type != GL20.GL_FLOAT || a.normalized) return "UNSUPPORTED_VERTEX_LAYOUT";
      if (a.usage == VertexAttributes.Usage.Position && a.numComponents == 3) position = true;
      else if (a.usage == VertexAttributes.Usage.Normal && a.numComponents == 3) normal = true;
      else if (a.usage == VertexAttributes.Usage.TextureCoordinates
          && a.unit == 0
          && a.numComponents == 2) uv = true;
      else return "UNSUPPORTED_VERTEX_LAYOUT";
    }
    if (!position || !normal) return "MISSING_POSITION_OR_NORMAL";
    if (r.material == null) return "MISSING_MATERIAL";
    DepthTestAttribute depth = (DepthTestAttribute) r.material.get(DepthTestAttribute.Type);
    if (depth != null
        && (!depth.depthMask
            || (depth.depthFunc != GL20.GL_LESS && depth.depthFunc != GL20.GL_LEQUAL)))
      return "ORDER_DEPENDENT_DEPTH_STATE";
    BlendingAttribute blending = (BlendingAttribute) r.material.get(BlendingAttribute.Type);
    if (blending != null && (blending.blended || blending.opacity != 1f)) return "ALPHA_BLENDING";
    for (Attribute a : r.material) {
      if (a.type == BlendingAttribute.Type
          || a.type == ColorAttribute.Diffuse
          || a.type == FloatAttribute.AlphaTest
          || a.type == IntAttribute.CullFace
          || a.type == DepthTestAttribute.Type) continue;
      if (a.type == TextureAttribute.Diffuse) {
        TextureAttribute t = (TextureAttribute) a;
        if (!uv || t.uvIndex != 0 || t.textureDescription.texture == null)
          return "UNSUPPORTED_DIFFUSE_TEXTURE";
        continue;
      }
      return "UNSUPPORTED_MATERIAL_ATTRIBUTE:" + a.type;
    }
    ColorAttribute color = (ColorAttribute) r.material.get(ColorAttribute.Diffuse);
    if (color != null && color.color.a != 1f && !r.material.has(FloatAttribute.AlphaTest))
      return "TRANSLUCENT_DIFFUSE";
    if (e != null) {
      if (e.shadowMap != null) return "SHADOW_MAP";
      for (Attribute a : e) {
        if (a.type == ColorAttribute.AmbientLight) continue;
        if (a.type == DirectionalLightsAttribute.Type
            && ((DirectionalLightsAttribute) a).lights.size <= 2) continue;
        return "UNSUPPORTED_ENVIRONMENT_ATTRIBUTE:" + a.type;
      }
    }
    return null;
  }

  static String transformRejection(Matrix4 m) {
    float[] v = m.val;
    for (float x : v) if (!Float.isFinite(x)) return "NONFINITE_TRANSFORM";
    if (v[3] != 0 || v[7] != 0 || v[11] != 0 || v[15] != 1) return "NONAFFINE_TRANSFORM";
    double a = dot(v, 0, 0),
        b = dot(v, 4, 4),
        c = dot(v, 8, 8),
        scale = Math.max(a, Math.max(b, c));
    if (a <= 0 || b <= 0 || c <= 0) return "DEGENERATE_TRANSFORM";
    if (Math.abs(a - b) > scale * 1e-5 || Math.abs(a - c) > scale * 1e-5) return "NONUNIFORM_SCALE";
    if (Math.abs(dot(v, 0, 4)) > scale * 1e-5
        || Math.abs(dot(v, 0, 8)) > scale * 1e-5
        || Math.abs(dot(v, 4, 8)) > scale * 1e-5) return "SHEARED_TRANSFORM";
    double determinant =
        (double) v[0] * ((double) v[5] * v[10] - (double) v[6] * v[9])
            - (double) v[4] * ((double) v[1] * v[10] - (double) v[2] * v[9])
            + (double) v[8] * ((double) v[1] * v[6] - (double) v[2] * v[5]);
    return determinant <= 0 ? "MIRRORED_TRANSFORM" : null;
  }

  private static double dot(float[] v, int a, int b) {
    return (double) v[a] * v[b] + (double) v[a + 1] * v[b + 1] + (double) v[a + 2] * v[b + 2];
  }

  private static void color(Color c, List<Object> out) {
    out.add(c.r);
    out.add(c.g);
    out.add(c.b);
    out.add(c.a);
  }

  private static void snapshot(Attributes attributes, List<Object> out) {
    out.add("attributes");
    for (Attribute a : attributes) {
      out.add(a.type);
      if (a instanceof BlendingAttribute b) {
        out.add(b.blended);
        out.add(b.sourceFunction);
        out.add(b.destFunction);
        out.add(b.opacity);
      } else if (a instanceof ColorAttribute c) color(c.color, out);
      else if (a instanceof FloatAttribute f) out.add(f.value);
      else if (a instanceof IntAttribute i) out.add(i.value);
      else if (a instanceof DepthTestAttribute d) {
        out.add(d.depthFunc);
        out.add(d.depthRangeNear);
        out.add(d.depthRangeFar);
        out.add(d.depthMask);
      } else if (a instanceof TextureAttribute t) {
        var d = t.textureDescription;
        out.add(d.texture);
        out.add(Arrays.asList(d.minFilter, d.magFilter, d.uWrap, d.vWrap));
        out.add(t.offsetU);
        out.add(t.offsetV);
        out.add(t.scaleU);
        out.add(t.scaleV);
        out.add(t.uvIndex);
      } else if (a instanceof DirectionalLightsAttribute d)
        for (DirectionalLight l : d.lights) {
          color(l.color, out);
          out.add(l.direction.x);
          out.add(l.direction.y);
          out.add(l.direction.z);
        }
    }
    out.add("end");
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof InstanceGroupKey k && values.equals(k.values);
  }

  @Override
  public int hashCode() {
    return values.hashCode();
  }
}
