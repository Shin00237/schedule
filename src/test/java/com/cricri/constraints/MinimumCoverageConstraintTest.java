package com.cricri.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
 * Tests unitaires pour MinimumCoverageConstraint.
 *
 * <p>Cette contrainte assure que chaque shift a le nombre minimum et maximum d'employés requis
 * selon sa configuration.
 */
class MinimumCoverageConstraintTest extends ConstraintTestBase {

  private MinimumCoverageConstraint constraint;

  @Override
  protected void setupSpecific() {
    constraint = new MinimumCoverageConstraint();
  }

  @Test
  void constraintPropertiesTest() {
    testConstraintProperties(constraint);

    assertEquals("MinimumCoverage(HARD)", constraint.getName());
  }

  @Test
  void basicMinimumCoverageConstraintTest() {
    testConstraintWithStandardData(constraint);

    // Vérifier que la couverture est respectée après résolution
    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);
    SolverAssertions.assertAllShiftsCovered(solver, context.getAssignments(), shifts);
  }

  @Test
  void coverageWithRangeRequirementsTest() {
    // Tester avec des shifts ayant des plages min-max
    List<Shift> rangeShifts =
        List.of(
            new Shift(
                "Flexible-1", week1.getDay(0), TestDataFactory.NORMAL_SHIFT, 1, 2), // 1-2 employés
            new Shift(
                "Flexible-2", week1.getDay(1), TestDataFactory.NORMAL_SHIFT, 1, 3) // 1-3 employés
            );

    SchedulingContext rangeContext = TestDataFactory.createContext(employees, rangeShifts);
    constraint.applyHardConstraint(rangeContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(rangeContext);
    SolverAssertions.assertAllShiftsCovered(solver, rangeContext.getAssignments(), rangeShifts);
  }

  @Test
  void infeasibleScenarioTest() {
    // Scénario impossible : 1 employé pour un shift qui en demande 2
    List<Employee> oneEmployee = TestDataFactory.createEmployees(1);
    List<Shift> impossibleShifts =
        List.of(new Shift("Impossible", week1.getDay(0), TestDataFactory.NORMAL_SHIFT, 2, 2));

    SchedulingContext impossibleContext =
        TestDataFactory.createContext(oneEmployee, impossibleShifts);
    testConstraintMakesScenarioInfeasible(
        constraint, impossibleContext, "1 employé pour un shift qui demande 2 employés minimum");
  }

  @Test
  void multipleShiftsWithDifferentRequirementsTest() {
    // Tester avec des shifts ayant des besoins variés
    List<Employee> fiveEmployees = TestDataFactory.createEmployees(5);
    List<Shift> variedShifts =
        List.of(
            new Shift("Light-1", week1.getDay(0), TestDataFactory.NORMAL_SHIFT, 1, 1),
            new Shift("Heavy-1", week1.getDay(1), TestDataFactory.NORMAL_SHIFT, 3, 3),
            new Shift("Flexible-1", week1.getDay(2), TestDataFactory.NORMAL_SHIFT, 1, 2));

    SchedulingContext variedContext = TestDataFactory.createContext(fiveEmployees, variedShifts);
    constraint.applyHardConstraint(variedContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(variedContext);
    SolverAssertions.assertAllShiftsCovered(solver, variedContext.getAssignments(), variedShifts);
  }

  @Test
  void constraintWithZeroMaxEmployeesTest() {
    // Tester avec maxEmployees = 0 (shift optionnel)
    List<Shift> optionalShifts =
        List.of(
            new Shift("Optional", week1.getDay(0), TestDataFactory.NORMAL_SHIFT, 0, 0),
            new Shift("Required", week1.getDay(1), TestDataFactory.NORMAL_SHIFT, 1, 1));

    SchedulingContext optionalContext = TestDataFactory.createContext(employees, optionalShifts);
    constraint.applyHardConstraint(optionalContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(optionalContext);

    // Vérifier que le shift optionnel n'a aucun employé assigné
    for (int e = 0; e < employees.size(); e++) {
      assertEquals(
          0,
          solver.value(optionalContext.getAssignments()[e][0]),
          "Shift optionnel ne devrait avoir aucun employé assigné");
    }

    // Vérifier que le shift requis a bien un employé
    int requiredAssignments = 0;
    for (int e = 0; e < employees.size(); e++) {
      if (solver.value(optionalContext.getAssignments()[e][1]) == 1) {
        requiredAssignments++;
      }
    }
    assertEquals(1, requiredAssignments, "Shift requis doit avoir exactement 1 employé");
  }
}
