package org.javity.particles;

/** Generation-checked control for one burst. */
public final class ParticleBurstHandle3D implements AutoCloseable {
  private final ParticleBurstPool3D pool;
  private final int lease;
  private final long generation;

  ParticleBurstHandle3D(ParticleBurstPool3D pool, int lease, long generation) {
    this.pool = pool;
    this.lease = lease;
    this.generation = generation;
  }

  public boolean isActive() {
    return pool.isActive(lease, generation);
  }

  public void pause() {
    pool.pause(lease, generation);
  }

  public void resume() {
    pool.resume(lease, generation);
  }

  public void cancel() {
    pool.cancel(lease, generation);
  }

  @Override
  public void close() {
    cancel();
  }
}
