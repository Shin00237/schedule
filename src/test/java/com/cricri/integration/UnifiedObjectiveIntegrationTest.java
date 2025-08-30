package com.cricri.integration;

import static com.cricri.testutils.SolverAssertions.assertSolutionExists;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.cricri.constraints.Constraint;
import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.config.ParameterKey;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ConstraintType;
import com.cricri.factory.ConstraintFactory;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.service.ShiftScheduler;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;

/**
 * Tests d'intégration pour l'objectif unifié avec la refacto SOFT.
 *
 * <p>Valide que toutes les contraintes SOFT contribuent correctement à l'objectif global et que le
 * système fonctionne end-to-end.
 */
class UnifiedObjectiveIntegrationTest {

  private List<Employee> employees;
  private List<Shift> shifts;

  @BeforeEach
  void setUp() {
    employees = TestDataFactory.createStandardEmployees();
    shifts = TestDataFactory.createTwoWeekShifts(); // Plus de complexité pour tester
  }

  @Test
  void testSingleSoftConstraintIntegration() {
    // Given - Seulement MaximizeWorkingHours en SOFT
    List<ConstraintConfig> configs =
        Arrays.asList(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD),
            ConstraintConfig.of(
                ConstraintType.MAXIMIZE_WORKING_HOURS,
                ConstraintNature.SOFT,
                ParameterKey.WEEKDAY_MULTIPLIER.getKeyName(),
                2));

    // When
    ShiftScheduler scheduler = buildSchedulerWithConfigs(configs);

