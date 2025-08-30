package com.cricri.monitoring;

import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverResponse;
import com.google.ortools.sat.CpSolverStatus;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConstraintMonitor {

  private static final Logger logger = LoggerFactory.getLogger(ConstraintMonitor.class);

  private final Map<String, ConstraintThresholds> thresholds;
  private final Map<String, ConstraintMetrics> metricsHistory;

  public ConstraintMonitor() {
    this.thresholds = initializeThresholds();
    this.metricsHistory = new ConcurrentHashMap<>();
  }

  private Map<String, ConstraintThresholds> initializeThresholds() {
    return Map.of(
        "MinimumCoverage", new ConstraintThresholds(0.1, 50, 5, 10),
        "AssignmentHours", new ConstraintThresholds(0.3, 100, 15, 50),
        "MaxHoursPerWeek", new ConstraintThresholds(0.5, 200, 20, 100),
        "MinimumRest", new ConstraintThresholds(1.0, 500, 50, 200),
        "MinimumRestDays", new ConstraintThresholds(0.8, 300, 30, 150),
        "MaximizeWorkingHours", new ConstraintThresholds(0.2, 80, 10, 30),
        "ShiftOverlap", new ConstraintThresholds(0.5, 200, 20, 100),
        "GLOBAL_MODEL", new ConstraintThresholds(5.0, 1000, 100, 500));
  }

  public void monitorConstraint(String constraintName, CpSolverResponse response) {
    ConstraintMetrics metrics = extractMetrics(constraintName, response);
    metricsHistory.put(constraintName, metrics);

    checkThresholds(constraintName, metrics);
    reportPerformance(constraintName, metrics);
  }

  // Overload pour accepter CpSolver + CpSolverStatus directement
  public void monitorConstraint(String constraintName, CpSolver solver, CpSolverStatus status) {
    ConstraintMetrics metrics = extractMetricsFromSolver(constraintName, solver, status);
    metricsHistory.put(constraintName, metrics);

    checkThresholds(constraintName, metrics);
    reportPerformance(constraintName, metrics);
  }

  private ConstraintMetrics extractMetrics(String constraintName, CpSolverResponse response) {
    return ConstraintMetrics.builder()
        .constraintName(constraintName)
        .wallTime(response.getWallTime())
        .branches(response.getNumBranches())
        .conflicts(response.getNumConflicts())
        .propagations(response.getNumIntegerPropagations())
        .variablesAfterPresolve((int) response.getNumBooleans())
        .status(response.getStatus())
        .build();
  }

  private ConstraintMetrics extractMetricsFromSolver(
      String constraintName, CpSolver solver, CpSolverStatus status) {
    return ConstraintMetrics.builder()
        .constraintName(constraintName)
        .wallTime(solver.wallTime())
        .branches(solver.numBranches())
        .conflicts(solver.numConflicts())
        // .propagations(solver.numIntegerPropagations())
        // .variablesAfterPresolve((int) solver.numBooleans())
        .status(status)
        .build();
  }

  private void checkThresholds(String constraintName, ConstraintMetrics metrics) {
    ConstraintThresholds threshold = thresholds.get(constraintName);
    if (threshold == null) return;

    if (metrics.getWallTime() > threshold.getMaxTime()) {
      logger.warn(
          "[PERFORMANCE] Contrainte '{}' trop lente: {}s (seuil: {}s)",
          constraintName,
          String.format("%.3f", metrics.getWallTime()),
          String.format("%.3f", threshold.getMaxTime()));
    }

    if (metrics.getBranches() > threshold.getMaxBranches()) {
      logger.warn(
          "[COMPLEXITY] Contrainte '{}' génère trop de branches: {} (seuil: {})",
          constraintName,
          metrics.getBranches(),
          threshold.getMaxBranches());
    }

    if (metrics.getConflicts() > threshold.getMaxConflicts()) {
      logger.error(
          "[CONFLICT] Contrainte '{}' génère beaucoup de conflits: {} (seuil: {})",
          constraintName,
          metrics.getConflicts(),
          threshold.getMaxConflicts());
    }

    if (metrics.getPropagations() > threshold.getMaxPropagations()) {
      logger.info(
          "[PROPAGATION] Contrainte '{}' nécessite beaucoup de propagations: {} (seuil: {})",
          constraintName,
          metrics.getPropagations(),
          threshold.getMaxPropagations());
    }
  }

  private void reportPerformance(String constraintName, ConstraintMetrics metrics) {
    logger.info(
        "[METRICS] {} - Temps: {}s, Branches: {}, Conflits: {}, Propagations: {} ({})",
        constraintName,
        String.format("%.3f", metrics.getWallTime()),
        metrics.getBranches(),
        metrics.getConflicts(),
        metrics.getPropagations(),
        metrics.getComplexity().getDescription());
  }

  public Map<String, ConstraintMetrics> getMetricsHistory() {
    return Map.copyOf(metricsHistory);
  }

  public String analyzeSolverFailure(CpSolverResponse response) {
    // Analyse basée sur les métriques disponibles

    if (response.getNumBranches() == 0 && response.getNumConflicts() == 0) {
      return "Modèle fondamentalement impossible - "
          + "aucune exploration nécessaire. Probablement détecté au presolve.";
    }

    if (response.getNumBranches() > 0 && response.getNumConflicts() > 100) {
      return "Nombreux conflits détectés pendant la recherche. "
          + "Vérifiez la compatibilité entre vos contraintes.";
    }

    if (response.getWallTime() < 0.1 && response.getNumBranches() == 0) {
      return "Contradictions détectées rapidement au presolve. "
          + "Certaines règles s'excluent mutuellement.";
    }

    return "Aucune solution trouvée pour ce modèle. Analysez les contraintes actives.";
  }
}
