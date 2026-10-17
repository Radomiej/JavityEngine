package org.javity.particles;

import java.util.Arrays;
import java.util.Objects;
import org.javity.core.GameSystem;
import org.javity.effects.Clock;

/**
 * Fixed-capacity CPU simulation of world-space bursts. The pool owns particle state, never
 * GameObjects or GPU resources. A burst is accepted atomically or rejected when either fixed
 * capacity is unavailable; it never evicts another burst. Delta time is consumed once per step,
 * with constant-acceleration integration and no catch-up emission loop.
 */
public final class ParticleBurstPool3D extends GameSystem {
  public static final int DEFAULT_PARTICLE_CAPACITY = 1024;
  public static final int DEFAULT_BURST_CAPACITY = 32;

  /** Packed render record: x,y,z,size,r,g,b,a. */
  public static final int RENDER_FLOATS_PER_PARTICLE = 8;

  private static final class Lease {
    long generation;
    int count;
    int activeCount;
    long eligibleFrame;
    boolean active;
    boolean paused;
  }

  private final Clock clock;
  private final int particleCapacity;
  private final Lease[] leases;
  private final double[] origins, initialVelocities, accelerations, ages, lifetimes;
  private final float[] colors, sizes;
  private final int[] owners;
  private final long[] ownerGenerations;
  private boolean released;
  private boolean created;
  private int activeBursts;
  private int activeParticles;

  public ParticleBurstPool3D() {
    this(Clock.WORLD);
  }

  public ParticleBurstPool3D(Clock clock) {
    this(clock, DEFAULT_PARTICLE_CAPACITY, DEFAULT_BURST_CAPACITY);
  }

  public ParticleBurstPool3D(Clock clock, int particleCapacity, int burstCapacity) {
    this.clock = Objects.requireNonNull(clock, "clock");
    if (particleCapacity < 1 || burstCapacity < 1)
      throw new IllegalArgumentException("Pool capacities must be positive");
    this.particleCapacity = particleCapacity;
    leases = new Lease[burstCapacity];
    for (int i = 0; i < leases.length; i++) leases[i] = new Lease();
    origins = new double[particleCapacity * 3];
    initialVelocities = new double[particleCapacity * 3];
    accelerations = new double[particleCapacity * 3];
    colors = new float[particleCapacity * 4];
    sizes = new float[particleCapacity];
    ages = new double[particleCapacity];
    lifetimes = new double[particleCapacity];
    owners = new int[particleCapacity];
    ownerGenerations = new long[particleCapacity];
    Arrays.fill(owners, -1);
  }

  /** Returns null when the fixed lease/particle capacity cannot accept the entire burst. */
  public ParticleBurstHandle3D burst(ParticleBurstSpec3D spec) {
    live();
    Objects.requireNonNull(spec, "spec");
    if (spec.count() > particleCapacity) return null;
    int leaseIndex = -1;
    for (int i = 0; i < leases.length; i++)
      if (!leases[i].active) {
        leaseIndex = i;
        break;
      }
    if (leaseIndex < 0 || particleCapacity - activeParticles < spec.count()) return null;
    Lease lease = leases[leaseIndex];
    long generation = ++lease.generation;
    lease.active = true;
    lease.paused = false;
    lease.count = spec.count();
    lease.activeCount = spec.count();
    lease.eligibleFrame = getGame().getFrameIndex() + 1;
    activeBursts++;

    float[] origin = {spec.origin().x, spec.origin().y, spec.origin().z};
    float[] min = {spec.minVelocity().x, spec.minVelocity().y, spec.minVelocity().z};
    float[] max = {spec.maxVelocity().x, spec.maxVelocity().y, spec.maxVelocity().z};
    float[] acceleration = {spec.acceleration().x, spec.acceleration().y, spec.acceleration().z};
    var color = spec.color();
    int spawned = 0;
    long random = spec.seed();
    for (int i = 0; i < particleCapacity && spawned < spec.count(); i++) {
      if (owners[i] >= 0) continue;
      int p = i * 3, c = i * 4;
      for (int axis = 0; axis < 3; axis++) {
        origins[p + axis] = origin[axis];
        random = next(random);
        double unit = ((random >>> 40) & 0xffffff) / 16777215.0;
        double sampled = min[axis] + ((double) max[axis] - min[axis]) * unit;
        initialVelocities[p + axis] = sampled;
        accelerations[p + axis] = acceleration[axis];
      }
      colors[c] = color.r;
      colors[c + 1] = color.g;
      colors[c + 2] = color.b;
      colors[c + 3] = color.a;
      sizes[i] = spec.size();
      ages[i] = 0;
      lifetimes[i] = spec.lifetimeSeconds();
      owners[i] = leaseIndex;
      ownerGenerations[i] = generation;
      spawned++;
    }
    activeParticles += spawned;
    return new ParticleBurstHandle3D(this, leaseIndex, generation);
  }