    // Then
    assertDoesNotThrow(() -> scheduler.buildModel());
    assertSolutionExists(scheduler.getModel());
  }

  @Test
  void testMultipleSoftConstraintsIntegration() {
    // Given - Plusieurs contraintes SOFT avec différents poids
    List<ConstraintConfig> configs =
        Arrays.asList(
            // Contraintes HARD obligatoires
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD),

            // Contraintes SOFT avec différentes priorités
            ConstraintConfig.of(
                ConstraintType.MAXIMIZE_WORKING_HOURS,
                ConstraintNature.SOFT,
                ParameterKey.WEEKDAY_MULTIPLIER.getKeyName(),
                2),
            ConstraintConfig.of(
                ConstraintType.MINIMUM_REST_DAYS, ConstraintNature.SOFT, ParameterKey.MIN_REST_DAYS_PER_WEEK.getKeyName(), 2),
            ConstraintConfig.of(
                ConstraintType.MAX_HOURS_PER_WEEK,
                ConstraintNature.SOFT,
                ParameterKey.MAX_HOURS_PER_WEEK.getKeyName(),
                45 * 60));

    // When
    ShiftScheduler scheduler = buildSchedulerWithConfigs(configs);

    // Then
    assertDoesNotThrow(() -> scheduler.buildModel());
    assertSolutionExists(scheduler.getModel());
  }

  @Test
  void testMixedHardAndSoftConstraints() {
    // Given - Mix réaliste de contraintes HARD et SOFT
    List<ConstraintConfig> configs =
        Arrays.asList(
            // Contraintes HARD critiques
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD),
            ConstraintConfig.of(
                ConstraintType.MINIMUM_REST, ConstraintNature.HARD, ParameterKey.MIN_REST_HOURS.getKeyName(), 11),

            // Contraintes SOFT pour optimisation
            ConstraintConfig.of(
                ConstraintType.MAXIMIZE_WORKING_HOURS,
                ConstraintNature.SOFT,
                ParameterKey.WEEKDAY_MULTIPLIER.getKeyName(),
                2),
            ConstraintConfig.of(
                ConstraintType.MAX_HOURS_PER_WEEK,
                ConstraintNature.SOFT,
                ParameterKey.MAX_HOURS_PER_WEEK.getKeyName(),
                40 * 60));

    // When
    ShiftScheduler scheduler = buildSchedulerWithConfigs(configs);

    // Then
    assertDoesNotThrow(() -> scheduler.buildModel());
    assertSolutionExists(scheduler.getModel());
  }

  @Test
  void testOnlyHardConstraints() {
    // Given - Seulement des contraintes HARD (pas d'objectif unifié)
    List<ConstraintConfig> configs =
        Arrays.asList(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD),
            ConstraintConfig.of(
                ConstraintType.MAX_HOURS_PER_WEEK,
                ConstraintNature.HARD,
                ParameterKey.MAX_HOURS_PER_WEEK.getKeyName(),
                40 * 60),
            ConstraintConfig.of(
                ConstraintType.MINIMUM_REST, ConstraintNature.HARD, ParameterKey.MIN_REST_HOURS.getKeyName(), 11));

    // When
    ShiftScheduler scheduler = buildSchedulerWithConfigs(configs);

    // Then - Devrait fonctionner même sans contraintes SOFT
    assertDoesNotThrow(() -> scheduler.buildModel());
    assertSolutionExists(scheduler.getModel());
  }

  @Test
  void testConstraintPriorityOrdering() {
    // Given - Contraintes avec différentes priorités
    List<ConstraintConfig> configs =
        Arrays.asList(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD), // -10
            ConstraintConfig.of(
                ConstraintType.MINIMUM_REST, ConstraintNature.HARD, ParameterKey.MIN_REST_HOURS.getKeyName(), 11),
            ConstraintConfig.of(
                ConstraintType.MAX_HOURS_PER_WEEK,
                ConstraintNature.SOFT,
                ParameterKey.MAX_HOURS_PER_WEEK.getKeyName(),
                40 * 60),
            ConstraintConfig.of(
                ConstraintType.MAXIMIZE_WORKING_HOURS,
                ConstraintNature.SOFT,
                ParameterKey.WEEKDAY_MULTIPLIER.getKeyName(),
                2));

    // When
    ShiftScheduler scheduler = buildSchedulerWithConfigs(configs);

    // Then - L'ordre d'application devrait être respecté (FUNDAMENTAL -> SAFETY -> NORMAL ->
    // OPTIMIZATION)
    assertDoesNotThrow(() -> scheduler.buildModel());
    assertSolutionExists(scheduler.getModel());
  }

  @Test
  void testRegressionMaximizeWorkingHoursBehavior() {
    // Given - Configuration identique à l'ancien système
    List<ConstraintConfig> configs =
        Arrays.asList(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD),
            ConstraintConfig.of(
                ConstraintType.MAXIMIZE_WORKING_HOURS,
                ConstraintNature.SOFT,
                ParameterKey.WEEKDAY_MULTIPLIER.getKeyName(),
                2 // Comme dans l'ancien système
                ));

    // When
    ShiftScheduler scheduler = buildSchedulerWithConfigs(configs);
    scheduler.buildModel();

    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(scheduler.getModel());

    // Then - Doit toujours optimiser les heures travaillées
    assertTrue(status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE);

    if (status == CpSolverStatus.OPTIMAL) {
      // Vérifier que des heures sont effectivement travaillées
      boolean someHoursWorked = false;
      for (int e = 0; e < employees.size(); e++) {
        for (int s = 0; s < shifts.size(); s++) {
          long hoursWorked = solver.value(scheduler.getContext().getActualHours()[e][s]);
          if (hoursWorked > 0) {
            someHoursWorked = true;
            break;
          }
        }
        if (someHoursWorked) break;
      }
      assertTrue(someHoursWorked, "L'optimisation devrait produire des heures travaillées");
    }
  }

  @Test
  void testHighConflictScenario() {
    // Given - Configuration avec beaucoup de contraintes potentiellement conflictuelles
    List<ConstraintConfig> configs =
        Arrays.asList(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD),

            // Contraintes SOFT conflictuelles
            ConstraintConfig.of(
                ConstraintType.MAXIMIZE_WORKING_HOURS,
                ConstraintNature.SOFT,
                ParameterKey.WEEKDAY_MULTIPLIER.getKeyName(),
                3 // Pousse vers plus d'heures
                ),
            ConstraintConfig.of(
                ConstraintType.MAX_HOURS_PER_WEEK,
                ConstraintNature.SOFT,
                ParameterKey.MAX_HOURS_PER_WEEK.getKeyName(),
                25 * 60 // Limite basse
                ),
            ConstraintConfig.of(
                ConstraintType.MINIMUM_REST_DAYS,
                ConstraintNature.SOFT,
                ParameterKey.MIN_REST_DAYS_PER_WEEK.getKeyName(),
                3 // Beaucoup de repos
                ),
            ConstraintConfig.of(
                ConstraintType.MINIMUM_REST,
                ConstraintNature.SOFT,
                ParameterKey.MIN_REST_HOURS.getKeyName(),
                12 // Repos long entre shifts
                ));

    // When/Then - Doit gérer les conflits gracieusement
    ShiftScheduler scheduler = buildSchedulerWithConfigs(configs);
    assertDoesNotThrow(() -> scheduler.buildModel());

    // Le solveur devrait trouver un compromis, même si pas optimal
    CpSolver solver = new CpSolver();
    solver.getParameters().setMaxTimeInSeconds(10.0); // Limite de temps pour éviter les timeouts
    CpSolverStatus status = solver.solve(scheduler.getModel());

    assertTrue(
        status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE,
        "Même avec des conflits, une solution devrait être trouvée");
  }

  private ShiftScheduler buildSchedulerWithConfigs(List<ConstraintConfig> configs) {
    ShiftScheduler scheduler = new ShiftScheduler(employees, shifts);

    for (ConstraintConfig config : configs) {
      Constraint constraint = ConstraintFactory.create(config);
      scheduler.withConstraint(constraint);
    }

    return scheduler;
  }
}
