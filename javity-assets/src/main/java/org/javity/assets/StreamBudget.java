package org.javity.assets;

/** Estimates include dependencies; updateMillis is a soft owner-thread time allowance. */
public record StreamBudget(int maxQueued, int maxInflight, long estimatedResidentBytes,
                           int maxResidentEntries, int updateMillis) {
  public StreamBudget {
    if (maxQueued <= 0 || maxInflight <= 0 || estimatedResidentBytes <= 0
        || maxResidentEntries <= 0 || updateMillis < 0)
      throw new IllegalArgumentException("Positive capacity and nonnegative updateMillis required");
  }
}
