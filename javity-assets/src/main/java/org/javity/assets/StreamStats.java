package org.javity.assets;

/** Immutable root-level diagnostics, not a GPU-memory measurement.
 * queued counts live waiting roots; inflight is actual admitted work (0/1 in this implementation).
 * ready includes unpinned cache; pinned counts READY roots with at least one live lease.
 * estimatedResidentBytes includes ready roots AND the active transaction's reservation until
 * completion/rollback. Dependency-inclusive root estimates may count shared dependencies twice.
 * overBudget means at least one live queued root cannot fit current reservations/residents by
 * bytes or entry count. It reports demand pressure, including reclaimable cache, even though
 * admission keeps estimates within limits. A busy loader alone does not imply overBudget. */
public record StreamStats(int queued, int inflight, int ready, int pinned,
                          long estimatedResidentBytes, boolean overBudget) {}
