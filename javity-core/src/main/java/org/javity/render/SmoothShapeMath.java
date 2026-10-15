package org.javity.render;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import java.util.List;

/** CPU geometry preparation shared by the SDF renderer and its pure tests. */
final class SmoothShapeMath {
  static final int MAX_POINTS = 64;
  static final float FRINGE_PIXELS = 1f;
  // Leaves headroom for GLSL dot products on points within the padded quad.
  private static final double MAX_SAFE_SQUARED_DISTANCE = Float.MAX_VALUE * 0.5d;
  private static final float EPSILON = 1e-6f;

  private SmoothShapeMath() {}

  static final class Prepared {
    final ShapeRenderer2D.Kind kind;
    final float[] points;
    final int pointCount;
    final float halfWidth, halfHeight, radius, cornerRadius, halfStroke;
    final ShapeRenderer2D.LineCap cap;
    final boolean hasArea;
    final float left, bottom, right, top;
    final float quadLeft, quadBottom, quadRight, quadTop;

    Prepared(
        ShapeRenderer2D.Kind kind,
        float[] points,
        int pointCount,
        float halfWidth,
        float halfHeight,
        float radius,
        float cornerRadius,
        float halfStroke,
        ShapeRenderer2D.LineCap cap,
        boolean hasArea,
        float left,
        float bottom,
        float right,
        float top,
        float quadLeft,
        float quadBottom,
        float quadRight,
        float quadTop) {
      this.kind = kind;
      this.points = points;
      this.pointCount = pointCount;
      this.halfWidth = halfWidth;
      this.halfHeight = halfHeight;
      this.radius = radius;
      this.cornerRadius = cornerRadius;
      this.halfStroke = halfStroke;
      this.cap = cap;
      this.hasArea = hasArea;
      this.left = left;
      this.bottom = bottom;
      this.right = right;
      this.top = top;
      this.quadLeft = quadLeft;
      this.quadBottom = quadBottom;
      this.quadRight = quadRight;
      this.quadTop = quadTop;
    }
  }

