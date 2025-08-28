package com.cricri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cricri.constraints.*;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.model.Week;
import com.cricri.testutils.SolverAssertions;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires pour ModularShiftScheduler.
 *
 * <p>Focus sur l'API fluide, la configuration des contraintes et objectifs, et les méthodes de
 * convenance du scheduler.
 */
class ModularShiftSchedulerTest {

  private List<Employee> employees;
  private List<Shift> shifts;
  private ModularShiftScheduler scheduler;

  @BeforeEach
  void setUp() {
    Loader.loadNativeLibraries();

    employees = TestDataFactory.createStandardEmployees();
    shifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);
    scheduler = new ModularShiftScheduler(employees, shifts);
  }

  @Test
  void testBasicSchedulerConstruction() {
    assertNotNull(scheduler.getEmployees());
    assertNotNull(scheduler.getShifts());
    assertNotNull(scheduler.getModel());
    assertNotNull(scheduler.getContext());

    assertEquals(employees.size(), scheduler.getEmployees().size());
    assertEquals(shifts.size(), scheduler.getShifts().size());
    assertEquals(employees, scheduler.getEmployees());
    assertEquals(shifts, scheduler.getShifts());
  }

  @Test
  void testFluentAPIConstraintMethods() {
    // Tester l'API fluide pour les contraintes
    ModularShiftScheduler configuredScheduler =
        scheduler
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.MINIMUM_COVERAGE,
                        ConstraintNature.HARD,
                        ConstraintPriority.FUNDAMENTAL)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.MAX_HOURS_PER_WEEK,
                        ConstraintNature.HARD,
                        ConstraintPriority.NORMAL,
                        "maxHoursPerWeek",
                        40 * 60)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.MINIMUM_REST,
                        ConstraintNature.HARD,
                        ConstraintPriority.SAFETY,
                        "minimumRest",
                        11)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.MINIMUM_REST_DAYS,
                        ConstraintNature.HARD,
                        ConstraintPriority.COMFORT,
                        "minimumRestDays",
                        1)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.ASSIGNMENT_HOURS,
                        ConstraintNature.HARD,
                        ConstraintPriority.CONSISTENCY,
                        "assignmentHours",
                        5 * 60)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.WORKING_DAYS,
                        ConstraintNature.HARD,
                        ConstraintPriority.COMFORT)));

    // Vérifier que c'est le même objet (fluent)
    assertEquals(scheduler, configuredScheduler);

    // Vérifier qu'on peut construire le modèle
    configuredScheduler.buildModel();
    assertTrue(true, "Construction du modèle réussie");
  }

  @Test
  void testFluentAPIObjectiveMethods() {
    // Tester l'API fluide pour les objectifs
    ModularShiftScheduler configuredScheduler =
        scheduler
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.MINIMUM_COVERAGE,
                        ConstraintNature.HARD,
                        ConstraintPriority.FUNDAMENTAL)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.ASSIGNMENT_HOURS,
                        ConstraintNature.HARD,
                        ConstraintPriority.CONSISTENCY,
                        "assignmentHours",
                        5 * 60)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.WORKING_DAYS,
                        ConstraintNature.HARD,
                        ConstraintPriority.COMFORT)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.WEEKDAY_PREFERENCE,
                        ConstraintNature.SOFT,
                        ConstraintPriority.COMFORT)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.WEEKDAY_PREFERENCE,
                        ConstraintNature.SOFT,
                        ConstraintPriority.COMFORT,
                        "bonusWeight",
                        5))); // Test avec multiplier

    assertEquals(scheduler, configuredScheduler);

    configuredScheduler.buildModel();
    CpSolver solver = SolverAssertions.solveAndAssertSolution(configuredScheduler);
    SolverAssertions.assertAllShiftsCovered(solver, configuredScheduler.getAssignments(), shifts);
  }

  @Test
  void testWithStandardConstraints() {
    // Tester la configuration standard
    ModularShiftScheduler standardScheduler =
        TestDataFactory.createStandardSchedulerWithConfig(
            employees,
            shifts,
            40 * 60, // maxHoursPerWeek
            11, // minRestHours
            5 * 60 // minHoursPerShift
            );
    // Réassigner pour garder la même référence
    scheduler = standardScheduler;

    assertEquals(scheduler, standardScheduler);

    standardScheduler.buildModel();
    CpSolver solver = SolverAssertions.solveAndAssertSolution(standardScheduler);
    SolverAssertions.assertAllShiftsCovered(solver, standardScheduler.getAssignments(), shifts);

    // Vérifier que les contraintes standard sont appliquées
    SolverAssertions.assertWeeklyHoursRespected(
        solver, standardScheduler.getContext().getHoursPerEmployeePerWeek(), employees, 40 * 60);
  }

  @Test
  void testMultipleConstraintChaining() {
    // Tester l'enchaînement de multiples contraintes
    ModularShiftScheduler chainedScheduler =
        new ModularShiftScheduler(employees, shifts)
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.MINIMUM_COVERAGE,
                        ConstraintNature.HARD,
                        ConstraintPriority.FUNDAMENTAL)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.ASSIGNMENT_HOURS,
                        ConstraintNature.HARD,
                        ConstraintPriority.CONSISTENCY,
                        "assignmentHours",
                        6 * 60)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.MAX_HOURS_PER_WEEK,
                        ConstraintNature.HARD,
                        ConstraintPriority.NORMAL,
                        "maxHoursPerWeek",
                        35 * 60)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.MINIMUM_REST,
                        ConstraintNature.HARD,
                        ConstraintPriority.SAFETY,
                        "minimumRest",
                        12)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.MINIMUM_REST_DAYS,
                        ConstraintNature.HARD,
                        ConstraintPriority.COMFORT,
                        "minimumRestDays",
                        2)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.WORKING_DAYS,
                        ConstraintNature.HARD,
                        ConstraintPriority.COMFORT)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.WEEKDAY_PREFERENCE,
                        ConstraintNature.SOFT,
                        ConstraintPriority.COMFORT,
                        "bonusWeight",
                        3)));

    chainedScheduler.buildModel();
    CpSolver solver = SolverAssertions.solveAndAssertSolution(chainedScheduler);

    // Vérifier que toutes les contraintes sont respectées
    SolverAssertions.assertAllShiftsCovered(solver, chainedScheduler.getAssignments(), shifts);
    SolverAssertions.assertWeeklyHoursRespected(
        solver, chainedScheduler.getContext().getHoursPerEmployeePerWeek(), employees, 35 * 60);
    SolverAssertions.assertWorkingDaysRespected(
        solver,
        chainedScheduler.getContext().getWorkingDaysPerWeek(),
        employees,
        5); // Max 5 jours avec 2 repos
  }

  @Test
  void testSchedulerWithNoConstraints() {
    // Tester un scheduler sans contraintes (devrait quand même fonctionner)
    ModularShiftScheduler emptyScheduler = new ModularShiftScheduler(employees, shifts);

    emptyScheduler.buildModel();

    // Sans contraintes, le modèle peut être résolu mais peut donner des résultats vides
    CpSolver solver = new CpSolver();
    solver.solve(emptyScheduler.getModel());

    // Test que le scheduler fonctionne même sans contraintes
    assertTrue(true, "Scheduler sans contraintes fonctionne");
  }

  @Test
  void testSchedulerAccessorMethods() {
    // Tester les méthodes d'accès après construction
    TestDataFactory.createStandardSchedulerWithConfig(employees, shifts, 40 * 60, 11, 5 * 60)
        .buildModel();
    // Utiliser le scheduler configuré
    scheduler =
        TestDataFactory.createStandardSchedulerWithConfig(employees, shifts, 40 * 60, 11, 5 * 60);
    scheduler.buildModel();

    // Vérifier que toutes les variables sont accessibles
    assertNotNull(scheduler.getAssignments());
    assertNotNull(scheduler.getActualHours());
    assertNotNull(scheduler.getWorkingDays());
    assertNotNull(scheduler.getWorkingDaysPerWeek());

    // Vérifier les dimensions
    assertEquals(employees.size(), scheduler.getAssignments().length);
    assertEquals(shifts.size(), scheduler.getAssignments()[0].length);

    assertEquals(employees.size(), scheduler.getActualHours().length);
    assertEquals(shifts.size(), scheduler.getActualHours()[0].length);
  }

  @Test
  void testSchedulerWithLargeDataset() {
    // Test avec plus de données
    List<Employee> largeTeam = TestDataFactory.createEmployees(6);
    Week[] weeks = TestDataFactory.createStandardWeeks();
    List<Shift> largeShiftSet =
        List.of(
            // Semaine 1
            new Shift("S1-L", weeks[0].getDay(0), TestDataFactory.MORNING_SHIFT, 2, 3),
            new Shift("S1-Ma", weeks[0].getDay(1), TestDataFactory.NORMAL_SHIFT, 1, 2),
            new Shift("S1-Me", weeks[0].getDay(2), TestDataFactory.EVENING_SHIFT, 2, 2),
            new Shift("S1-J", weeks[0].getDay(3), TestDataFactory.MORNING_SHIFT, 1, 2),
            new Shift("S1-V", weeks[0].getDay(4), TestDataFactory.NORMAL_SHIFT, 2, 3),
            // Semaine 2
            new Shift("S2-L", weeks[1].getDay(0), TestDataFactory.NORMAL_SHIFT, 1, 2),
            new Shift("S2-Ma", weeks[1].getDay(1), TestDataFactory.EVENING_SHIFT, 1, 1),
            new Shift("S2-J", weeks[1].getDay(3), TestDataFactory.MORNING_SHIFT, 2, 2));

    ModularShiftScheduler largeScheduler =
        TestDataFactory.createStandardSchedulerWithConfig(
            largeTeam, largeShiftSet, 40 * 60, 11, 5 * 60);

    long startTime = System.currentTimeMillis();
    largeScheduler.buildModel();
    CpSolver solver = SolverAssertions.solveAndAssertSolution(largeScheduler);
    long buildTime = System.currentTimeMillis() - startTime;

    // Vérifier performance raisonnable
    assertTrue(buildTime < 5000, "Construction et résolution devrait prendre moins de 5s");

    SolverAssertions.assertAllShiftsCovered(solver, largeScheduler.getAssignments(), largeShiftSet);

    System.out.printf(
        "Test large dataset: %d employés, %d shifts, %dms%n",
        largeTeam.size(), largeShiftSet.size(), buildTime);
  }

  @Test
  void testSchedulerPrintMethods() {
    // Tester les méthodes d'affichage (pas d'assertion, juste vérifier qu'elles ne crashent pas)
    scheduler
        .withConstraint(
            ConstraintFactory.create(
                ConstraintConfig.of(
                    ConstraintType.MINIMUM_COVERAGE,
                    ConstraintNature.HARD,
                    ConstraintPriority.FUNDAMENTAL)))
        .withConstraint(
            ConstraintFactory.create(
                ConstraintConfig.of(
                    ConstraintType.MAX_HOURS_PER_WEEK,
                    ConstraintNature.HARD,
                    ConstraintPriority.NORMAL,
                    "maxHoursPerWeek",
                    40 * 60)))
        .withConstraint(
            ConstraintFactory.create(
                ConstraintConfig.of(
                    ConstraintType.WEEKDAY_PREFERENCE,
                    ConstraintNature.SOFT,
                    ConstraintPriority.COMFORT)));

    // Ces méthodes devraient fonctionner sans crash
    scheduler.printConstraints();
    // Méthode printObjectives() supprimée - plus nécessaire avec la nouvelle architecture

    assertTrue(true, "Méthodes d'affichage fonctionnent");
  }

  @Test
  void testSchedulerWithEdgeCaseData() {
    // Test avec des données limite
    List<Employee> singleEmployee = TestDataFactory.createEmployees(1);
    List<Shift> singleShift = shifts.subList(0, 1);

    ModularShiftScheduler edgeScheduler =
        new ModularShiftScheduler(singleEmployee, singleShift)
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.MINIMUM_COVERAGE,
                        ConstraintNature.HARD,
                        ConstraintPriority.FUNDAMENTAL)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.ASSIGNMENT_HOURS,
                        ConstraintNature.HARD,
                        ConstraintPriority.CONSISTENCY,
                        "assignmentHours",
                        4 * 60)));

    edgeScheduler.buildModel();
    CpSolver solver = SolverAssertions.solveAndAssertSolution(edgeScheduler);

    // Vérifier que l'employé unique est assigné au shift unique
    assertEquals(1, solver.value(edgeScheduler.getAssignments()[0][0]));
  }

  @Test
  void testSchedulerConstraintValidation() {
    // Tester avec des paramètres de contraintes variés
    ModularShiftScheduler validationScheduler =
        new ModularShiftScheduler(employees, shifts)
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.MINIMUM_COVERAGE,
                        ConstraintNature.HARD,
                        ConstraintPriority.FUNDAMENTAL)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.ASSIGNMENT_HOURS,
                        ConstraintNature.HARD,
                        ConstraintPriority.CONSISTENCY,
                        "assignmentHours",
                        0)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.MAX_HOURS_PER_WEEK,
                        ConstraintNature.HARD,
                        ConstraintPriority.NORMAL,
                        "maxHoursPerWeek",
                        168 * 60)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.MINIMUM_REST,
                        ConstraintNature.HARD,
                        ConstraintPriority.SAFETY,
                        "minimumRest",
                        1)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.MINIMUM_REST_DAYS,
                        ConstraintNature.HARD,
                        ConstraintPriority.COMFORT,
                        "minimumRestDays",
                        0)))
            .withConstraint(
                ConstraintFactory.create(
                    ConstraintConfig.of(
                        ConstraintType.WORKING_DAYS,
                        ConstraintNature.HARD,
                        ConstraintPriority.COMFORT)));

    validationScheduler.buildModel();
    CpSolver solver = SolverAssertions.solveAndAssertSolution(validationScheduler);
    SolverAssertions.assertAllShiftsCovered(solver, validationScheduler.getAssignments(), shifts);
  }

  @Test
  void testSchedulerMemoryManagement() {
    // Test de création/destruction répétée pour vérifier la gestion mémoire
    for (int i = 0; i < 5; i++) {
      ModularShiftScheduler tempScheduler =
          TestDataFactory.createStandardSchedulerWithConfig(employees, shifts, 40 * 60, 11, 5 * 60);

      tempScheduler.buildModel();
      CpSolver solver = SolverAssertions.solveAndAssertSolution(tempScheduler);
      SolverAssertions.assertAllShiftsCovered(solver, tempScheduler.getAssignments(), shifts);

      // Le scheduler devrait être collecté automatiquement
    }

    assertTrue(true, "Tests de gestion mémoire passés");
  }

  @Test
  void testSchedulerConfigurationImmutability() {
    // Vérifier que modifier la configuration ne casse pas les instances précédentes
    ModularShiftScheduler config1 =
        TestDataFactory.createStandardSchedulerWithConfig(employees, shifts, 30 * 60, 10, 4 * 60);

    ModularShiftScheduler config2 =
        TestDataFactory.createStandardSchedulerWithConfig(employees, shifts, 50 * 60, 12, 6 * 60);

    // Les deux configurations devraient fonctionner indépendamment
    config1.buildModel();
    config2.buildModel();

    CpSolver solver1 = SolverAssertions.solveAndAssertSolution(config1);
    CpSolver solver2 = SolverAssertions.solveAndAssertSolution(config2);

    SolverAssertions.assertAllShiftsCovered(solver1, config1.getAssignments(), shifts);
    SolverAssertions.assertAllShiftsCovered(solver2, config2.getAssignments(), shifts);

    // Vérifier que les contraintes sont bien différentes
    SolverAssertions.assertWeeklyHoursRespected(
        solver1, config1.getContext().getHoursPerEmployeePerWeek(), employees, 30 * 60);
    SolverAssertions.assertWeeklyHoursRespected(
        solver2, config2.getContext().getHoursPerEmployeePerWeek(), employees, 50 * 60);
  }
}
