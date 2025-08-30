package com.cricri.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.config.ParameterKey;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ConstraintType;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.model.ShiftType;
import com.cricri.model.Week;
import com.cricri.service.SchedulingContext;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaxHoursPerWeekConstraintTest {

  private List<Employee> employees;
  private List<Shift> shifts;
  private SchedulingContext context;
  private MaxHoursPerWeekConstraint constraint;
  private final int maxHoursPerWeek = 20 * 60; // 20h pour les tests

  @BeforeEach
  void setUp() {
    Loader.loadNativeLibraries();

    // Employés de test
    employees = Arrays.asList(new Employee("E1", "Alice"), new Employee("E2", "Bob"));

    // Shifts sur 2 semaines pour tester la limite par semaine
    ShiftType normalShift =
        new ShiftType("NORMAL", 480, 960, 480, 45); // 8h effectives (480-45=435min)
    Week week1 = Week.create(0);
    Week week2 = Week.create(1);

    shifts =
        Arrays.asList(
            // Semaine 1 : 3 shifts = 435*3 = 1305min = 21.75h > 20h limite
            new Shift("S1W1-1", week1.getDay(0), normalShift, 1, 1),
            new Shift("S1W1-2", week1.getDay(1), normalShift, 1, 1),
            new Shift("S1W1-3", week1.getDay(2), normalShift, 1, 1),
            // Semaine 2 : 2 shifts = 435*2 = 870min = 14.5h < 20h limite
            new Shift("S2W2-1", week2.getDay(0), normalShift, 1, 1),
            new Shift("S2W2-2", week2.getDay(1), normalShift, 1, 1));

    // Contexte de test
    context = new SchedulingContext(employees, shifts, new HashMap<>());
    constraint = new MaxHoursPerWeekConstraint(
        ConstraintConfig.of(ConstraintType.MAX_HOURS_PER_WEEK, ConstraintNature.HARD, ParameterKey.MAX_HOURS_PER_WEEK.getKeyName(), maxHoursPerWeek)
    );
  }

  @Test
  void applyConstraintTest() {
    // Appliquer les contraintes nécessaires
    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(context);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(), 5 * 60)
    ).applyHardConstraint(context);
    constraint.applyHardConstraint(context);

    // Résoudre
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(context.getModel());

    // Vérifier qu'une solution existe
    assertTrue(
        status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE,
        "Une solution doit exister");

    // Vérifier les limites d'heures par semaine
    for (int e = 0; e < employees.size(); e++) {
      for (int w = 0; w < 2; w++) { // 2 semaines
        long hoursInWeek = solver.value(context.getHoursPerEmployeePerWeek()[e][w]);
        assertTrue(
            hoursInWeek <= maxHoursPerWeek,
            "L'employé "
                + employees.get(e).nom()
                + " semaine "
                + (w + 1)
                + " dépasse la limite: "
                + (hoursInWeek / 60.0)
                + "h > "
                + (maxHoursPerWeek / 60.0)
                + "h");
      }
    }

    // Vérifier que tous les shifts sont couverts
    for (int s = 0; s < shifts.size(); s++) {
      boolean isCovered = false;
      for (int e = 0; e < employees.size(); e++) {
        if (solver.value(context.getAssignments()[e][s]) == 1) {
          isCovered = true;
          break;
        }
      }
      assertTrue(isCovered, "Le shift " + shifts.get(s).id() + " doit être couvert");
    }
  }

  @Test
  void constraintNameTest() {
    assertEquals("MaxHoursPerWeek(" + (maxHoursPerWeek / 60.0) + "h, HARD)", constraint.getName());
  }

  @Test
  void hoursCalculationTest() {
    // Appliquer les contraintes
    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(context);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(), 5 * 60)
    ).applyHardConstraint(context);
    constraint.applyHardConstraint(context);

    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(context.getModel());

    assertTrue(status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE);

    // Vérifier que les heures calculées correspondent aux assignations
    for (int e = 0; e < employees.size(); e++) {
      for (int w = 0; w < 2; w++) {
        long expectedHours = 0;

        // Calculer les heures attendues pour cette semaine
        for (int s = 0; s < shifts.size(); s++) {
          Shift shift = shifts.get(s);
          if (shift.day().getWeekNumber() == w
              && solver.value(context.getAssignments()[e][s]) == 1) {
            expectedHours += solver.value(context.getActualHours()[e][s]);
          }
        }

        long actualHours = solver.value(context.getHoursPerEmployeePerWeek()[e][w]);
        assertEquals(
            expectedHours,
            actualHours,
            "Les heures calculées ne correspondent pas aux assignations");
      }
    }
  }
}
