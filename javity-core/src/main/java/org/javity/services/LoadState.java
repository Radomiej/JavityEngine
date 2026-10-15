package org.javity.services;

/** Observable phases of an incremental scene request. */
public enum LoadState {
  LOADING,
  FADING_OUT,
  FADING_IN,
  COMPLETED,
  FAILED,
  CANCELLED;

  public boolean isDone() {
    return this == COMPLETED || this == FAILED || this == CANCELLED;
  }
}
