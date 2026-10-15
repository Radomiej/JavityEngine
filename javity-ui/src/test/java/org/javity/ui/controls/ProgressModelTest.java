package org.javity.ui.controls;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.javity.effects.Ease;
import org.junit.Test;

public class ProgressModelTest {
  @Test
  public void delayedIncreaseAndDecreaseAreBoundedAndFinishAfterDelay() {
    ProgressModel model = new ProgressModel();
    model.setDuration(1);
    model.setDelay(.5f);
    model.setValue(.8f);

    model.act(.5f);
    assertEquals(0, model.getDisplayedValue(), .001f);
    assertEquals(.4f, model.getDelayedIncrease(), .001f);
    model.act(.5f);
    assertEquals(.4f, model.getDisplayedValue(), .001f);
    assertEquals(.8f, model.getDelayedIncrease(), .001f);
    model.act(.5f);
    assertEquals(.8f, model.getDelayedIncrease(), .001f);
    assertFalse(model.isAnimating());

    model.setValue(.2f);
    model.act(.5f);
    assertEquals(.5f, model.getDisplayedValue(), .001f);
    assertEquals(.8f, model.getDelayedDecrease(), .001f);
    model.act(1.5f);
    assertEquals(.2f, model.getDelayedDecrease(), .001f);
  }

  @Test
  public void reversalsSameTargetAndDtPartitionRemainDeterministic() {
    ProgressModel one = configured();
    ProgressModel pieces = configured();
    one.setValue(1);
    pieces.setValue(1);
    one.act(.6f);
    for (int i = 0; i < 6; i++) pieces.act(.1f);
    assertEquals(one.getDisplayedValue(), pieces.getDisplayedValue(), .0001f);

    one.setValue(0);
    one.act(.25f);
    float start = one.getDisplayedValue();
    one.act(.25f);
    float after = one.getDisplayedValue();
    one.setValue(0);
    assertEquals(0, one.getValue(), 0);
    assertEquals(after, one.getDisplayedValue(), 0);
    assertTrue(start > after);
    one.setValue(1);
    assertEquals(after, one.getDisplayedValue(), 0);
    one.act(.2f);
    assertTrue(Float.isFinite(one.getDisplayedValue()));
  }

  @Test
  public void rangeClampInstantResetCallbacksAndCloseAreSafe() {
    ProgressModel model = new ProgressModel();
    AtomicInteger calls = new AtomicInteger();
    model.setOnComplete(calls::incrementAndGet);
    model.setRange(0, 20);
    model.setValue(5);
    assertEquals(1, calls.get());
    model.setDuration(1);
    model.setDelay(0);
    model.setEase(Ease.OUT_QUAD);
    model.setValue(10);
    model.setRange(0, 4);
    assertEquals(4, model.getValue(), 0);
    assertEquals(4, model.getDisplayedValue(), 0);
    assertEquals(4, model.getDelayedIncrease(), 0);
    assertFalse(model.isAnimating());
    assertEquals(2, calls.get());

    model.setValue(3);
    model.close();
    assertThrows(IllegalStateException.class, () -> model.act(.1f));
    assertEquals(2, calls.get());
  }

  @Test
  public void rejectsInvalidRangeTimesAndValues() {
    ProgressModel model = new ProgressModel();
    assertThrows(IllegalArgumentException.class, () -> model.setRange(1, 1));
    assertThrows(IllegalArgumentException.class, () -> model.setDuration(Float.NaN));
    assertThrows(IllegalArgumentException.class, () -> model.setDelay(-1));
    assertThrows(IllegalArgumentException.class, () -> model.setValue(Float.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> model.act(-.1f));
  }

  @Test
  public void interpolationSupportsTheFullFiniteFloatRange() {
    ProgressModel model = new ProgressModel();
    model.setRange(-Float.MAX_VALUE, Float.MAX_VALUE);
    model.setValue(-Float.MAX_VALUE);
    model.setDuration(1);
    model.setValue(Float.MAX_VALUE);
    model.act(.5f);
    assertTrue(Float.isFinite(model.getDelayedIncrease()));
    assertEquals(0, model.getDelayedIncrease(), 1e31f);
  }

  private static ProgressModel configured() {
    ProgressModel model = new ProgressModel();
    model.setDuration(1);
    model.setDelay(.2f);
    model.setEase(Ease.IN_OUT_QUAD);
    return model;
  }
}
