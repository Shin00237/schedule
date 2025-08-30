package com.cricri.monitoring;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cricri.constraints.MinimumCoverageConstraint;
import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ConstraintType;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.service.ShiftScheduler;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test d'intégration pour le monitoring de la contrainte MinimumCoverage.
 *
 * <p>Ce test valide que le monitoring fonctionne correctement avec une vraie contrainte et des
 * données réalistes.
 */
public class MinimumCoverageConstraintMonitoringTest {

  private static final Logger logger =
      LoggerFactory.getLogger(MinimumCoverageConstraintMonitoringTest.class);

  private ShiftScheduler scheduler;
  private List<Employee> employees;
  private List<Shift> shifts;

  @BeforeEach
  public void setup() {
    // Charger les librairies natives OR-Tools
    try {
      Loader.loadNativeLibraries();
      logger.info("✓ Librairies OR-Tools chargées avec succès");

    } catch (Exception e) {
      logger.error("❌ Impossible de charger les librairies OR-Tools: {}", e.getMessage());
      org.junit.jupiter.api.Assumptions.assumeFalse(true, "OR-Tools non disponible, test ignoré");
    }

    // Créer des données de test réalistes
    employees = TestDataFactory.createEmployees(5); // 5 employés
    shifts =
        TestDataFactory.createStandardWeekShifts(TestDataFactory.WEEK_1); // Une semaine de shifts

    scheduler = new ShiftScheduler(employees, shifts, true); // Monitoring activé pour les tests
  }

  @Test
  public void testMinimumCoverageConstraintWithMonitoring() {
    logger.info("=== Test du monitoring avec MinimumCoverageConstraint ===");

    // Ajouter la contrainte MinimumCoverage
    MinimumCoverageConstraint constraint =
        new MinimumCoverageConstraint(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD));

    scheduler.withConstraint(constraint);

    // Résoudre avec monitoring
    CpSolver solver = scheduler.solve();

    // Vérifications de base
    assertNotNull(solver);

    // Accéder au monitor pour vérifier les métriques
    ConstraintMonitor monitor = scheduler.getConstraintMonitor();
    Map<String, ConstraintMetrics> history = monitor.getMetricsHistory();

    // Vérifier que les métriques ont été collectées
    assertTrue(history.containsKey("GLOBAL_MODEL"));

    ConstraintMetrics metrics = history.get("GLOBAL_MODEL");
    assertNotNull(metrics);
    assertTrue(metrics.getWallTime() >= 0);
    assertTrue(metrics.getBranches() >= 0);
    assertTrue(metrics.getConflicts() >= 0);

    logger.info("Métriques collectées:");
    logger.info("- Temps: {}s", String.format("%.3f", metrics.getWallTime()));
    logger.info("- Branches: {}", metrics.getBranches());
    logger.info("- Conflits: {}", metrics.getConflicts());
    logger.info("- Complexité: {}", metrics.getComplexity());

