package org.javity.input;

import java.util.Objects;

/** Source-independent description of one digital or analog action input. */
public sealed interface InputBinding
    permits InputBinding.Key, InputBinding.Button, InputBinding.Axis {
  static String identifier(String value, String label) {
    Objects.requireNonNull(value, label);
    if (value.isBlank() || value.indexOf('\u0000') >= 0)
      throw new IllegalArgumentException(label + " must be nonblank and cannot contain NUL");
    return value;
  }

  record Key(int key) implements InputBinding {}

  record Button(String source, String button) implements InputBinding {
    public Button {
      source = identifier(source, "source");
      button = identifier(button, "button");
    }
  }

  record Axis(String source, String axis, int direction, float deadzone) implements InputBinding {
    public Axis {
      source = identifier(source, "source");
      axis = identifier(axis, "axis");
      if (direction != -1 && direction != 1)
        throw new IllegalArgumentException("direction must be -1 or +1");
      if (!Float.isFinite(deadzone) || deadzone < 0f || deadzone >= 1f)
        throw new IllegalArgumentException("deadzone must be finite and in [0, 1)");
    }
  }
}
