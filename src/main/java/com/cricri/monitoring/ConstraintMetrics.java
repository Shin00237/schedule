package com.cricri.monitoring;

import com.google.ortools.sat.CpSolverStatus;

public class ConstraintMetrics {

  private final String constraintName;
  private final double wallTime;
  private final long branches;
  private final long conflicts;
  private final long propagations;
  private final int variablesAfterPresolve;
  private final CpSolverStatus status;
  private final ComplexityLevel complexity;

  public enum ComplexityLevel {
    TRIVIAL(0, "Résolu au presolve"),
    LOW(1, "Résolution simple"),
    MEDIUM(100, "Résolution modérée"),
    HIGH(1000, "Résolution complexe"),
    CRITICAL(10000, "Résolution très complexe");

    private final int branchThreshold;
    private final String description;

    ComplexityLevel(int branchThreshold, String description) {
      this.branchThreshold = branchThreshold;
      this.description = description;
    }

    public static ComplexityLevel fromBranches(long branches) {
      if (branches == 0) return TRIVIAL;
      if (branches <= LOW.branchThreshold) return LOW;
      if (branches <= MEDIUM.branchThreshold) return MEDIUM;
      if (branches <= HIGH.branchThreshold) return HIGH;
      return CRITICAL;
    }

    public String getDescription() {
      return description;
    }
  }

  private ConstraintMetrics(Builder builder) {
    this.constraintName = builder.constraintName;
    this.wallTime = builder.wallTime;
    this.branches = builder.branches;
    this.conflicts = builder.conflicts;
    this.propagations = builder.propagations;
    this.variablesAfterPresolve = builder.variablesAfterPresolve;
    this.status = builder.status;
    this.complexity = ComplexityLevel.fromBranches(branches);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String constraintName;
    private double wallTime;
    private long branches;
    private long conflicts;
    private long propagations;
    private int variablesAfterPresolve;
    private CpSolverStatus status;

    public Builder constraintName(String constraintName) {
      this.constraintName = constraintName;
      return this;
    }

    public Builder wallTime(double wallTime) {
      this.wallTime = wallTime;
      return this;
    }

    public Builder branches(long branches) {
      this.branches = branches;
      return this;
    }

    public Builder conflicts(long conflicts) {
      this.conflicts = conflicts;
      return this;
    }

    public Builder propagations(long propagations) {
      this.propagations = propagations;
      return this;
    }

    public Builder variablesAfterPresolve(int variables) {
      this.variablesAfterPresolve = variables;
      return this;
    }

    public Builder status(CpSolverStatus status) {
      this.status = status;
      return this;
    }

    public ConstraintMetrics build() {
      return new ConstraintMetrics(this);
    }
  }

  public String getConstraintName() {
    return constraintName;
  }

  public double getWallTime() {
    return wallTime;
  }

  public long getBranches() {
    return branches;
  }

  public long getConflicts() {
    return conflicts;
  }

  public long getPropagations() {
    return propagations;
  }

  public int getVariablesAfterPresolve() {
    return variablesAfterPresolve;
  }

  public CpSolverStatus getStatus() {
    return status;
  }

  public ComplexityLevel getComplexity() {
    return complexity;
  }
}
