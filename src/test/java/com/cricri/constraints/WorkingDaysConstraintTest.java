package com.cricri.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cricri.constraints.enums.ConstraintPriority;
import com.cricri.model.Shift;
import com.cricri.service.SchedulingContext;
import com.cricri.testutils.ConstraintTestBase;
import com.cricri.testutils.SolverAssertions;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.sat.CpSolver;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires pour WorkingDaysConstraint.
 *
 * <p>Cette contrainte établit la cohérence entre les assignations de shifts et les variables de
 * jours travaillés. Elle assure que : - Si un employé travaille un shift un jour donné →
 * workingDay[jour] = true - workingDaysPerWeek = somme des workingDays de la semaine
 */
class WorkingDaysConstraintTest extends ConstraintTestBase {

  private WorkingDaysConstraint constraint;

  @Override
  protected void setupSpecific() {
    constraint = new WorkingDaysConstraint();
  }

  @Test
  void constraintPropertiesTest() {
    testConstraintProperties(constraint);

    assertEquals("WorkingDays(HARD)", constraint.getName());
    assertEquals(ConstraintPriority.CONSISTENCY, constraint.getPriority());
  }

  @Test
  void basicWorkingDaysConsistencyTest() {
    // Appliquer les contraintes de base pour avoir des assignations
    new MinimumCoverageConstraint().applyHardConstraint(context);
    new AssignmentHoursConstraint(5 * 60).applyHardConstraint(context);
    constraint.applyHardConstraint(context);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // Vérifier la cohérence assignations → workingDays
    verifyAssignmentWorkingDayConsistency(solver, context, shifts);
  }

