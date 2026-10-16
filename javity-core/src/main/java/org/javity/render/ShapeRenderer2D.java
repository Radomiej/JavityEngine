package org.javity.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import java.util.*;

/**
 * Basic world figures, not full Shapes material API. Points copied; caller supplies valid simple
 * polygons.
 */
public final class ShapeRenderer2D extends Render2D {
  public enum Kind {
    RECTANGLE,
    CIRCLE,
    POLYLINE,
    POLYGON
  }

  public enum LineCap {
    BUTT,
    ROUND,
    SQUARE
  }

  public enum LineJoin {
    ROUND
  }

  public enum Space {
    WORLD,
    SCREEN
  }

  public final Kind kind;
  public final float width, height, radius;
  public float strokeWidth;
  public final Color color = Color.WHITE.cpy();

  /** Optional SDF color material; polygon rendering rejects this legacy-only path. */
  public ShapeMaterial2D material;

  /** Enables shader edge smoothing for supported shapes; polygons retain their legacy fill. */
  public boolean antiAlias;

  /** Rounded rectangle corner radius in local shape units. */
  public float cornerRadius = 0;

  /** Endpoint style for polylines. */
  public LineCap lineCap = LineCap.ROUND;

  /** Join style for polylines. */
  public LineJoin lineJoin = LineJoin.ROUND;

  /** Coordinate space used by the renderer. */
  public Space space = Space.WORLD;

  private DashPattern2D dashPattern;
  private double dashPhase;

  private final List<Vector2> points;
  final float[] vertices;
  final com.badlogic.gdx.utils.ShortArray triangles;

  private ShapeRenderer2D(
      Kind kind, float width, float height, float radius, List<Vector2> points) {
    this.kind = kind;
    this.antiAlias = kind != Kind.POLYGON;
    this.width = width;
    this.height = height;
    this.radius = radius;
    this.points =
        points.stream()
            .map(
                p -> {
                  if (!Float.isFinite(p.x) || !Float.isFinite(p.y))
                    throw new IllegalArgumentException("Invalid point");
                  return p.cpy();
                })
            .toList();
    vertices = new float[points.size() * 2];
    for (int i = 0; i < points.size(); i++) {
      vertices[i * 2] = points.get(i).x;
      vertices[i * 2 + 1] = points.get(i).y;
    }
    triangles =
        kind == Kind.POLYGON
            ? new com.badlogic.gdx.math.EarClippingTriangulator().computeTriangles(vertices)
            : new com.badlogic.gdx.utils.ShortArray();
  }

  private static void positive(float value) {
    if (!Float.isFinite(value) || value <= 0)
      throw new IllegalArgumentException("Positive size required");
  }

  public static ShapeRenderer2D rectangle(float width, float height) {
    positive(width);
    positive(height);
    return new ShapeRenderer2D(Kind.RECTANGLE, width, height, 0, List.of());
  }

  public static ShapeRenderer2D circle(float radius) {
    positive(radius);
    return new ShapeRenderer2D(Kind.CIRCLE, 0, 0, radius, List.of());
  }

  public static ShapeRenderer2D polyline(List<Vector2> points, float width) {
    if (points.size() < 2) throw new IllegalArgumentException("2 points required");
    positive(width);
    var shape = new ShapeRenderer2D(Kind.POLYLINE, 0, 0, 0, points);
    shape.strokeWidth = width;
    return shape;
  }

  public static ShapeRenderer2D polygon(List<Vector2> points) {
    if (points.size() < 3 || points.size() > 32767)
      throw new IllegalArgumentException("3..32767 points required");
    return new ShapeRenderer2D(Kind.POLYGON, 0, 0, 0, points);
  }

  public List<Vector2> getPoints() {
    return points.stream().map(Vector2::cpy).toList();
  }

  /**
   * Applies an immutable local-path dash pattern. Positive phase advances ink along point order.
   */
  public ShapeRenderer2D setDashes(DashPattern2D pattern, double phase) {
    if (kind != Kind.POLYLINE)
      throw new IllegalStateException("Dashes are supported only for polylines");
    if (pattern == null)
      throw new IllegalArgumentException("Dash pattern is required; use clearDashes");
    if (!Double.isFinite(phase)) throw new IllegalArgumentException("Dash phase must be finite");
    dashPattern = pattern;
    dashPhase = floorModulo(phase, pattern.periodDouble());
    return this;
  }

  /** Changes phase without changing the immutable pattern or renderer shader definition. */
  public ShapeRenderer2D setDashPhase(double phase) {
    if (dashPattern == null) throw new IllegalStateException("Shape has no dash pattern");
    if (!Double.isFinite(phase)) throw new IllegalArgumentException("Dash phase must be finite");
    dashPhase = floorModulo(phase, dashPattern.periodDouble());
    return this;
  }

  public ShapeRenderer2D clearDashes() {
    dashPattern = null;
    dashPhase = 0;
    return this;
  }

  public DashPattern2D dashPattern() {
    return dashPattern;
  }

  /** Normalized phase in [0, period), measured in local path units. */
  public double dashPhase() {
    return dashPhase;
  }

  double dashPhaseDouble() {
    return dashPhase;
  }

  /**
   * Validates path-dependent dash point, float-precision, and repeat limits without creating GL
   * resources.
   */
  public void validateDashConfiguration() {
    if (dashPattern == null) return;
    if (kind != Kind.POLYLINE)
      throw new IllegalArgumentException("Dashes are supported only for polylines");
    List<Vector2> effective = new ArrayList<>();
    for (Vector2 point : points) {
      if (!Float.isFinite(point.x) || !Float.isFinite(point.y))
        throw new IllegalArgumentException("Polyline points must be finite");
      if (effective.isEmpty()
          || point.x != effective.get(effective.size() - 1).x
          || point.y != effective.get(effective.size() - 1).y) {
        if (effective.size() == 64)
          throw new IllegalArgumentException("Polyline exceeds 64 effective points");
        effective.add(point);
      }
    }
    float smallest = Float.POSITIVE_INFINITY;
    for (float length : dashPattern.lengths()) smallest = Math.min(smallest, length);
    double period = dashPattern.periodDouble();
    if (smallest <= 8f * Math.ulp((float) period))
      throw new IllegalArgumentException("Dash pattern is below shader float precision");
    for (int i = 1; i < effective.size(); i++) {
      Vector2 a = effective.get(i - 1), b = effective.get(i);
      double length = Math.hypot((double) b.x - a.x, (double) b.y - a.y);
      if (!Double.isFinite(length) || length <= 0 || length > Float.MAX_VALUE)
        throw new IllegalArgumentException(
            "Polyline segment length is not representable as a float");
      if (smallest <= 8f * Math.ulp((float) Math.max(length, period))
          || length / period > 1_000_000d)
        throw new IllegalArgumentException(
            "Dash pattern is below shader precision or exceeds 1,000,000 cycles per segment");
    }
  }

  private static double floorModulo(double value, double period) {
    double result = value % period;
    if (result < 0) result += period;
    if (result >= period) result = Math.nextDown(period);
    return result;
  }
}
