package org.javity.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bit-exact ordered composition inputs; tags distinguish slot modes and presence. */
public final class ExactPoseKey {
  private record FloatBits(List<Integer> values) {}

  private record Mask(List<Boolean> values) {}

  private final List<Object> state;

  private ExactPoseKey(List<Object> state) {
    this.state = List.copyOf(state);
  }

  public static Builder builder(SharedPoseData data) {
    return new Builder().data(data);
  }

  public static final class Builder {
    private final List<Object> state = new ArrayList<>();

    public Builder data(SharedPoseData data) {
      state.add(Objects.requireNonNull(data));
      return this;
    }

    public Builder tag(int value) {
      state.add(value);
      return this;
    }

    public Builder number(double value) {
      state.add(Double.doubleToRawLongBits(value));
      return this;
    }

    public Builder floats(float[] values) {
      List<Integer> bits = new ArrayList<>(values.length);
      for (float value : values) bits.add(Float.floatToRawIntBits(value));
      state.add(new FloatBits(List.copyOf(bits)));
      return this;
    }

    public Builder mask(boolean[] values) {
      List<Boolean> copy = new ArrayList<>(values.length);
      for (boolean value : values) copy.add(value);
      state.add(new Mask(List.copyOf(copy)));
      return this;
    }

    public ExactPoseKey build() {
      return new ExactPoseKey(state);
    }
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ExactPoseKey key && state.equals(key.state);
  }

  @Override
  public int hashCode() {
    return state.hashCode();
  }
}
