package org.javity.gltf;

import static org.junit.Assert.*;

import org.junit.Test;

public class HdrSettingsTest {
  @Test
  public void accountsForHalfFloatAndDepthAndTransactionalPeak() {
    assertEquals(1000, HdrWorldPass.validateTarget(10, 10, 4096, 1000));
    assertEquals(2000, HdrWorldPass.validateResizeBudget(10, 10, 10, 10, 4096, 2000));
    assertThrows(
        IllegalArgumentException.class, () -> HdrWorldPass.validateTarget(10, 10, 4096, 999));
    assertThrows(
        IllegalArgumentException.class,
        () -> HdrWorldPass.validateResizeBudget(10, 10, 20, 10, 4096, 2999));
  }

  @Test
  public void rejectsInvalidConfigurationWithoutGL() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new HdrSettings(Float.NaN, HdrSettings.ToneMapper.ACES));
    assertThrows(
        IllegalArgumentException.class, () -> new HdrSettings(0, HdrSettings.ToneMapper.REINHARD));
    assertThrows(
        IllegalArgumentException.class, () -> new HdrSettings(65, HdrSettings.ToneMapper.ACES));
    assertThrows(NullPointerException.class, () -> new HdrSettings(1, null));
    assertThrows(
        IllegalArgumentException.class, () -> HdrWorldPass.validateTarget(0, 1, 4096, 100));
    assertThrows(
        IllegalArgumentException.class,
        () -> HdrWorldPass.validateTarget(4097, 1, 4096, Long.MAX_VALUE));
    assertThrows(
        IllegalArgumentException.class,
        () -> HdrWorldPass.validateTarget(1, 1, 5000, Long.MAX_VALUE));
  }
}