  /** Returns null for a singular or unsupported 2D-to-pixel transform. */
  static Prepared prepare(
      ShapeRenderer2D shape,
      Matrix4 projection,
      Matrix4 transform,
      int viewportWidth,
      int viewportHeight) {
    if (shape == null || projection == null || transform == null)
      throw new IllegalArgumentException("Shape and matrices are required");
    if (viewportWidth <= 0 || viewportHeight <= 0) return null;
    if (!finite(shape.strokeWidth) || shape.strokeWidth < 0)
      throw new IllegalArgumentException("Stroke width must be finite and nonnegative");
    if (!finite(shape.cornerRadius) || shape.cornerRadius < 0)
      throw new IllegalArgumentException("Corner radius must be finite and nonnegative");
    if (shape.lineCap == null || shape.lineJoin == null)
      throw new IllegalArgumentException("Line cap and join are required");
    if (!finite(shape.color.r, shape.color.g, shape.color.b, shape.color.a))
      throw new IllegalArgumentException("Shape color must be finite");

    Matrix4 combined = new Matrix4(projection).mul(transform);
    float[] matrix = combined.val;
    for (float value : matrix) if (!finite(value)) return null;
    if (matrix[Matrix4.M30] != 0f
        || matrix[Matrix4.M31] != 0f
        || matrix[Matrix4.M32] != 0f
        || Math.abs(matrix[Matrix4.M33]) <= EPSILON) return null;

    float w = matrix[Matrix4.M33];
    float j00 = .5f * viewportWidth * matrix[Matrix4.M00] / w;
    float j01 = .5f * viewportWidth * matrix[Matrix4.M01] / w;
    float j10 = .5f * viewportHeight * matrix[Matrix4.M10] / w;
    float j11 = .5f * viewportHeight * matrix[Matrix4.M11] / w;
    float det = j00 * j11 - j01 * j10;
    float scale =
        Math.max(
            Math.max(Math.abs(j00), Math.abs(j01)),
            Math.max(Math.abs(j10), Math.abs(j11)));
    if (!finite(det) || scale == 0 || Math.abs(det) <= 1e-12f * scale * scale) return null;
    float inv00 = j11 / det, inv01 = -j01 / det;
    float inv10 = -j10 / det, inv11 = j00 / det;
    float padX = FRINGE_PIXELS * (Math.abs(inv00) + Math.abs(inv01));
    float padY = FRINGE_PIXELS * (Math.abs(inv10) + Math.abs(inv11));

    float halfStroke = shape.strokeWidth * .5f;
    float halfWidth = 0, halfHeight = 0, radius = 0, cornerRadius = 0;
    float[] points = new float[MAX_POINTS * 2];
    int count = 0;
    double left, bottom, right, top;
    boolean hasArea = true;
    switch (shape.kind) {
      case RECTANGLE -> {
        if (!finite(shape.width, shape.height) || shape.width <= 0 || shape.height <= 0)
          throw new IllegalArgumentException("Rectangle dimensions must be finite and positive");
        halfWidth = shape.width * .5f;
        halfHeight = shape.height * .5f;
        cornerRadius = Math.min(shape.cornerRadius, Math.min(halfWidth, halfHeight));
        double extent = halfStroke;
        left = -halfWidth - extent;
        right = halfWidth + extent;
        bottom = -halfHeight - extent;
        top = halfHeight + extent;
      }
      case CIRCLE -> {
        if (!finite(shape.radius) || shape.radius <= 0)
          throw new IllegalArgumentException("Circle radius must be finite and positive");
        radius = shape.radius;
        double extent = (double) radius + halfStroke;
        left = bottom = -extent;
        right = top = extent;
      }
      case POLYLINE -> {
        if (shape.strokeWidth <= 0)
          throw new IllegalArgumentException("Polyline stroke must be positive");
        List<Vector2> source = shape.getPoints();
        for (Vector2 point : source) {
          if (!finite(point.x, point.y))
            throw new IllegalArgumentException("Polyline points must be finite");
          if (count == 0
              || point.x != points[(count - 1) * 2]
              || point.y != points[(count - 1) * 2 + 1]) {
            if (count == MAX_POINTS)
              throw new IllegalArgumentException("Polyline exceeds 64 effective points");
            points[count * 2] = point.x;
            points[count * 2 + 1] = point.y;
            count++;
          }
        }
        if (count == 0) throw new IllegalArgumentException("Polyline requires points");
        hasArea = count > 1 || shape.lineCap == ShapeRenderer2D.LineCap.ROUND;
        float minX = points[0], maxX = points[0], minY = points[1], maxY = points[1];
        for (int i = 1; i < count; i++) {
          validateSegment(
              points[(i - 1) * 2],
              points[(i - 1) * 2 + 1],
              points[i * 2],
              points[i * 2 + 1]);
          minX = Math.min(minX, points[i * 2]);
          maxX = Math.max(maxX, points[i * 2]);
          minY = Math.min(minY, points[i * 2 + 1]);
          maxY = Math.max(maxY, points[i * 2 + 1]);
        }
        double capExtra =
            count > 1 && shape.lineCap == ShapeRenderer2D.LineCap.SQUARE
                ? Math.sqrt(2) * halfStroke
                : 0;
        double extent = halfStroke + capExtra;
        left = minX - extent;
        right = maxX + extent;
        bottom = minY - extent;
        top = maxY + extent;
      }
      case POLYGON -> { return null; }
      default -> { return null; }
    }
    float leftF = representableFloat(left, "Shape left bound");
    float bottomF = representableFloat(bottom, "Shape bottom bound");
    float rightF = representableFloat(right, "Shape right bound");
    float topF = representableFloat(top, "Shape top bound");
    double quadLeftD = left - padX;
    double quadBottomD = bottom - padY;
    double quadRightD = right + padX;
    double quadTopD = top + padY;
    double quadWidth = quadRightD - quadLeftD;
    double quadHeight = quadTopD - quadBottomD;
    if (quadWidth > Float.MAX_VALUE || quadHeight > Float.MAX_VALUE)
      throw new IllegalArgumentException("Shape quad dimensions exceed finite float range");
    double quadDiagonalSquared = quadWidth * quadWidth + quadHeight * quadHeight;
    if (!Double.isFinite(quadDiagonalSquared)
        || quadDiagonalSquared > MAX_SAFE_SQUARED_DISTANCE)
      throw new IllegalArgumentException("Shape quad is too large for finite shader distance math");
    float quadLeft = representableFloat(quadLeftD, "Shape quad left bound");
    float quadBottom = representableFloat(quadBottomD, "Shape quad bottom bound");
    float quadRight = representableFloat(quadRightD, "Shape quad right bound");
    float quadTop = representableFloat(quadTopD, "Shape quad top bound");
    double submittedWidth = (double) quadRight - quadLeft;
    double submittedHeight = (double) quadTop - quadBottom;
    if (submittedWidth > Float.MAX_VALUE || submittedHeight > Float.MAX_VALUE)
      throw new IllegalArgumentException("Rounded shape quad dimensions exceed finite float range");
    if (hasArea && (submittedWidth <= 0 || submittedHeight <= 0))
      throw new IllegalArgumentException("Shape quad collapses after float rounding");
    double submittedDiagonalSquared =
        submittedWidth * submittedWidth + submittedHeight * submittedHeight;
    if (!Double.isFinite(submittedDiagonalSquared)
        || submittedDiagonalSquared > MAX_SAFE_SQUARED_DISTANCE)
      throw new IllegalArgumentException(
          "Rounded shape quad is too large for finite shader distance math");
    return new Prepared(
        shape.kind,
        points,
        count,
        halfWidth,
        halfHeight,
        radius,
        cornerRadius,
        halfStroke,
        shape.lineCap,
        hasArea,
        leftF,
        bottomF,
        rightF,
        topF,
        quadLeft,
        quadBottom,
        quadRight,
        quadTop);
  }

