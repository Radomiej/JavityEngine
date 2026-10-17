package org.javity.animation;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.List;
import java.util.Objects;

/** Immutable named sequence of caller-owned atlas regions. */
public final class SpriteClip {
  private final String name;
  private final List<TextureRegion> frames;
  private final float secondsPerFrame;

  public SpriteClip(String name, List<TextureRegion> frames, float secondsPerFrame) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Clip name is required");
    Objects.requireNonNull(frames);
    if (frames.isEmpty()) throw new IllegalArgumentException("At least one frame is required");
    for (TextureRegion frame : frames) Objects.requireNonNull(frame, "Frame cannot be null");
    if (!Float.isFinite(secondsPerFrame) || secondsPerFrame <= 0)
      throw new IllegalArgumentException("Seconds per frame must be finite and positive");
    this.name = name;
    this.frames = List.copyOf(frames);
    this.secondsPerFrame = secondsPerFrame;
  }

  public String name() {
    return name;
  }

  public List<TextureRegion> frames() {
    return frames;
  }

  public float secondsPerFrame() {
    return secondsPerFrame;
  }

  public double duration() {
    return (double) frames.size() * secondsPerFrame;
  }
}
