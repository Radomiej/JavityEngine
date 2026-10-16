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
    return SmoothShapeMath.prepare(shape, IDENTITY, IDENTITY, 800, 600);
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
        IllegalArgumentException.class, () -> prepare(ShapeRenderer2D.polyline(points, 1)));
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
    assertNull(SmoothShapeMath.prepare(rect, IDENTITY, new Matrix4().scale(0, 1, 1), 800, 600));
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
    var line = ShapeRenderer2D.polyline(List.of(new Vector2(0, 0), new Vector2(1e-8f, 0)), 2e-9f);
    Matrix4 largeScale = new Matrix4().scale(1e8f, 1e8f, 1);
    var prepared = SmoothShapeMath.prepare(line, IDENTITY, largeScale, 800, 600);
    assertNotNull(prepared);
    assertTrue(SmoothShapeMath.distance(prepared, 5e-9f, 0) < 0);
  }

  @Test
  public void rejectsAnyNonzeroPerspectiveRowCoefficient() {
    Matrix4 perspective = new Matrix4();
    perspective.val[Matrix4.M30] = 1e-8f;
    assertNull(
        SmoothShapeMath.prepare(ShapeRenderer2D.rectangle(2, 2), IDENTITY, perspective, 800, 600));
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
    var line = ShapeRenderer2D.polyline(List.of(new Vector2(-1e20f, 0), new Vector2(1e20f, 0)), 1);
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
    var line = ShapeRenderer2D.polyline(List.of(new Vector2(0, 0), new Vector2(3, 4)), 2);
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

  @Test
  public void dashIntervalsUseSignedPhaseAndCumulativeDistanceAcrossBends() {
    var line = ShapeRenderer2D.polyline(List.of(new Vector2(0, 0), new Vector2(40, 0)), 2);
    line.lineCap = ShapeRenderer2D.LineCap.BUTT;
    line.setDashes(new DashPattern2D(10, 10), 0);
    var prepared = prepare(line);
    assertTrue(SmoothShapeMath.distance(prepared, 5, 0) < 0);
    assertTrue(SmoothShapeMath.distance(prepared, 15, 0) > 0);
    assertEquals(0, SmoothShapeMath.distance(prepared, 20, 0), 1e-6);
    assertTrue(SmoothShapeMath.distance(prepared, 20.01f, 0) < 0);
    line.setDashPhase(5);
    prepared = prepare(line);
    assertTrue(SmoothShapeMath.distance(prepared, 2, 0) < 0);
    assertTrue(SmoothShapeMath.distance(prepared, 7, 0) > 0);
    line.setDashPhase(-5);
    prepared = prepare(line);
    assertTrue(SmoothShapeMath.distance(prepared, 2, 0) > 0);
    assertTrue(SmoothShapeMath.distance(prepared, 7, 0) < 0);

    var bent =
        ShapeRenderer2D.polyline(
            List.of(new Vector2(0, 0), new Vector2(13, 0), new Vector2(13, 30)), 2);
    bent.lineCap = ShapeRenderer2D.LineCap.BUTT;
    bent.setDashes(new DashPattern2D(10, 10), 0);
    prepared = prepare(bent);
    assertEquals(13, prepared.cumulativeLengths[1], 0);
    assertEquals(13, prepared.dashStartPhases[1], 0);
    assertTrue(SmoothShapeMath.distance(prepared, 13, 4) > 0);
    assertTrue(SmoothShapeMath.distance(prepared, 13, 9) < 0);
  }

  @Test
  public void dashValidationAndPhaseAreImmutableAndBounded() {
    assertThrows(IllegalArgumentException.class, () -> new DashPattern2D(1, 2, 3));
    assertThrows(IllegalArgumentException.class, () -> new DashPattern2D(1, 0));
    assertThrows(IllegalArgumentException.class, () -> new DashPattern2D(1, -2));
    assertThrows(
        IllegalArgumentException.class, () -> new DashPattern2D(1, Float.POSITIVE_INFINITY));
    var pattern = new DashPattern2D(9, 4, 3, 7);
    float[] copy = pattern.lengths();
    copy[0] = 100;
    assertEquals(9, pattern.length(0), 0);
    var line = ShapeRenderer2D.polyline(List.of(new Vector2(), new Vector2(30, 0)), 2);
    line.setDashes(pattern, -31);
    assertEquals(15, line.dashPhase(), 1e-5);
    assertThrows(IllegalArgumentException.class, () -> line.setDashPhase(Float.NaN));
    assertThrows(
        IllegalStateException.class, () -> ShapeRenderer2D.circle(3).setDashes(pattern, 0));
    line.setDashes(new DashPattern2D(1e-8f, 1e-8f), 0);
    assertThrows(IllegalArgumentException.class, () -> prepare(line));
  }

  @Test
  public void multiEntryPatternUsesModuloAndDuplicatePointsDoNotConsumeDistance() {
    var line =
        ShapeRenderer2D.polyline(
            List.of(new Vector2(0, 0), new Vector2(0, 0), new Vector2(13, 0), new Vector2(13, 30)),
            2);
    line.lineCap = ShapeRenderer2D.LineCap.BUTT;
    line.setDashes(new DashPattern2D(9, 4, 3, 7), -31);
    var prepared = prepare(line);
    assertEquals(3, prepared.pointCount);
    assertEquals(13, prepared.cumulativeLengths[1], 0);
    assertEquals(5, prepared.dashStartPhases[1], 0); // (-31 + 13) mod 23; the duplicate adds zero.
    assertTrue(SmoothShapeMath.distance(prepared, 13, 0) < 0);
    assertTrue(SmoothShapeMath.distance(prepared, 13, 2) < 0);
    line.setDashPhase(46); // phase-equivalent to zero.
    prepared = prepare(line);
    assertEquals(13, prepared.dashStartPhases[1], 0);
    assertTrue(SmoothShapeMath.distance(prepared, 13, 4) > 0);
  }

  @Test
  public void fractionalPeriodUsesDoubleSumForLargePhaseAndPointBoundary() {
    var pattern = new DashPattern2D(1f, .1f, 1f, .1f, 1f, .1f, 1f, .1f);
    assertEquals(4.4000000059604645d, pattern.period(), 0);
    var point = ShapeRenderer2D.polyline(List.of(new Vector2(), new Vector2()), 2);
    point.setDashes(pattern, 1_000_000f);
    assertTrue(point.dashPhase() > 3.19 && point.dashPhase() < 3.2);
    assertTrue(SmoothShapeMath.distance(prepare(point), 0, 0) < 0);

    var boundary = ShapeRenderer2D.polyline(List.of(new Vector2(), new Vector2()), 4);
    boundary.setDashes(new DashPattern2D(10, 10), Math.nextDown(10f));
    assertTrue(SmoothShapeMath.distance(prepare(boundary), 0, 0) < 0);
    boundary.setDashPhase(Math.nextDown(10d));
    assertTrue(SmoothShapeMath.distance(prepare(boundary), 0, 0) < 0);
    boundary.setDashPhase(10d);
    assertTrue(Float.isInfinite(SmoothShapeMath.distance(prepare(boundary), 0, 0)));

    var precisionRejected = ShapeRenderer2D.polyline(List.of(new Vector2(), new Vector2()), 4);
    precisionRejected.setDashes(new DashPattern2D(1e-7f, 1e8f), 1);
    assertThrows(IllegalArgumentException.class, precisionRejected::validateDashConfiguration);
  }

  @Test
  public void floatPhasePackingPreservesBothSidesAndExactPatternTransitions() {
    var pattern = new DashPattern2D(10, 10, 10, 10);
    double[] boundaries = {10, 20, 30};
    boolean[] expectedBefore = {true, false, true};
    boolean[] expectedAtAndAfter = {false, true, false};
    for (int boundaryIndex = 0; boundaryIndex < boundaries.length; boundaryIndex++) {
      double boundary = boundaries[boundaryIndex];
      for (int side = -1; side <= 1; side++) {
        double phase =
            side < 0 ? Math.nextDown(boundary) : side > 0 ? Math.nextUp(boundary) : boundary;
        var point = ShapeRenderer2D.polyline(List.of(new Vector2(), new Vector2()), 4);
        point.setDashes(pattern, phase);
        var prepared = prepare(point);
        float packed = SmoothShapeRenderer.packDashPhase(prepared.dashStartPhases[0], prepared);
        boolean expected =
            side < 0 ? expectedBefore[boundaryIndex] : expectedAtAndAfter[boundaryIndex];
        assertEquals(
            "boundary " + boundary + " side " + side,
            expected,
            SmoothShapeMath.distance(prepared, 0, 0) < 0);
        assertEquals(
            "packed interval at " + boundary + " side " + side,
            expected,
            dashIntervalContainsInk(packed, prepared));
        if (side == 0) assertEquals((float) boundary, packed, 0);
        if (side < 0)
          assertTrue("predecessor must remain below boundary", packed < (float) boundary);
        if (side > 0) assertTrue("successor must remain above boundary", packed > (float) boundary);
      }
    }
  }

  @Test
  public void floatPhasePackingKeepsStrictBentJoinContinuation() {
    var bent =
        ShapeRenderer2D.polyline(
            List.of(new Vector2(0, 0), new Vector2(20, 0), new Vector2(20, 20)), 4);
    bent.lineCap = ShapeRenderer2D.LineCap.BUTT;
    bent.setDashes(new DashPattern2D(10, 10, 10, 10), Math.ulp(20d));
    var prepared = prepare(bent);
    double vertexPhase = prepared.dashStartPhases[1];
    float packed = SmoothShapeRenderer.packDashPhase(vertexPhase, prepared);
    assertEquals(20.000000000000004d, vertexPhase, 0);
    assertTrue("strict ON phase must remain inside the ON interval", packed > 20f && packed < 30f);
    assertEquals(-0.58578646, SmoothShapeMath.distance(prepared, 21, -1), 1e-6);
  }

  private boolean dashIntervalContainsInk(float phase, SmoothShapeMath.Prepared prepared) {
    double cursor = 0;
    for (int i = 0; i < prepared.dashCount; i++) {
      float start = (float) cursor;
      cursor += prepared.dashLengths[i];
      float end = (float) cursor;
      if (phase >= start && phase < end) return (i & 1) == 0;
    }
    return false;
  }

  @Test
  public void tinyMagnifiedBentPathKeepsStrictRoundJoinContinuation() {
    var tinyBend =
        ShapeRenderer2D.polyline(
            List.of(new Vector2(0, 0), new Vector2(5.5e-9f, 0), new Vector2(5.5e-9f, 1e-8f)),
            1e-9f);
    tinyBend.lineCap = ShapeRenderer2D.LineCap.BUTT;
    tinyBend.setDashes(new DashPattern2D(2.5e-9f, 2.5e-9f), 0);
    var prepared =
        SmoothShapeMath.prepare(tinyBend, IDENTITY, new Matrix4().scale(1e8f, 1e8f, 1), 800, 600);
    assertNotNull(prepared);
    assertTrue(prepared.dashStartPhases[1] > 0 && prepared.dashStartPhases[1] < 2.5e-9);
    assertEquals(-7.5e-11, SmoothShapeMath.distance(prepared, 5.8e-9f, -.3e-9f), 2e-11);
  }

  @Test
  public void negativeSubnormalPhaseNormalizesInsideFinalGap() {
    var point = ShapeRenderer2D.polyline(List.of(new Vector2(), new Vector2()), 4);
    point.setDashes(new DashPattern2D(10, 10), -Double.MIN_VALUE);
    assertTrue(point.dashPhase() < 20);
    assertTrue(point.dashPhase() > 19.9);
    assertTrue(Float.isInfinite(SmoothShapeMath.distance(prepare(point), 0, 0)));
  }

  @Test
  public void onePointAndReversedPathsKeepOpenPathPhaseRules() {
    var point = ShapeRenderer2D.polyline(List.of(new Vector2(2, 3), new Vector2(2, 3)), 4);
    point.setDashes(new DashPattern2D(10, 10), 0);
    assertTrue(SmoothShapeMath.distance(prepare(point), 2, 3) < 0);
    point.setDashPhase(15);
    assertTrue(Float.isInfinite(SmoothShapeMath.distance(prepare(point), 2, 3)));

    var reversed = ShapeRenderer2D.polyline(List.of(new Vector2(30, 0), new Vector2(0, 0)), 2);
    reversed.lineCap = ShapeRenderer2D.LineCap.BUTT;
    reversed.setDashes(new DashPattern2D(10, 10), 0);
    var prepared = prepare(reversed);
    assertTrue(SmoothShapeMath.distance(prepared, 25, 0) < 0);
    assertTrue(SmoothShapeMath.distance(prepared, 15, 0) > 0);
    assertTrue(SmoothShapeMath.distance(prepared, 5, 0) < 0);
    var mirrored =
        SmoothShapeMath.prepare(reversed, IDENTITY, new Matrix4().scale(-1, 1, 1), 800, 600);
    assertEquals(prepared.dashStartPhases[0], mirrored.dashStartPhases[0], 0);
    assertEquals(prepared.dashStartPhases[1], mirrored.dashStartPhases[1], 0);

    var tiny = ShapeRenderer2D.polyline(List.of(new Vector2(0, 0), new Vector2(1e-8f, 0)), 1e-9f);
    tiny.setDashes(new DashPattern2D(2.5e-9f, 2.5e-9f), 0);
    assertNotNull(
        SmoothShapeMath.prepare(tiny, IDENTITY, new Matrix4().scale(1e8f, 1e8f, 1), 800, 600));
  }
}
