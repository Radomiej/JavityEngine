package org.javity.assets;

/** Root-level queue/resident limits. Estimates include dependencies; active loads reserve bytes
 * and one entry before admission. maxInflight is an upper bound (effective concurrency is one).
 * updateMillis is a soft owner-thread allowance; zero still advances one manager step. */
public record StreamBudget(int maxQueued, int maxInflight, long estimatedResidentBytes,
                           int maxResidentEntries, int updateMillis) {
  public StreamBudget {
    if (maxQueued <= 0 || maxInflight <= 0 || estimatedResidentBytes <= 0
        || maxResidentEntries <= 0 || updateMillis < 0)
      throw new IllegalArgumentException("Positive capacity and nonnegative updateMillis required");
  }
}
