package com.cricri.monitoring;

import static org.junit.jupiter.api.Assertions.*;

import com.cricri.constraints.MinimumCoverageConstraint;
import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ConstraintType;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.service.ShiftScheduler;
import com.cricri.testutils.TestDataFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test d'intégration simple pour valider la structure du monitoring.
 *
 * <p>Ce test valide que le monitoring est correctement intégré dans ShiftScheduler sans dépendre
 * des librairies natives OR-Tools qui peuvent poser problème.
 */
public class ConstraintMonitoringIntegrationTest {

  private static final Logger logger =
      LoggerFactory.getLogger(ConstraintMonitoringIntegrationTest.class);

  private ShiftScheduler scheduler;
  private List<Employee> employees;
  private List<Shift> shifts;

  @BeforeEach
  public void setup() {
    // Créer des données de test avec des limites respectées
    employees = TestDataFactory.createEmployees(3); // Max 8, donc 3 c'est safe
    shifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.WEEK_1);

    scheduler = new ShiftScheduler(employees, shifts);

    logger.info("=== Setup test intégration monitoring ===");
    logger.info("Employés créés: {}", employees.size());
    logger.info("Shifts créés: {}", shifts.size());
  }

  @Test
  public void testConstraintMonitorIntegration() {
    logger.info("=== Test intégration basique du monitoring ===");

    // Vérifier que le scheduler a un monitor
    ConstraintMonitor monitor = scheduler.getConstraintMonitor();
    assertNotNull(monitor, "Le ShiftScheduler doit avoir un ConstraintMonitor");

    // Vérifier que l'historique est vide au début
    assertTrue(
        monitor.getMetricsHistory().isEmpty(),
        "L'historique des métriques doit être vide au début");

    // Ajouter une contrainte
    MinimumCoverageConstraint constraint =
        new MinimumCoverageConstraint(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD));

    scheduler.withConstraint(constraint);

    // Vérifier que la contrainte a été ajoutée
    assertEquals(1, scheduler.getConstraints().size());

    logger.info("✓ Monitoring correctement intégré dans ShiftScheduler");
    logger.info("✓ Contrainte ajoutée avec succès");
  }

  @Test
  public void testConstraintMonitorConfiguration() {
    logger.info("=== Test configuration du monitoring ===");

    ConstraintMonitor monitor = scheduler.getConstraintMonitor();

    // Le monitor doit avoir des seuils configurés pour les contraintes connues
    assertNotNull(monitor);

    // Créer des métriques simulées pour tester le monitoring
    ConstraintMetrics metrics =
        ConstraintMetrics.builder()
            .constraintName("TestConstraint")
            .wallTime(0.1)
            .branches(50)
            .conflicts(2)
            .propagations(100)
            .variablesAfterPresolve(25)
            .status(com.google.ortools.sat.CpSolverStatus.OPTIMAL)
            .build();

    // Valider que les métriques sont correctement construites
    assertEquals("TestConstraint", metrics.getConstraintName());
    assertEquals(0.1, metrics.getWallTime(), 0.001);
    assertEquals(50, metrics.getBranches());
    assertEquals(2, metrics.getConflicts());
    assertEquals(ConstraintMetrics.ComplexityLevel.MEDIUM, metrics.getComplexity());

    logger.info(
        "✓ Métriques correctement créées: {} ({})",
        metrics.getConstraintName(),
        metrics.getComplexity());
  }

  @Test
  public void testMonitorThresholds() {
    logger.info("=== Test seuils de monitoring ===");

    ConstraintMonitor monitor = scheduler.getConstraintMonitor();

    // Test avec métriques normales (ne doit pas déclencher d'alertes)
    ConstraintMetrics normalMetrics =
        ConstraintMetrics.builder()
            .constraintName("NormalConstraint")
            .wallTime(0.05) // Sous le seuil
            .branches(10) // Sous le seuil
            .conflicts(1) // Sous le seuil
            .propagations(20) // Sous le seuil
            .variablesAfterPresolve(30)
            .status(com.google.ortools.sat.CpSolverStatus.OPTIMAL)
            .build();

    // Simuler le monitoring (doit passer sans alertes)
    monitor.monitorConstraint("NormalConstraint", createMockResponse(normalMetrics));

    // Vérifier que les métriques ont été enregistrées
    assertTrue(monitor.getMetricsHistory().containsKey("NormalConstraint"));

    logger.info("✓ Monitoring de contrainte normale réussi");

    // Test avec métriques élevées (doit déclencher des alertes)
    logger.info("Test avec métriques élevées (doit générer des warnings):");

    ConstraintMetrics highMetrics =
        ConstraintMetrics.builder()
            .constraintName("SlowConstraint")
            .wallTime(2.0) // Au-dessus du seuil
            .branches(5000) // Au-dessus du seuil
            .conflicts(200) // Au-dessus du seuil
            .propagations(2000)
            .variablesAfterPresolve(100)
            .status(com.google.ortools.sat.CpSolverStatus.OPTIMAL)
            .build();

    monitor.monitorConstraint("SlowConstraint", createMockResponse(highMetrics));

    // Vérifier que les métriques ont été enregistrées
    assertTrue(monitor.getMetricsHistory().containsKey("SlowConstraint"));
    assertEquals(ConstraintMetrics.ComplexityLevel.CRITICAL, highMetrics.getComplexity());

    logger.info("✓ Monitoring de contrainte lente réussi (avec alertes attendues)");
  }

  @Test
  public void testFailureAnalysis() {
    logger.info("=== Test analyse d'échec ===");

    ConstraintMonitor monitor = scheduler.getConstraintMonitor();

    // Test d'analyse avec échec rapide (presolve)
    ConstraintMetrics failureMetrics =
        ConstraintMetrics.builder()
            .constraintName("FailedConstraint")
            .wallTime(0.01) // Très rapide
            .branches(0) // Aucune branche
            .conflicts(0) // Aucun conflit
            .propagations(0)
            .variablesAfterPresolve(50)
            .status(com.google.ortools.sat.CpSolverStatus.INFEASIBLE)
            .build();

    String diagnosis = monitor.analyzeSolverFailure(createMockResponse(failureMetrics));

    // Vérifier que le diagnostic est pertinent
    assertNotNull(diagnosis);
    assertTrue(diagnosis.contains("presolve") || diagnosis.contains("impossible"));

    logger.info("Diagnostic d'échec: {}", diagnosis);
    logger.info("✓ Analyse d'échec fonctionnelle");
  }

  // Helper pour créer un mock response simple
  private com.google.ortools.sat.CpSolverResponse createMockResponse(ConstraintMetrics metrics) {
    return org.mockito.Mockito.mock(
        com.google.ortools.sat.CpSolverResponse.class,
        invocation -> {
          String methodName = invocation.getMethod().getName();
          return switch (methodName) {
            case "getWallTime" -> metrics.getWallTime();
            case "getNumBranches" -> metrics.getBranches();
            case "getNumConflicts" -> metrics.getConflicts();
            case "getNumIntegerPropagations" -> metrics.getPropagations();
            case "getNumBooleans" -> (long) metrics.getVariablesAfterPresolve();
            case "getStatus" -> metrics.getStatus();
            default -> invocation.callRealMethod();
          };
        });
  }
}