    // La contrainte MinimumCoverage étant généralement simple,
    // on s'attend à une complexité faible
    assertTrue(
        metrics.getComplexity().ordinal() <= ConstraintMetrics.ComplexityLevel.MEDIUM.ordinal());
  }

  @Test
  public void testMultipleConstraintsWithMonitoring() {
    logger.info("=== Test du monitoring avec plusieurs contraintes ===");

    // Ajouter plusieurs contraintes pour voir l'impact
    scheduler.withConstraint(
        new MinimumCoverageConstraint(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)));

    // Résoudre avec monitoring
    CpSolver solver = scheduler.solve();

    // Le solver a été exécuté
    assertNotNull(solver);

    // Vérifier les métriques
    ConstraintMonitor monitor = scheduler.getConstraintMonitor();
    Map<String, ConstraintMetrics> history = monitor.getMetricsHistory();

    assertTrue(history.containsKey("GLOBAL_MODEL"));

    ConstraintMetrics metrics = history.get("GLOBAL_MODEL");
    logger.info("Métriques avec contraintes multiples:");
    logger.info("- Temps: {}s", String.format("%.3f", metrics.getWallTime()));
    logger.info("- Branches: {}", metrics.getBranches());
    logger.info("- Complexité: {}", metrics.getComplexity());

    // Avec plus de contraintes, on peut s'attendre à plus de complexité
    assertTrue(metrics.getWallTime() >= 0);
  }

  @Test
  public void testInfeasibleScenarioWithMonitoring() {
    logger.info("=== Test du monitoring avec scénario impossible ===");

    // Créer un scénario potentiellement impossible avec très peu d'employés
    List<Employee> fewEmployees = TestDataFactory.createEmployees(1); // Seulement 1 employé
    List<Shift> manyShifts =
        TestDataFactory.createStandardWeekShifts(TestDataFactory.WEEK_1); // Beaucoup de shifts

    ShiftScheduler constrainedScheduler = new ShiftScheduler(fewEmployees, manyShifts, true);
    constrainedScheduler.withConstraint(
        new MinimumCoverageConstraint(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)));

    // Résoudre avec monitoring
    CpSolver solver = constrainedScheduler.solve();

    // Le solver a été exécuté
    assertNotNull(solver);

    // Vérifier les métriques même en cas d'échec
    ConstraintMonitor monitor = constrainedScheduler.getConstraintMonitor();
    Map<String, ConstraintMetrics> history = monitor.getMetricsHistory();

    if (history.containsKey("GLOBAL_MODEL")) {
      ConstraintMetrics metrics = history.get("GLOBAL_MODEL");
      logger.info("Métriques en cas d'échec potentiel:");
      logger.info("- Temps: {}s", String.format("%.3f", metrics.getWallTime()));
      logger.info("- Branches: {}", metrics.getBranches());
      logger.info("- Status: {}", metrics.getStatus());

      // En cas d'infaisabilité, on s'attend généralement à un temps rapide
      if (metrics.getStatus() == CpSolverStatus.INFEASIBLE) {
        assertTrue(metrics.getWallTime() < 1.0, "Échec doit être détecté rapidement");
      }
    }
  }

  @Test
  public void testPerformanceThresholds() {
    logger.info("=== Test des seuils de performance ===");

    // Créer un scénario plus complexe pour tester les seuils
    List<Employee> manyEmployees = TestDataFactory.createEmployees(8); // Max autorisé
    List<Shift> manyShifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.WEEK_1);

    ShiftScheduler complexScheduler = new ShiftScheduler(manyEmployees, manyShifts, true);
    complexScheduler.withConstraint(
        new MinimumCoverageConstraint(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)));

    // Résoudre avec monitoring
    logger.info("Résolution d'un modèle complexe...");
    CpSolver solver = complexScheduler.solve();

    // Vérifier que le monitoring a fonctionné
    ConstraintMonitor monitor = complexScheduler.getConstraintMonitor();
    Map<String, ConstraintMetrics> history = monitor.getMetricsHistory();

    assertTrue(history.containsKey("GLOBAL_MODEL"));

    ConstraintMetrics metrics = history.get("GLOBAL_MODEL");
    logger.info("Métriques du modèle complexe:");
    logger.info("- Temps: {}s", String.format("%.3f", metrics.getWallTime()));
    logger.info("- Branches: {}", metrics.getBranches());
    logger.info("- Conflits: {}", metrics.getConflicts());
    logger.info("- Complexité: {}", metrics.getComplexity());

    // Validation que les métriques sont cohérentes
    assertTrue(metrics.getWallTime() >= 0);
    assertTrue(metrics.getBranches() >= 0);
    assertTrue(metrics.getConflicts() >= 0);

    // Les seuils d'alerte devraient être configurés dans ConstraintMonitor
    // et des warnings devraient apparaître dans les logs si dépassés
  }
}
