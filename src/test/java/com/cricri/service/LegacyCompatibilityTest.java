package com.cricri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.testutils.SolverAssertions;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;

/**
 * Tests de compatibilité avec l'ancienne version de ShiftSchedulerTest.
 * 
 * Assure que la migration vers les nouveaux tests n'a pas cassé 
 * les fonctionnalités existantes.
 */
class LegacyCompatibilityTest {

  private List<Employee> employees;
  private List<Shift> shifts;

  @BeforeEach
  void setUp() {
    Loader.loadNativeLibraries();
    
    employees = TestDataFactory.createEmployees(4); // Comme dans l'ancien test
    shifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);
  }

  @Test
  void testLegacyStandardConfigurationStillWorks() {
    // Recréer la configuration "standard" de l'ancien test
    ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts)
        .withStandardConstraints(
            40 * 60, // 40h par semaine
            11,      // 11h de repos minimum  
            5 * 60   // 5h minimum par shift
        );

    scheduler.buildModel();
    CpSolver solver = SolverAssertions.solveAndAssertSolution(scheduler);

    // Vérifications similaires à l'ancien test
    SolverAssertions.assertAllShiftsCovered(solver, scheduler.getAssignments(), shifts);
    SolverAssertions.assertWeeklyHoursRespected(
        solver, scheduler.getContext().getHoursPerEmployeePerWeek(),
        employees, 40 * 60);
  }

  @Test
  void testLegacyWeekdayOptimizationStillWorks() {
    // Test similaire à testWeekdayStaffingObjective de l'ancien code
    ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts)
        .withMinimumCoverage()
        .withAssignmentHours(5 * 60)
        .withMaxHoursPerWeek(40 * 60)
        .withWorkingDays()
        .withMinimumRestDays(1)
        .withWeekdayPreference();

    scheduler.buildModel();
    CpSolver solver = SolverAssertions.solveAndAssertSolution(scheduler);

    // Analyser la répartition weekday/weekend comme dans l'ancien test
    int weekdayEmployees = 0;
    int weekendEmployees = 0;

    for (int s = 0; s < shifts.size(); s++) {
      Shift shift = shifts.get(s);
      int employeesAssigned = 0;

      for (int e = 0; e < employees.size(); e++) {
        if (solver.value(scheduler.getAssignments()[e][s]) == 1) {
          employeesAssigned++;
        }
      }

      if (shift.day().isWeekend()) {
        weekendEmployees += employeesAssigned;
      } else {
        weekdayEmployees += employeesAssigned;
      }

      // Vérification identique à l'ancien test
      assertTrue(employeesAssigned >= shift.minEmployes(),
          "Le shift " + shift.id() + " doit avoir au moins " + shift.minEmployes() + " employé(s)");
    }

    // Même assertion que dans l'ancien test
    assertTrue(weekdayEmployees >= weekendEmployees,
        "L'optimisation devrait privilégier les jours en semaine");

    System.out.printf("Compatibilité: Employés en semaine: %d, weekend: %d%n", 
        weekdayEmployees, weekendEmployees);
  }

  @Test
  void testLegacyMethodsStillAccessible() {
    // Vérifier que les méthodes utilisées dans l'ancien test sont toujours disponibles
    ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts)
        .withStandardConstraints(40 * 60, 11, 5 * 60);

    scheduler.buildModel();

    // Méthodes d'accès utilisées dans l'ancien test
    assertNotNull(scheduler.getModel(), "getModel() devrait être accessible");
    assertNotNull(scheduler.getEmployees(), "getEmployees() devrait être accessible");
    assertNotNull(scheduler.getShifts(), "getShifts() devrait être accessible");
    assertNotNull(scheduler.getAssignments(), "getAssignments() devrait être accessible");
    assertNotNull(scheduler.getActualHours(), "getActualHours() devrait être accessible");
    assertNotNull(scheduler.getWorkingDays(), "getWorkingDays() devrait être accessible");
    assertNotNull(scheduler.getWorkingDaysPerWeek(), "getWorkingDaysPerWeek() devrait être accessible");

    // Vérifier que les dimensions sont correctes
    assertEquals(employees.size(), scheduler.getEmployees().size());
    assertEquals(shifts.size(), scheduler.getShifts().size());
    assertEquals(employees.size(), scheduler.getAssignments().length);
    assertEquals(shifts.size(), scheduler.getAssignments()[0].length);
  }

  @Test
  void testLegacyTwoWeekScenarioStillWorks() {
    // Recréer le scénario "deux semaines" de l'ancien test
    List<Shift> twoWeekShifts = TestDataFactory.createTwoWeekShifts(
        TestDataFactory.createStandardWeeks()[0],
        TestDataFactory.createStandardWeeks()[1]);

    ModularShiftScheduler scheduler = new ModularShiftScheduler(employees.subList(0, 2), twoWeekShifts)
        .withMinimumCoverage()
        .withAssignmentHours(5 * 60)
        .withMaxHoursPerWeek(40 * 60)
        .withWorkingDays();

    scheduler.buildModel();
    CpSolver solver = SolverAssertions.solveAndAssertSolution(scheduler);

    // Vérifications similaires à l'ancien test
    SolverAssertions.assertAllShiftsCovered(solver, scheduler.getAssignments(), twoWeekShifts);

    // Vérifier que les heures par semaine sont respectées pour chaque semaine
    for (int e = 0; e < 2; e++) {
      for (int w = 0; w < 2; w++) {
        long hoursInWeek = solver.value(scheduler.getContext().getHoursPerEmployeePerWeek()[e][w]);
        assertTrue(hoursInWeek <= 40 * 60,
            String.format("L'employé %d semaine %d dépasse la limite: %.1fh > 40h",
                e + 1, w + 1, hoursInWeek / 60.0));
      }
    }
  }

  @Test  
  void testLegacyConflictScenarioStillWorks() {
    // Tester un scénario de conflit similaire à l'ancien test
    List<Shift> conflictShifts = TestDataFactory.createConflictingShifts(
        TestDataFactory.createStandardWeeks()[0]);

    ModularShiftScheduler scheduler = new ModularShiftScheduler(employees.subList(0, 2), conflictShifts)
        .withMinimumCoverage()
        .withAssignmentHours(5 * 60)
        .withMinimumRest(11);

    scheduler.buildModel();
    CpSolver solver = SolverAssertions.solveAndAssertSolution(scheduler);

    // Vérifier que les conflits de repos sont bien gérés
    SolverAssertions.assertAllShiftsCovered(solver, scheduler.getAssignments(), conflictShifts);

    // Vérifier qu'aucun employé n'a de conflit de repos (comme dans l'ancien test)
    for (int e = 0; e < 2; e++) {
      boolean assignedToVendrediMatin = solver.value(scheduler.getAssignments()[e][0]) == 1;
      boolean assignedToVendrediSoir = solver.value(scheduler.getAssignments()[e][1]) == 1;

      // Cette vérification était dans l'ancien test
      assertTrue(!(assignedToVendrediMatin && assignedToVendrediSoir),
          "L'employé " + (e + 1) + " ne peut pas faire Vendredi matin ET soir");
    }
  }

  private void assertNotNull(Object object, String message) {
    assertTrue(object != null, message);
  }
}