  public int getParticleCapacity() {
    return particleCapacity;
  }

  public int getBurstCapacity() {
    return leases.length;
  }

  public int getActiveParticleCount() {
    return activeParticles;
  }

  public int getActiveBurstCount() {
    return activeBursts;
  }

  /** Copies live particles into caller-owned packed storage and returns the number copied. */
  public int copyRenderData(float[] output) {
    Objects.requireNonNull(output, "output");
    if (output.length < particleCapacity * RENDER_FLOATS_PER_PARTICLE)
      throw new IllegalArgumentException("Output must fit the configured particle capacity");
    // Validate every live double-to-float conversion before exposing any part of this snapshot.
    for (int i = 0; i < particleCapacity; i++) {
      if (!isLiveSlot(i)) continue;
      int p = i * 3;
      for (int axis = 0; axis < 3; axis++) {
        float value = (float) positionAt(p + axis, ages[i]);
        if (!Float.isFinite(value))
          throw new IllegalStateException(
              "Validated particle position is not representable as float");
      }
    }
    int copied = 0;
    for (int i = 0; i < particleCapacity; i++) {
      if (!isLiveSlot(i)) continue;
      int p = i * 3, c = i * 4, o = copied * RENDER_FLOATS_PER_PARTICLE;
      output[o] = (float) positionAt(p, ages[i]);
      output[o + 1] = (float) positionAt(p + 1, ages[i]);
      output[o + 2] = (float) positionAt(p + 2, ages[i]);
      output[o + 3] = sizes[i];
      output[o + 4] = colors[c];
      output[o + 5] = colors[c + 1];
      output[o + 6] = colors[c + 2];
      output[o + 7] = colors[c + 3];
      copied++;
    }
    return copied;
  }

  @Override
  public void onCreate() {
    created = true;
  }

  @Override
  public void onAfterUpdate(float delta) {
    if (clock == Clock.WORLD) advance(delta);
  }

  @Override
  public void onUiUpdate(float delta) {
    if (clock == Clock.UI) advance(delta);
  }

  boolean isActive(int index, long generation) {
    return !released
        && index >= 0
        && index < leases.length
        && leases[index].active
        && leases[index].generation == generation;
  }

  void pause(int index, long generation) {
    if (isActive(index, generation)) leases[index].paused = true;
  }

  void resume(int index, long generation) {
    if (isActive(index, generation)) leases[index].paused = false;
  }

  void cancel(int index, long generation) {
    if (isActive(index, generation)) retire(index, generation);
  }

  @Override
  protected void onRelease() {
    released = true;
    for (int i = 0; i < leases.length; i++) if (leases[i].active) retire(i, leases[i].generation);
    Arrays.fill(owners, -1);
    activeBursts = activeParticles = 0;
  }

  private void advance(float delta) {
    if (released || !Float.isFinite(delta) || delta <= 0 || getGame().isDisposed()) return;
    for (int leaseIndex = 0; leaseIndex < leases.length; leaseIndex++) {
      Lease lease = leases[leaseIndex];
      if (!lease.active || lease.paused || getGame().getFrameIndex() < lease.eligibleFrame)
        continue;
      for (int i = 0; i < particleCapacity; i++) {
        if (owners[i] != leaseIndex || ownerGenerations[i] != lease.generation) continue;
        double age = Math.min(lifetimes[i], ages[i] + (double) delta);
        if (age >= lifetimes[i]) {
          owners[i] = -1;
          lease.activeCount--;
          activeParticles--;
          continue;
        }
        ages[i] = age;
      }
      if (lease.activeCount == 0) retire(leaseIndex, lease.generation);
    }
  }

  private boolean isLiveSlot(int particleIndex) {
    int owner = owners[particleIndex];
    return owner >= 0
        && leases[owner].active
        && leases[owner].generation == ownerGenerations[particleIndex];
  }

  private double positionAt(int coordinateIndex, double age) {
    double velocity = initialVelocities[coordinateIndex];
    double acceleration = accelerations[coordinateIndex];
    return origins[coordinateIndex] + velocity * age + 0.5 * acceleration * age * age;
  }

  private void retire(int index, long generation) {
    Lease lease = leases[index];
    if (!lease.active || lease.generation != generation) return;
    for (int i = 0; i < particleCapacity; i++) {
      if (owners[i] == index && ownerGenerations[i] == generation) {
        owners[i] = -1;
        activeParticles--;
      }
    }
    lease.active = false;
    lease.paused = false;
    lease.count = lease.activeCount = 0;
    activeBursts--;
  }

  private void live() {
    if (released || isDisposed() || getGame() == null || !created)
      throw new IllegalStateException("ParticleBurstPool3D requires a live started game");
  }

  private static long next(long x) {
    x ^= x >>> 12;
    x ^= x << 25;
    x ^= x >>> 27;
    return x * 2685821657736338717L;
  }
}
