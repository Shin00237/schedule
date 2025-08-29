package com.cricri.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cricri.constraints.enums.ConstraintPriority;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.service.SchedulingContext;
import com.cricri.testutils.ConstraintTestBase;
import com.cricri.testutils.SolverAssertions;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.sat.CpSolver;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires pour AssignmentHoursConstraint.
 *
 * <p>Cette contrainte assure la cohérence entre assignations et heures réelles : - Si employé non
 * assigné → heures = 0 - Si employé assigné → heures >= minimum ET heures <= maximum du shift
 */
class AssignmentHoursConstraintTest extends ConstraintTestBase {

  private AssignmentHoursConstraint constraint;
  private final int minHoursPerShift = 5 * 60; // 5h en minutes

  @Override
  protected void setupSpecific() {
    constraint = new AssignmentHoursConstraint(minHoursPerShift);
  }

  @Test
  void constraintPropertiesTest() {
    testConstraintProperties(constraint);

    assertEquals(
        "AssignmentHours(min=" + (minHoursPerShift / 60.0) + "h, HARD)", constraint.getName());
    assertEquals(ConstraintPriority.CONSISTENCY, constraint.getPriority());
  }

  @Test
  void basicAssignmentHoursConstraintTest() {
    // Ajouter une contrainte de couverture minimum pour avoir des assignations
    new MinimumCoverageConstraint().applyHardConstraint(context);

    // Appliquer la contrainte testée
    constraint.applyHardConstraint(context);

    // Résoudre et vérifier qu'une solution existe
    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // Vérifier la cohérence assignation-heures
    SolverAssertions.assertAssignmentHoursConsistency(
        solver, context.getAssignments(), context.getActualHours(), shifts, minHoursPerShift);
  }

  @Test
  void nonAssignedEmployeeHasZeroHoursTest() {
    // Créer un scénario avec plus d'employés que nécessaire
    List<Employee> manyEmployees = TestDataFactory.createEmployees(5);
    List<Shift> fewShifts = shifts.subList(0, 2); // Seulement 2 shifts
    SchedulingContext testContext = TestDataFactory.createContext(manyEmployees, fewShifts);

    // Appliquer les contraintes
    new MinimumCoverageConstraint().applyHardConstraint(testContext);
    constraint.applyHardConstraint(testContext);

    // Résoudre
    CpSolver solver = SolverAssertions.solveAndAssertSolution(testContext);

    // Vérifier qu'au moins un employé n'est assigné à aucun shift
    boolean foundNonAssignedEmployee = false;
    for (int e = 0; e < manyEmployees.size(); e++) {
      boolean isAssignedToAnyShift = false;
      long totalHours = 0;

      for (int s = 0; s < fewShifts.size(); s++) {
        if (solver.value(testContext.getAssignments()[e][s]) == 1) {
          isAssignedToAnyShift = true;
        }
        totalHours += solver.value(testContext.getActualHours()[e][s]);
      }

      if (!isAssignedToAnyShift) {
        foundNonAssignedEmployee = true;
        assertEquals(
            0,
            totalHours,
            "L'employé " + manyEmployees.get(e).nom() + " non assigné devrait avoir 0h");
      }
    }

    assert foundNonAssignedEmployee
        : "Il devrait y avoir au moins un employé non assigné dans ce scénario";
  }

  @Test
  void assignedEmployeeRespectsMinimumHoursTest() {
    // Ajouter contrainte de couverture
    new MinimumCoverageConstraint().applyHardConstraint(context);
    constraint.applyHardConstraint(context);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // Vérifier que tous les employés assignés ont au moins le minimum d'heures
    for (int e = 0; e < employees.size(); e++) {
      for (int s = 0; s < shifts.size(); s++) {
        boolean isAssigned = solver.value(context.getAssignments()[e][s]) == 1;
        long actualHours = solver.value(context.getActualHours()[e][s]);

        if (isAssigned) {
          assert actualHours >= minHoursPerShift
              : String.format(
                  "L'employé %s assigné au shift %s devrait avoir au moins %dh, mais n'a que %dmin",
                  employees.get(e).nom(), shifts.get(s).id(), minHoursPerShift / 60, actualHours);
        }
      }
    }
  }

