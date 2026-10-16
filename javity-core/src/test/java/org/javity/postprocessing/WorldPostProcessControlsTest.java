package org.javity.postprocessing;

import static org.junit.Assert.*;

import org.junit.Test;

public class WorldPostProcessControlsTest {
  @Test
  public void baseAndIndependentLeasesResolveDeterministically() {
    WorldPostProcess.Controls controls = new WorldPostProcess.ControlState().view();
    controls.setBaseTint(.8f, .9f, 1f);
    controls.setBaseDesaturation(.1f);
    WorldPostProcess.ControlLease first = controls.acquire();
    first.setTint(.5f, 1f, 1f);
    first.setDesaturation(.4f);
    first.setVignette(.2f);
    WorldPostProcess.ControlLease second = controls.acquire();
    second.setTint(1f, .5f, 1f);
    second.setIntensity(.5f);

    WorldPostProcess.Resolved combined = controls.resolve();
    assertEquals(.4f, combined.red(), 1e-6f);
    assertEquals(.675f, combined.green(), 1e-6f);
    assertEquals(.5f, combined.desaturation(), 1e-6f);
    assertEquals(.2f, combined.vignette(), 1e-6f);

    first.close();
    assertFalse(first.isActive());
    WorldPostProcess.Resolved afterFirst = controls.resolve();
    assertEquals(.8f, afterFirst.red(), 1e-6f);
    assertEquals(.675f, afterFirst.green(), 1e-6f);
    assertEquals(.1f, afterFirst.desaturation(), 1e-6f);
    assertEquals(0f, afterFirst.vignette(), 1e-6f);

    controls.setBaseVignette(.25f);
    assertEquals(.25f, controls.resolve().vignette(), 1e-6f);
    second.close();
    assertFalse(controls.resolve().isIdentity());
    assertEquals(.25f, controls.resolve().vignette(), 1e-6f);
    second.close();
  }

  @Test
  public void intensityZeroAndCancellationRevealLiveBaseAndIdentityIsExact() {
    WorldPostProcess.Controls controls = new WorldPostProcess.ControlState().view();
    assertTrue(controls.resolve().isIdentity());
    WorldPostProcess.ControlLease lease = controls.acquire();
    lease.setDesaturation(1);
    assertEquals(1f, controls.resolve().desaturation(), 0f);
    lease.setIntensity(0);
    assertTrue(controls.resolve().isIdentity());
    controls.setBaseDesaturation(.35f);
    assertEquals(.35f, controls.resolve().desaturation(), 1e-6f);
    lease.close();
    assertEquals(.35f, controls.resolve().desaturation(), 1e-6f);
    controls.setBaseDesaturation(0);
    assertTrue(controls.resolve().isIdentity());
  }

  @Test
  public void rejectsNonFiniteAndOutOfRangeValuesBeforeChangingState() {
    WorldPostProcess.Controls controls = new WorldPostProcess.ControlState().view();
    WorldPostProcess.ControlLease lease = controls.acquire();
    assertThrows(IllegalArgumentException.class, () -> lease.setIntensity(Float.NaN));
    assertThrows(IllegalArgumentException.class, () -> lease.setDesaturation(-.01f));
    lease.setTint(.3f, .2f, .1f);
    assertThrows(IllegalArgumentException.class, () -> lease.setTint(.8f, Float.NaN, .7f));
    assertEquals(.3f, controls.resolve().red(), 1e-6f);
    assertEquals(.2f, controls.resolve().green(), 1e-6f);
    assertThrows(IllegalArgumentException.class, () -> controls.setBaseTint(.5f, Float.NaN, .5f));
    assertEquals(.3f, controls.resolve().red(), 1e-6f);
    assertThrows(
        IllegalArgumentException.class, () -> controls.setBaseVignette(Float.POSITIVE_INFINITY));
    lease.close();
    assertThrows(IllegalStateException.class, () -> lease.setVignette(.2f));
  }

  @Test
  public void validatesPhysicalTargetBudgetBeforeAllocation() {
    assertEquals(600, WorldPostProcess.validateTarget(10, 10, 20, 600));
    assertEquals(1_200, WorldPostProcess.validateResizeBudget(10, 10, 10, 10, 20, 1_200));
    assertThrows(
        IllegalArgumentException.class, () -> WorldPostProcess.validateTarget(10, 10, 20, 599));
    assertThrows(
        IllegalArgumentException.class, () -> WorldPostProcess.validateTarget(21, 10, 20, 10_000));
    assertThrows(
        IllegalArgumentException.class, () -> WorldPostProcess.validateTarget(0, 10, 20, 10_000));
    assertThrows(
        IllegalArgumentException.class,
        () -> WorldPostProcess.validateResizeBudget(10, 10, 10, 10, 20, 1_199));
  }

  @Test
  public void closingOwnerInvalidatesItsGenerationAndAllLeases() {
    WorldPostProcess.ControlState state = new WorldPostProcess.ControlState();
    WorldPostProcess.Controls controls = state.view();
    WorldPostProcess.ControlLease lease = controls.acquire();
    state.close();
    assertFalse(lease.isActive());
    assertThrows(IllegalStateException.class, () -> lease.setIntensity(.5f));
    assertThrows(IllegalStateException.class, controls::acquire);
    assertThrows(IllegalStateException.class, controls::resolve);
  }

  @Test
  public void leaseCapacityRejectsWithoutInvalidatingExistingHandles() {
    WorldPostProcess.Controls controls = new WorldPostProcess.ControlState().view();
    WorldPostProcess.ControlLease[] leases =
        new WorldPostProcess.ControlLease[WorldPostProcess.MAX_CONTROL_LEASES];
    for (int i = 0; i < leases.length; i++) leases[i] = controls.acquire();
    assertThrows(IllegalStateException.class, controls::acquire);
    for (WorldPostProcess.ControlLease lease : leases) assertTrue(lease.isActive());
    leases[0].close();
    assertTrue(controls.acquire().isActive());
  }
}
