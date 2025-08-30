package com.cricri.monitoring;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.google.ortools.sat.CpSolverResponse;
import com.google.ortools.sat.CpSolverStatus;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConstraintMonitorTest {

  private static final Logger logger = LoggerFactory.getLogger(ConstraintMonitorTest.class);
  private ConstraintMonitor monitor;

  @BeforeEach
  public void setup() {
    monitor = new ConstraintMonitor();
  }

  @Test
  public void testConstraintMonitoring() {
    // Créer une réponse simulée avec de bonnes performances
    CpSolverResponse goodResponse = createMockResponse(0.05, 50, 2, 25, CpSolverStatus.OPTIMAL);

    // Tester le monitoring
    monitor.monitorConstraint("TestConstraint", goodResponse);

    // Vérifier que les métriques sont correctement collectées
    Map<String, ConstraintMetrics> history = monitor.getMetricsHistory();
    assertTrue(history.containsKey("TestConstraint"));

    ConstraintMetrics metrics = history.get("TestConstraint");
    assertEquals(0.05, metrics.getWallTime(), 0.001);
    assertEquals(50, metrics.getBranches());
    assertEquals(2, metrics.getConflicts());
    assertEquals(ConstraintMetrics.ComplexityLevel.MEDIUM, metrics.getComplexity());
  }

  @Test
  public void testPerformanceThresholds() {
    // Créer une réponse avec des métriques élevées (doit déclencher des alertes)
    CpSolverResponse slowResponse =
        createMockResponse(2.0, 15000, 150, 1000, CpSolverStatus.OPTIMAL);

    logger.info("Test avec des performances dégradées (doit déclencher des alertes) :");
    monitor.monitorConstraint("SlowConstraint", slowResponse);

    Map<String, ConstraintMetrics> history = monitor.getMetricsHistory();
    ConstraintMetrics metrics = history.get("SlowConstraint");

    assertEquals(ConstraintMetrics.ComplexityLevel.CRITICAL, metrics.getComplexity());
  }

  @Test
  public void testFailureAnalysis() {
    // Test avec échec rapide (presolve)
    CpSolverResponse infeasibleResponse =
        createMockResponse(0.01, 0, 0, 0, CpSolverStatus.INFEASIBLE);

    String diagnosis = monitor.analyzeSolverFailure(infeasibleResponse);
    assertTrue(diagnosis.contains("presolve"));

    logger.info("Diagnostic d'échec: {}", diagnosis);
  }

  @Test
  public void testComplexityLevels() {
    // Test des différents niveaux de complexité
    assertEquals(
        ConstraintMetrics.ComplexityLevel.TRIVIAL,
        ConstraintMetrics.ComplexityLevel.fromBranches(0));
    assertEquals(
        ConstraintMetrics.ComplexityLevel.LOW, ConstraintMetrics.ComplexityLevel.fromBranches(1));
    assertEquals(
        ConstraintMetrics.ComplexityLevel.MEDIUM,
        ConstraintMetrics.ComplexityLevel.fromBranches(100));
    assertEquals(
        ConstraintMetrics.ComplexityLevel.HIGH,
        ConstraintMetrics.ComplexityLevel.fromBranches(1000));
    assertEquals(
        ConstraintMetrics.ComplexityLevel.CRITICAL,
        ConstraintMetrics.ComplexityLevel.fromBranches(15000));
  }

  private CpSolverResponse createMockResponse(
      double wallTime, long branches, long conflicts, long propagations, CpSolverStatus status) {
    CpSolverResponse mockResponse = Mockito.mock(CpSolverResponse.class);
    when(mockResponse.getWallTime()).thenReturn(wallTime);
    when(mockResponse.getNumBranches()).thenReturn(branches);
    when(mockResponse.getNumConflicts()).thenReturn(conflicts);
    when(mockResponse.getNumIntegerPropagations()).thenReturn(propagations);
    when(mockResponse.getNumBooleans()).thenReturn(50L);
    when(mockResponse.getStatus()).thenReturn(status);
    return mockResponse;
  }
}
