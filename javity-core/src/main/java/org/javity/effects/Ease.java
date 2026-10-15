package org.javity.effects;

/** Curves used to interpolate an effect from its captured start to its destination. */
public enum Ease {
  LINEAR {
    @Override
    public double apply(double t) {
      return t;
    }
  },
  IN_QUAD {
    @Override
    public double apply(double t) {
      return t * t;
    }
  },
  OUT_QUAD {
    @Override
    public double apply(double t) {
      return t * (2d - t);
    }
  },
  IN_OUT_QUAD {
    @Override
    public double apply(double t) {
      return t < .5d ? 2d * t * t : 1d - Math.pow(-2d * t + 2d, 2d) / 2d;
    }
  };

  public abstract double apply(double t);
}