  private static void validateSegment(float x0, float y0, float x1, float y1) {
    double dx = (double) x1 - x0;
    double dy = (double) y1 - y0;
    double lengthSquared = dx * dx + dy * dy;
    double length = Math.hypot(dx, dy);
    float floatDx = (float) dx;
    float floatDy = (float) dy;
    float floatLengthSquared = floatDx * floatDx + floatDy * floatDy;
    if (!representable(dx)
        || !representable(dy)
        || !representable(length)
        || !Double.isFinite(lengthSquared)
        || lengthSquared > MAX_SAFE_SQUARED_DISTANCE
        || !Float.isFinite(floatLengthSquared)
        || floatLengthSquared == 0f)
      throw new IllegalArgumentException("Polyline segment geometry exceeds finite float range");
    float floatLength = (float) length;
    if (!Float.isFinite(floatLength) || floatLength == 0f)
      throw new IllegalArgumentException("Polyline segment length is not representable as a float");
  }

  private static float representableFloat(double value, String name) {
    if (!representable(value))
      throw new IllegalArgumentException(name + " exceeds finite float range");
    return (float) value;
  }

  private static boolean representable(double value) {
    return Double.isFinite(value) && Math.abs(value) <= Float.MAX_VALUE;
  }

  /** Signed local-space distance. Negative values are inside the filled shape. */
  static float distance(Prepared shape, float x, float y) {
    if (!shape.hasArea) return Float.POSITIVE_INFINITY;
    return switch (shape.kind) {
      case CIRCLE -> outline((float) Math.hypot(x, y) - shape.radius, shape.halfStroke);
      case RECTANGLE ->
          outline(
              roundedBox(x, y, shape.halfWidth, shape.halfHeight, shape.cornerRadius),
              shape.halfStroke);
      case POLYLINE -> polylineDistance(shape, x, y);
      case POLYGON -> Float.POSITIVE_INFINITY;
    };
  }

  private static float outline(float contourDistance, float halfStroke) {
    return halfStroke == 0 ? contourDistance : Math.abs(contourDistance) - halfStroke;
  }

  private static float roundedBox(float x, float y, float hx, float hy, float radius) {
    float qx = Math.abs(x) - hx + radius;
    float qy = Math.abs(y) - hy + radius;
    return (float) Math.hypot(Math.max(qx, 0), Math.max(qy, 0))
        + Math.min(Math.max(qx, qy), 0) - radius;
  }

  private static float polylineDistance(Prepared shape, float x, float y) {
    if (shape.pointCount == 1) {
      if (shape.cap != ShapeRenderer2D.LineCap.ROUND) return Float.POSITIVE_INFINITY;
      return (float) Math.hypot(x - shape.points[0], y - shape.points[1]) - shape.halfStroke;
    }
    float distance = Float.POSITIVE_INFINITY;
    for (int i = 0; i < shape.pointCount - 1; i++) {
      float x0 = shape.points[i * 2], y0 = shape.points[i * 2 + 1];
      float x1 = shape.points[(i + 1) * 2], y1 = shape.points[(i + 1) * 2 + 1];
      float dx = x1 - x0, dy = y1 - y0;
      float length = (float) Math.hypot(dx, dy);
      float tx = dx / length, ty = dy / length;
      float along = (x - x0) * tx + (y - y0) * ty;
      float across = -(x - x0) * ty + (y - y0) * tx;
      float before = i == 0 && shape.cap == ShapeRenderer2D.LineCap.SQUARE ? shape.halfStroke : 0;
      float after =
          i == shape.pointCount - 2 && shape.cap == ShapeRenderer2D.LineCap.SQUARE
              ? shape.halfStroke
              : 0;
      float halfLength = (length + before + after) * .5f;
      float center = (length + after - before) * .5f;
      float qx = Math.abs(along - center) - halfLength;
      float qy = Math.abs(across) - shape.halfStroke;
      float segment =
          (float) Math.hypot(Math.max(qx, 0), Math.max(qy, 0))
              + Math.min(Math.max(qx, qy), 0);
      distance = Math.min(distance, segment);
      if (i > 0)
        distance =
            Math.min(distance, (float) Math.hypot(x - x0, y - y0) - shape.halfStroke);
    }
    if (shape.cap == ShapeRenderer2D.LineCap.ROUND) {
      int last = (shape.pointCount - 1) * 2;
      distance =
          Math.min(
              distance,
              (float) Math.hypot(x - shape.points[0], y - shape.points[1]) - shape.halfStroke);
      distance =
          Math.min(
              distance,
              (float) Math.hypot(x - shape.points[last], y - shape.points[last + 1])
                  - shape.halfStroke);
    }
    return distance;
  }

  private static boolean finite(float value) {
    return Float.isFinite(value);
  }

  private static boolean finite(float a, float b) {
    return finite(a) && finite(b);
  }

  private static boolean finite(float a, float b, float c, float d) {
    return finite(a) && finite(b) && finite(c) && finite(d);
  }

  private static boolean finite(float a, float b, float c, float d, float e, float f) {
    return finite(a, b, c, d) && finite(e) && finite(f);
  }
}
