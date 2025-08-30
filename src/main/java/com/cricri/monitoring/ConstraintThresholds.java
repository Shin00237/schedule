package com.cricri.monitoring;

public class ConstraintThresholds {

  private final double maxTime;
  private final long maxBranches;
  private final long maxConflicts;
  private final long maxPropagations;

  public ConstraintThresholds(
      double maxTime, long maxBranches, long maxConflicts, long maxPropagations) {
    this.maxTime = maxTime;
    this.maxBranches = maxBranches;
    this.maxConflicts = maxConflicts;
    this.maxPropagations = maxPropagations;
  }

  public double getMaxTime() {
    return maxTime;
  }

  public long getMaxBranches() {
    return maxBranches;
  }

  public long getMaxConflicts() {
    return maxConflicts;
  }

  public long getMaxPropagations() {
    return maxPropagations;
  }
}
