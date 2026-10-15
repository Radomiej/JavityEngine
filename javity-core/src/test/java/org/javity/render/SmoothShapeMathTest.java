package org.javity.render;

import static org.junit.Assert.*;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class SmoothShapeMathTest {
  private static final Matrix4 IDENTITY = new Matrix4();

  private SmoothShapeMath.Prepared prepare(ShapeRenderer2D shape) {
    return SmoothShapeMath.prepare(
        shape, IDENTITY, IDENTITY, 800, 600);
  }

  @Test
  public void compactsConsecutiveDuplicatesBeforeApplyingPointLimit() {
    List<Vector2> points = new ArrayList<>();
    for (int i = 0; i < 64; i++) {
      points.add(new Vector2(i, 0));
      points.add(new Vector2(i, 0));
    }
    var prepared = prepare(ShapeRenderer2D.polyline(points, 2));
    assertEquals(64, prepared.pointCount);
  }

  @Test
  public void rejectsMoreThan64EffectivePolylinePoints() {
    List<Vector2> points = new ArrayList<>();
    for (int i = 0; i < 65; i++) points.add(new Vector2(i, i % 2));
    assertThrows(
        IllegalArgumentException.class,
        () -> prepare(ShapeRenderer2D.polyline(points, 1)));
  }

  @Test
  public void allCoincidentPointsReduceToOnePointAndCapControlsDegenerateInk() {
    var points = List.of(new Vector2(3, 4), new Vector2(3, 4), new Vector2(3, 4));
    var round = ShapeRenderer2D.polyline(points, 4);
    var roundPrepared = prepare(round);
    assertEquals(1, roundPrepared.pointCount);
    assertTrue(roundPrepared.hasArea);
    assertEquals(-2, SmoothShapeMath.distance(roundPrepared, 3, 4), 1e-5);

    round.lineCap = ShapeRenderer2D.LineCap.BUTT;
    assertFalse(prepare(round).hasArea);
    round.lineCap = ShapeRenderer2D.LineCap.SQUARE;
    assertFalse(prepare(round).hasArea);
  }

  @Test
  public void worldToPixelJacobianControlsConservativeFringePadding() {
    var line = ShapeRenderer2D.polyline(List.of(new Vector2(-2, 0), new Vector2(2, 0)), 2);
    Matrix4 zoom = new Matrix4().scale(4, 2, 1);
    var base = SmoothShapeMath.prepare(line, IDENTITY, IDENTITY, 800, 600);
    var scaled = SmoothShapeMath.prepare(line, IDENTITY, zoom, 800, 600);
    assertEquals(base.left, scaled.left, 1e-5); // contour bound is unchanged
    assertTrue(scaled.quadLeft > base.quadLeft);
    assertTrue(scaled.quadBottom > base.quadBottom);

    Matrix4 anisotropicProjection = new Matrix4().scale(2, 5, 1);
    var anisotropic = SmoothShapeMath.prepare(line, anisotropicProjection, IDENTITY, 800, 600);
    assertNotEquals(base.quadLeft - base.left, anisotropic.quadLeft - anisotropic.left, 1e-6);
  }

  @Test
  public void rotatedTransformsExpandFringeOnBothLocalAxes() {
    var line = ShapeRenderer2D.polyline(List.of(new Vector2(-2, 0), new Vector2(2, 0)), 2);
    Matrix4 rotated = new Matrix4().rotate(0, 0, 1, 45).scale(2, 1, 1);
    var prepared = SmoothShapeMath.prepare(line, IDENTITY, rotated, 800, 600);
    assertTrue(prepared.quadLeft < prepared.left);
    assertTrue(prepared.quadBottom < prepared.bottom);
  }

  @Test
  public void zeroScaleOrInvalidViewportSafelySkipsDrawing() {
    var rect = ShapeRenderer2D.rectangle(8, 4);
    assertNull(
        SmoothShapeMath.prepare(rect, IDENTITY, new Matrix4().scale(0, 1, 1), 800, 600));
    assertNull(SmoothShapeMath.prepare(rect, IDENTITY, IDENTITY, 0, 600));
  }

  @Test
  public void rejectsNullLineCapAndJoinBeforeRendererCanReachGl() {
    var line = ShapeRenderer2D.polyline(List.of(new Vector2(), new Vector2(1, 0)), 1);
    line.lineCap = null;
    assertThrows(IllegalArgumentException.class, () -> prepare(line));
    line.lineCap = ShapeRenderer2D.LineCap.ROUND;
    line.lineJoin = null;
    assertThrows(IllegalArgumentException.class, () -> prepare(line));
  }

  @Test
  public void tinyPositiveSegmentRemainsValidUnderLargeScale() {
    var line =
        ShapeRenderer2D.polyline(
            List.of(new Vector2(0, 0), new Vector2(1e-8f, 0)), 2e-9f);
    Matrix4 largeScale = new Matrix4().scale(1e8f, 1e8f, 1);
    var prepared = SmoothShapeMath.prepare(line, IDENTITY, largeScale, 800, 600);
    assertNotNull(prepared);
    assertTrue(
        SmoothShapeMath.distance(prepared, 5e-9f, 0) < 0);
  }

  @Test
  public void rejectsAnyNonzeroPerspectiveRowCoefficient() {
    Matrix4 perspective = new Matrix4();
    perspective.val[Matrix4.M30] = 1e-8f;
    assertNull(
        SmoothShapeMath.prepare(
            ShapeRenderer2D.rectangle(2, 2), IDENTITY, perspective, 800, 600));
  }

  @Test
  public void rejectsExtremePolylineEndpointsBeforeShaderLengthOverflows() {
    var line =
        ShapeRenderer2D.polyline(
            List.of(new Vector2(-Float.MAX_VALUE, 0), new Vector2(Float.MAX_VALUE, 0)), 1);
    assertThrows(IllegalArgumentException.class, () -> prepare(line));
  }

  @Test
  public void rejectsFiniteSegmentsWhoseSquaredLengthExceedsShaderFloatRange() {
    var line =
        ShapeRenderer2D.polyline(
            List.of(new Vector2(-1e20f, 0), new Vector2(1e20f, 0)), 1);
    assertThrows(IllegalArgumentException.class, () -> prepare(line));
  }

  @Test
  public void rejectsQuadDimensionsThatCannotBeSubmittedAsFiniteFloats() {
    var circle = ShapeRenderer2D.circle(Float.MAX_VALUE * 0.6f);
    assertThrows(IllegalArgumentException.class, () -> prepare(circle));
  }

  @Test
  public void rejectsRoundCapQuadThatCollapsesAfterFloatRounding() {
    var point = new Vector2(1e20f, 0);
    var line = ShapeRenderer2D.polyline(List.of(point, point), 2);
    assertThrows(IllegalArgumentException.class, () -> prepare(line));
  }

  @Test
  public void rejectsSegmentsWhoseSquaredLengthUnderflowsInShaderFloatMath() {
    var line = ShapeRenderer2D.polyline(List.of(new Vector2(), new Vector2(1e-30f, 0)), 1);
    line.lineCap = ShapeRenderer2D.LineCap.BUTT;
    assertThrows(IllegalArgumentException.class, () -> prepare(line));
    line.lineCap = ShapeRenderer2D.LineCap.SQUARE;
    assertThrows(IllegalArgumentException.class, () -> prepare(line));
  }

  @Test
  public void rectangleAndCircleBoundsContainRadiusAndCenteredStroke() {
    var rectangle = ShapeRenderer2D.rectangle(10, 6);
    rectangle.strokeWidth = 2;
    rectangle.cornerRadius = 100;
    var rect = prepare(rectangle);
    assertEquals(-6, rect.left, 1e-6);
    assertEquals(6, rect.right, 1e-6);
    assertEquals(-4, rect.bottom, 1e-6);
    assertEquals(4, rect.top, 1e-6);

    var circle = ShapeRenderer2D.circle(5);
    circle.strokeWidth = 4;
    var circ = prepare(circle);
    assertEquals(-7, circ.left, 1e-6);
    assertEquals(7, circ.right, 1e-6);
  }

  @Test
  public void diagonalSquareCapsAreIncludedInBoundsAndDistance() {
    var line =
        ShapeRenderer2D.polyline(List.of(new Vector2(0, 0), new Vector2(3, 4)), 2);
    line.lineCap = ShapeRenderer2D.LineCap.SQUARE;
    var prepared = prepare(line);
    assertTrue(prepared.left < -0.2f);
    assertTrue(prepared.bottom < -0.2f);
    assertTrue(SmoothShapeMath.distance(prepared, -0.5f, -0.5f) < 0);
    assertTrue(SmoothShapeMath.distance(prepared, -0.9f, -0.9f) > 0);
  }

  @Test
  public void roundJoinAndEndpointCapsHaveSignedDistance() {
    var line =
        ShapeRenderer2D.polyline(
            List.of(new Vector2(0, 0), new Vector2(4, 0), new Vector2(4, 4)), 2);
    var prepared = prepare(line);
    assertTrue(SmoothShapeMath.distance(prepared, 4.7f, 0.7f) < 0);
    assertTrue(SmoothShapeMath.distance(prepared, -0.7f, 0) < 0);
    line.lineCap = ShapeRenderer2D.LineCap.BUTT;
    assertTrue(SmoothShapeMath.distance(prepare(line), -0.1f, 0) > 0);
  }
}