  @Test
  void assignedEmployeeRespectsMaximumHoursTest() {
    new MinimumCoverageConstraint().applyHardConstraint(context);
    constraint.applyHardConstraint(context);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // Vérifier que les heures n'excèdent pas la durée effective du shift
    for (int e = 0; e < employees.size(); e++) {
      for (int s = 0; s < shifts.size(); s++) {
        boolean isAssigned = solver.value(context.getAssignments()[e][s]) == 1;
        long actualHours = solver.value(context.getActualHours()[e][s]);
        int maxShiftHours = shifts.get(s).type().dureeEffectiveMinutes();

        if (isAssigned) {
          assert actualHours <= maxShiftHours
              : String.format(
                  "L'employé %s au shift %s ne devrait pas dépasser %dmin, mais a %dmin",
                  employees.get(e).nom(), shifts.get(s).id(), maxShiftHours, actualHours);
        }
      }
    }
  }

  @Test
  void withDifferentMinimumHoursTest() {
    // Tester avec un minimum d'heures différent
    AssignmentHoursConstraint strictConstraint =
        new AssignmentHoursConstraint(7 * 60); // 7h minimum

    new MinimumCoverageConstraint().applyHardConstraint(context);
    strictConstraint.applyHardConstraint(context);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // Vérifier que le nouveau minimum est respecté
    for (int e = 0; e < employees.size(); e++) {
      for (int s = 0; s < shifts.size(); s++) {
        boolean isAssigned = solver.value(context.getAssignments()[e][s]) == 1;
        long actualHours = solver.value(context.getActualHours()[e][s]);

        if (isAssigned) {
          assert actualHours >= 7 * 60
              : String.format(
                  "Avec contrainte stricte, l'employé %s devrait avoir au moins 7h, mais n'a que %dmin",
                  employees.get(e).nom(), actualHours);
        }
      }
    }
  }

  @Test
  void withZeroMinimumHoursTest() {
    // Tester avec minimum = 0 (permet des assignations partielles)
    AssignmentHoursConstraint flexibleConstraint = new AssignmentHoursConstraint(0);

    new MinimumCoverageConstraint().applyHardConstraint(context);
    flexibleConstraint.applyHardConstraint(context);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // Vérifier que les employés assignés peuvent avoir 0h (cas limite)
    boolean foundAssignedWithZeroHours = false;
    for (int e = 0; e < employees.size(); e++) {
      for (int s = 0; s < shifts.size(); s++) {
        boolean isAssigned = solver.value(context.getAssignments()[e][s]) == 1;
        long actualHours = solver.value(context.getActualHours()[e][s]);

        if (isAssigned && actualHours == 0) {
          foundAssignedWithZeroHours = true;
        }

        // Les heures doivent toujours être >= 0
        assert actualHours >= 0 : "Les heures ne peuvent pas être négatives";
      }
    }

    // Note: Avec minimum=0, OR-Tools peut décider d'assigner avec 0h
    // Ce n'est pas forcément un problème, c'est juste plus flexible
  }

  @Test
  void constraintWithVariousShiftTypesTest() {
    // Créer des shifts de différents types
    List<Shift> mixedShifts = TestDataFactory.createMixedShifts(week1);
    SchedulingContext mixedContext = TestDataFactory.createContext(employees, mixedShifts);

    new MinimumCoverageConstraint().applyHardConstraint(mixedContext);
    constraint.applyHardConstraint(mixedContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(mixedContext);

    // Vérifier que chaque type de shift respecte ses propres limites
    for (int e = 0; e < employees.size(); e++) {
      for (int s = 0; s < mixedShifts.size(); s++) {
        boolean isAssigned = solver.value(mixedContext.getAssignments()[e][s]) == 1;
        long actualHours = solver.value(mixedContext.getActualHours()[e][s]);
        Shift shift = mixedShifts.get(s);

        if (isAssigned) {
          assert actualHours >= minHoursPerShift
              : String.format(
                  "Shift %s: heures insuffisantes %dmin < %dmin",
                  shift.id(), actualHours, minHoursPerShift);
          assert actualHours <= shift.type().dureeEffectiveMinutes()
              : String.format(
                  "Shift %s: heures excessives %dmin > %dmin",
                  shift.id(), actualHours, shift.type().dureeEffectiveMinutes());
        } else {
          assertEquals(
              0,
              actualHours,
              String.format("Shift %s: employé non assigné mais heures > 0", shift.id()));
        }
      }
    }
  }
}
