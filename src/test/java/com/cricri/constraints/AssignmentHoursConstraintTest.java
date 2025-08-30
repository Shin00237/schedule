package com.cricri.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.config.ParameterKey;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ConstraintType;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.model.ShiftType;
import com.cricri.service.SchedulingContext;
import com.cricri.testutils.ConstraintTestBase;
import com.cricri.testutils.SolverAssertions;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.sat.CpSolver;

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
    constraint = new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(), minHoursPerShift)
    );
  }

  @Test
  void constraintPropertiesTest() {
    constraintPropertiesTest(constraint);

    assertEquals(
        "AssignmentHours(min=" + (minHoursPerShift / 60.0) + "h, HARD)", constraint.getName());
  }

  @Test
  void basicAssignmentHoursConstraintTest() {
    // Ajouter une contrainte de couverture minimum pour avoir des assignations
    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(context);

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
    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(testContext);
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
    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(context);
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
    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(context);
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
        new AssignmentHoursConstraint(
            ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(), 7 * 60)
        );

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(context);
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
    AssignmentHoursConstraint flexibleConstraint = new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(), 0)
    );

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(context);
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

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(mixedContext);
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

  @Test
  void constraintWithZeroBreakShiftsTest() {
    // Créer un type de shift sans pause (Duration.ZERO)
    ShiftType shiftSansPause = new ShiftType(
        "SANS_PAUSE",
        LocalTime.of(9, 0),
        LocalTime.of(17, 0),
        Duration.ZERO
    );

    List<Shift> shiftsZeroBreak = Arrays.asList(
        new Shift("Lundi-SANS_PAUSE", week1.getDay(0), shiftSansPause, 1, 2),
        new Shift("Mardi-SANS_PAUSE", week1.getDay(1), shiftSansPause, 1, 2)
    );

    SchedulingContext zeroBreakContext = TestDataFactory.createContext(employees, shiftsZeroBreak);
 AssignmentHoursConstraint zeroBreakConstraint = new AssignmentHoursConstraint(
            ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD,
                              ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(), 480)
        );
    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(zeroBreakContext);
    zeroBreakConstraint.applyHardConstraint(zeroBreakContext);


    CpSolver solver = SolverAssertions.solveAndAssertSolution(zeroBreakContext);

    // Vérifier que la solution est faisable
    SolverAssertions.assertAllShiftsCovered(solver, zeroBreakContext.getAssignments(), shiftsZeroBreak);

    // Vérifier que les employés assignés sont présents l'entièreté du shift (actualHours)
    for (int e = 0; e < employees.size(); e++) {
      for (int s = 0; s < shiftsZeroBreak.size(); s++) {
        boolean isAssigned = solver.value(zeroBreakContext.getAssignments()[e][s]) == 1;
        long actualHours = solver.value(zeroBreakContext.getActualHours()[e][s]);
        Shift shift = shiftsZeroBreak.get(s);
        int dureeEffectiveMinutes = shift.type().dureeEffectiveMinutes();

        if (isAssigned) {
          // Avec pause de 0 minutes, durée effective = durée totale (8h = 480min)
          assertEquals(
              dureeEffectiveMinutes,
              actualHours,
              String.format(
                  "Employé %s assigné au shift %s sans pause devrait être présent %dmin, mais actualHours=%dmin",
                  employees.get(e).nom(), shift.id(), dureeEffectiveMinutes, actualHours));

          // Vérifier également que la durée effective est bien de 8h (480 minutes)
          assertEquals(480, dureeEffectiveMinutes,
              "Shift sans pause de 9h-17h devrait avoir une durée effective de 480min");
        } else {
          assertEquals(
              0,
              actualHours,
              String.format("Employé %s non assigné au shift %s devrait avoir 0h",
                  employees.get(e).nom(), shift.id()));
        }
      }
    }
  }
}