  @Test
  void workingDaysWithMultipleShiftsPerDayTest() {
    // Créer des shifts avec plusieurs shifts le même jour
    List<Shift> multiShiftsPerDay =
        List.of(
            new Shift("Lundi-MATIN", week1.getDay(0), TestDataFactory.MORNING_SHIFT, 1, 1),
            new Shift("Lundi-SOIR", week1.getDay(0), TestDataFactory.EVENING_SHIFT, 1, 1),
            new Shift("Mardi-NORMAL", week1.getDay(1), TestDataFactory.NORMAL_SHIFT, 1, 1));

    SchedulingContext multiShiftContext =
        TestDataFactory.createContext(employees, multiShiftsPerDay);

    new MinimumCoverageConstraint().applyHardConstraint(multiShiftContext);
    new AssignmentHoursConstraint(5 * 60).applyHardConstraint(multiShiftContext);
    constraint.applyHardConstraint(multiShiftContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(multiShiftContext);

    // Un employé assigné aux shifts Lundi-MATIN et Lundi-SOIR
    // devrait avoir workingDay[lundi] = true (pas deux fois true)
    for (int e = 0; e < employees.size(); e++) {
      boolean assignedLundiMatin = solver.value(multiShiftContext.getAssignments()[e][0]) == 1;
      boolean assignedLundiSoir = solver.value(multiShiftContext.getAssignments()[e][1]) == 1;
      boolean workingLundi = solver.value(multiShiftContext.getWorkingDays()[e][0][0]) == 1;

      if (assignedLundiMatin || assignedLundiSoir) {
        assertTrue(
            workingLundi,
            "L'employé "
                + employees.get(e).nom()
                + " travaille lundi mais workingDay[lundi] = false");
      }
    }
  }

  @Test
  void workingDaysPerWeekCalculationTest() {
    new MinimumCoverageConstraint().applyHardConstraint(context);
    new AssignmentHoursConstraint(5 * 60).applyHardConstraint(context);
    constraint.applyHardConstraint(context);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // Vérifier que workingDaysPerWeek = somme des workingDays
    for (int e = 0; e < employees.size(); e++) {
      int expectedWorkingDays = 0;

      // Compter les jours réellement travaillés
      for (int d = 0; d < 7; d++) {
        if (solver.value(context.getWorkingDays()[e][0][d]) == 1) {
          expectedWorkingDays++;
        }
      }

      long actualWorkingDaysPerWeek = solver.value(context.getWorkingDaysPerWeek()[e][0]);

      assertEquals(
          expectedWorkingDays,
          actualWorkingDaysPerWeek,
          "workingDaysPerWeek devrait égaler la somme des workingDays pour "
              + employees.get(e).nom());
    }
  }

  @Test
  void workingDaysAcrossMultipleWeeksTest() {
    SchedulingContext twoWeekContext = createTwoWeekScenario();

    new MinimumCoverageConstraint().applyHardConstraint(twoWeekContext);
    new AssignmentHoursConstraint(5 * 60).applyHardConstraint(twoWeekContext);
    constraint.applyHardConstraint(twoWeekContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(twoWeekContext);
    List<Shift> twoWeekShifts = TestDataFactory.createTwoWeekShifts(week1, week2);

    // Vérifier la cohérence pour chaque semaine
    for (int e = 0; e < employees.size(); e++) {
      for (int w = 0; w < 2; w++) { // 2 semaines
        int expectedWorkingDays = 0;

        // Compter les jours travaillés dans cette semaine
        for (int d = 0; d < 7; d++) {
          if (solver.value(twoWeekContext.getWorkingDays()[e][w][d]) == 1) {
            expectedWorkingDays++;
          }
        }

        long actualWorkingDays = solver.value(twoWeekContext.getWorkingDaysPerWeek()[e][w]);
        assertEquals(
            expectedWorkingDays,
            actualWorkingDays,
            String.format(
                "Semaine %d, employé %s: incohérence workingDays", w + 1, employees.get(e).nom()));
      }
    }

    // Vérifier que les assignations correspondent bien aux bonnes semaines
    verifyAssignmentWorkingDayConsistency(solver, twoWeekContext, twoWeekShifts);
  }

  @Test
  void workingDaysWithNoAssignmentsTest() {
    // Créer un scénario où certains employés n'ont aucune assignation
    List<com.cricri.model.Employee> manyEmployees = TestDataFactory.createEmployees(6);
    List<Shift> fewShifts = shifts.subList(0, 2); // Seulement 2 shifts
    SchedulingContext sparsContext = TestDataFactory.createContext(manyEmployees, fewShifts);

    new MinimumCoverageConstraint().applyHardConstraint(sparsContext);
    new AssignmentHoursConstraint(5 * 60).applyHardConstraint(sparsContext);
    constraint.applyHardConstraint(sparsContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(sparsContext);

    // Vérifier la cohérence générale mais sans supposer workingDaysPerWeek = 0 pour non-assignés
    // car OR-Tools peut optimiser différemment
    for (int e = 0; e < manyEmployees.size(); e++) {
      boolean hasAnyAssignment = false;

      for (int s = 0; s < fewShifts.size(); s++) {
        if (solver.value(sparsContext.getAssignments()[e][s]) == 1) {
          hasAnyAssignment = true;
          break;
        }
      }

      long workingDays = solver.value(sparsContext.getWorkingDaysPerWeek()[e][0]);

      if (hasAnyAssignment) {
        assertTrue(
            workingDays > 0,
            "L'employé "
                + manyEmployees.get(e).nom()
                + " avec assignation devrait avoir >0 jours travaillés");
      }
      // Note: Ne pas vérifier workingDays = 0 pour non-assignés car la contrainte
      // permet workingDays >= assignments, pas d'égalité stricte
    }
  }

  @Test
  void workingDaysWithComplexShiftPatternsTest() {
    // Créer un pattern complexe : shifts sur différents jours et types
    List<Shift> complexPattern =
        List.of(
            new Shift("Lundi-MATIN", week1.getDay(0), TestDataFactory.MORNING_SHIFT, 1, 1),
            new Shift("Mercredi-SOIR", week1.getDay(2), TestDataFactory.EVENING_SHIFT, 1, 1),
            new Shift("Vendredi-NUIT", week1.getDay(4), TestDataFactory.NIGHT_SHIFT, 1, 1),
            new Shift("Dimanche-NORMAL", week1.getDay(6), TestDataFactory.NORMAL_SHIFT, 1, 1));

    SchedulingContext complexContext = TestDataFactory.createContext(employees, complexPattern);

    new MinimumCoverageConstraint().applyHardConstraint(complexContext);
    new AssignmentHoursConstraint(5 * 60).applyHardConstraint(complexContext);
    constraint.applyHardConstraint(complexContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(complexContext);

    // Vérifier que chaque jour d'assignation correspond à un workingDay
    for (int e = 0; e < employees.size(); e++) {
      boolean[] expectedWorkingDays = new boolean[7];

      // Mapper les assignations aux jours
      for (int s = 0; s < complexPattern.size(); s++) {
        if (solver.value(complexContext.getAssignments()[e][s]) == 1) {
          int dayOfWeek = complexPattern.get(s).day().getDayInWeek();
          expectedWorkingDays[dayOfWeek] = true;
        }
      }

      // Vérifier workingDays
      for (int d = 0; d < 7; d++) {
        boolean actualWorkingDay = solver.value(complexContext.getWorkingDays()[e][0][d]) == 1;
        if (expectedWorkingDays[d]) {
          assertTrue(
              actualWorkingDay,
              String.format(
                  "L'employé %s devrait travailler le jour %d", employees.get(e).nom(), d));
        }
      }
    }
  }

  @Test
  void workingDaysConstraintConsistencyTest() {
    // Test de cohérence globale avec toutes les contraintes
    new MinimumCoverageConstraint().applyHardConstraint(context);
    new AssignmentHoursConstraint(5 * 60).applyHardConstraint(context);
    constraint.applyHardConstraint(context);
    new MinimumRestDaysConstraint(1).applyHardConstraint(context); // Max 6 jours travaillés

    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // Vérifier que workingDaysPerWeek respecte la limite de MinimumRestDaysConstraint
    for (int e = 0; e < employees.size(); e++) {
      long workingDays = solver.value(context.getWorkingDaysPerWeek()[e][0]);
      assertTrue(
          workingDays <= 6, "workingDaysPerWeek devrait respecter la contrainte MinimumRestDays");
    }

    // Tous les shifts doivent être couverts
    SolverAssertions.assertAllShiftsCovered(solver, context.getAssignments(), shifts);
  }

  @Test
  void workingDaysEdgeCasesTest() {
    // Test avec des cas limites : shifts à minuit, weekend, etc.
    List<Shift> edgeCases =
        List.of(
            new Shift(
                "Samedi-NORMAL", week1.getDay(5), TestDataFactory.NORMAL_SHIFT, 1, 1), // Weekend
            new Shift(
                "Dimanche-MATIN", week1.getDay(6), TestDataFactory.MORNING_SHIFT, 1, 1) // Dimanche
            );

    SchedulingContext edgeContext = TestDataFactory.createContext(employees, edgeCases);

    new MinimumCoverageConstraint().applyHardConstraint(edgeContext);
    new AssignmentHoursConstraint(5 * 60).applyHardConstraint(edgeContext);
    constraint.applyHardConstraint(edgeContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(edgeContext);

    // Vérifier que les jours de weekend sont correctement traités
    verifyAssignmentWorkingDayConsistency(solver, edgeContext, edgeCases);
  }

  /** Méthode utilitaire pour vérifier la cohérence assignations → workingDays */
  private void verifyAssignmentWorkingDayConsistency(
      CpSolver solver, SchedulingContext context, List<Shift> shifts) {

    for (int e = 0; e < context.getEmployeeCount(); e++) {
      boolean[] shouldWorkDays = new boolean[7]; // Un par jour de la semaine

      // Déterminer quels jours l'employé devrait travailler basé sur ses assignations
      for (int s = 0; s < shifts.size(); s++) {
        if (solver.value(context.getAssignments()[e][s]) == 1) {
          Shift shift = shifts.get(s);
          int dayOfWeek = shift.day().getDayInWeek(); // 0-6
          shouldWorkDays[dayOfWeek] = true;
        }
      }

      // Vérifier que workingDays correspond
      for (int d = 0; d < 7; d++) {
        boolean actualWorkingDay = solver.value(context.getWorkingDays()[e][0][d]) == 1;
        if (shouldWorkDays[d]) {
          assertTrue(
              actualWorkingDay,
              String.format(
                  "L'employé %d devrait travailler le jour %d selon ses assignations", e, d));
        }
        // Note: actualWorkingDay peut être true même si shouldWorkDays[d] est false
        // car la contrainte est workingDay >= assignment, pas d'égalité stricte
      }
    }
  }
}
