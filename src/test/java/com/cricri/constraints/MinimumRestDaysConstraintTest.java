package com.cricri.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ConstraintType;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.service.ObjectiveCollector;
import com.cricri.service.SchedulingContext;
import com.cricri.testutils.ConstraintTestBase;
import com.cricri.testutils.SolverAssertions;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.sat.CpSolver;

/**
 * Tests unitaires pour MinimumRestDaysConstraint.
 *
 * <p>Cette contrainte assure qu'un employé a un nombre minimum de jours de repos par semaine (ex:
 * au moins 1 jour de repos = max 6 jours travaillés).
 */
class MinimumRestDaysConstraintTest extends ConstraintTestBase {

  private MinimumRestDaysConstraint constraint;
  private final int minimumRestDays = 1; // 1 jour de repos minimum par semaine

  @Override
  protected void setupSpecific() {
    constraint = new MinimumRestDaysConstraint(minimumRestDays);
  }

  @Test
  void constraintPropertiesTest() {
    testConstraintProperties(constraint);

    assertEquals(
        "MinimumRestDays(" + minimumRestDays + " rest days min, SOFT)", constraint.getName());
  }

  @Test
  void basicRestDaysConstraintHardTest() {
    // Test HARD : Avec des shifts normaux, la contrainte devrait être satisfaite
    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(context);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, "minHoursPerShift", 5 * 60)
    ).applyHardConstraint(context);
    // WorkingDaysConstraint supprimée - logique maintenant dans SchedulingContext
    constraint.applyHardConstraint(context);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // Vérifier que chaque employé respecte le maximum de jours travaillés
    SolverAssertions.assertWorkingDaysRespected(
        solver, context.getWorkingDaysPerWeek(), employees, 6); // Max 6 jours (1 repos)
  }

  @Test
  void basicRestDaysConstraintSoftTest() {
    // Test SOFT : Même avec des shifts normaux, devrait permettre une solution
    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(context);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, "minHoursPerShift", 5 * 60)
    ).applyHardConstraint(context);
    // WorkingDaysConstraint supprimée - logique maintenant dans SchedulingContext
    ObjectiveCollector sharedCollector = new ObjectiveCollector();

    constraint.applySoftConstraint(context, sharedCollector);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // En mode SOFT, la contrainte peut être violée mais la solution existe toujours
    SolverAssertions.assertAllShiftsCovered(solver, context.getAssignments(), shifts);
  }

  @Test
  void impossibleScenarioWithOneEmployeeAndSevenShiftsHardTest() {
    // Un employé, 7 shifts (un par jour), 1 jour de repos minimum → Impossible en HARD
    List<Employee> oneEmployee = TestDataFactory.createEmployees(1);
    List<Shift> allWeekShifts = TestDataFactory.createWeekShifts(week1, 1, 1); // 7 shifts
    SchedulingContext impossibleContext = TestDataFactory.createContext(oneEmployee, allWeekShifts);

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(impossibleContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, "minHoursPerShift", 5 * 60)
    ).applyHardConstraint(impossibleContext);
    // WorkingDaysConstraint supprimée - logique maintenant dans SchedulingContext

    // Le problème devrait être infaisable en mode HARD
    testConstraintMakesScenarioInfeasible(
        constraint,
        impossibleContext,
        "1 employé ne peut pas couvrir 7 shifts avec 1 jour de repos obligatoire");
  }

  @Test
  void feasibleScenarioWithTwoEmployeesTest() {
    // Deux employés peuvent couvrir 7 shifts avec contrainte de repos
    List<Employee> twoEmployees = TestDataFactory.createEmployees(2);
    List<Shift> allWeekShifts = TestDataFactory.createWeekShifts(week1, 1, 1); // 7 shifts
    SchedulingContext feasibleContext = TestDataFactory.createContext(twoEmployees, allWeekShifts);

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(feasibleContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, "minHoursPerShift", 5 * 60)
    ).applyHardConstraint(feasibleContext);
    // WorkingDaysConstraint supprimée - logique maintenant dans SchedulingContext
    constraint.applyHardConstraint(feasibleContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(feasibleContext);

    // Vérifier que tous les shifts sont couverts
    SolverAssertions.assertAllShiftsCovered(
        solver, feasibleContext.getAssignments(), allWeekShifts);

    // Vérifier que chaque employé a au moins 1 jour de repos
    SolverAssertions.assertWorkingDaysRespected(
        solver, feasibleContext.getWorkingDaysPerWeek(), twoEmployees, 6);
  }

  @Test
  void stricterRestDaysConstraintTest() {
    // Tester avec 2 jours de repos minimum (max 5 jours travaillés)
    MinimumRestDaysConstraint strictConstraint = new MinimumRestDaysConstraint(2);

    // Même avec 2 employés et 7 shifts, ce sera plus difficile
    List<Employee> twoEmployees = TestDataFactory.createEmployees(2);
    List<Shift> allWeekShifts = TestDataFactory.createWeekShifts(week1, 1, 1);
    SchedulingContext strictContext = TestDataFactory.createContext(twoEmployees, allWeekShifts);

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(strictContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, "minHoursPerShift", 5 * 60)
    ).applyHardConstraint(strictContext);
    // WorkingDaysConstraint supprimée - logique maintenant dans SchedulingContext
    strictConstraint.applyHardConstraint(strictContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(strictContext);

    // Vérifier le maximum de 5 jours travaillés par employé
    SolverAssertions.assertWorkingDaysRespected(
        solver, strictContext.getWorkingDaysPerWeek(), twoEmployees, 5);

    assertEquals("MinimumRestDays(2 rest days min, SOFT)", strictConstraint.getName());
  }

  @Test
  void flexibleRestDaysConstraintTest() {
    // Tester avec 0 jour de repos minimum (7 jours travaillés possibles)
    MinimumRestDaysConstraint flexibleConstraint = new MinimumRestDaysConstraint(0);

    List<Employee> oneEmployee = TestDataFactory.createEmployees(1);
    List<Shift> allWeekShifts = TestDataFactory.createWeekShifts(week1, 1, 1);
    SchedulingContext flexibleContext = TestDataFactory.createContext(oneEmployee, allWeekShifts);

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(flexibleContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, "minHoursPerShift", 5 * 60)
    ).applyHardConstraint(flexibleContext);
    // WorkingDaysConstraint supprimée - logique maintenant dans SchedulingContext
    flexibleConstraint.applyHardConstraint(flexibleContext);

    // Maintenant, un employé devrait pouvoir couvrir tous les shifts
    CpSolver solver = SolverAssertions.solveAndAssertSolution(flexibleContext);
    SolverAssertions.assertAllShiftsCovered(
        solver, flexibleContext.getAssignments(), allWeekShifts);

    // Vérifier qu'il peut travailler jusqu'à 7 jours
    SolverAssertions.assertWorkingDaysRespected(
        solver, flexibleContext.getWorkingDaysPerWeek(), oneEmployee, 7);
  }

  @Test
  void multiWeekRestDaysConstraintTest() {
    // Tester sur plusieurs semaines
    SchedulingContext twoWeekContext = createTwoWeekScenario();

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(twoWeekContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, "minHoursPerShift", 5 * 60)
    ).applyHardConstraint(twoWeekContext);
    // WorkingDaysConstraint supprimée - logique maintenant dans SchedulingContext
    constraint.applyHardConstraint(twoWeekContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(twoWeekContext);

    // Vérifier que chaque employé respecte la contrainte pour chaque semaine
    for (int e = 0; e < employees.size(); e++) {
      for (int w = 0; w < 2; w++) { // 2 semaines
        long workingDays = solver.value(twoWeekContext.getWorkingDaysPerWeek()[e][w]);
        assertTrue(
            workingDays <= 6,
            String.format(
                "L'employé %s semaine %d travaille %d jours (max 6)",
                employees.get(e).nom(), w + 1, workingDays));
      }
    }
  }

  @Test
  void restDaysWithMixedShiftTypesTest() {
    // Tester avec différents types de shifts
    List<Shift> mixedShifts = TestDataFactory.createMixedShifts(week1);
    SchedulingContext mixedContext = TestDataFactory.createContext(employees, mixedShifts);

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(mixedContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, "minHoursPerShift", 5 * 60)
    ).applyHardConstraint(mixedContext);
    // WorkingDaysConstraint supprimée - logique maintenant dans SchedulingContext
    constraint.applyHardConstraint(mixedContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(mixedContext);

    // Même avec des shifts variés, la contrainte de repos doit être respectée
    SolverAssertions.assertWorkingDaysRespected(
        solver, mixedContext.getWorkingDaysPerWeek(), employees, 6);
    SolverAssertions.assertAllShiftsCovered(solver, mixedContext.getAssignments(), mixedShifts);
  }

  @Test
  void restDaysWithMultipleShiftsPerDayTest() {
    // Tester avec plusieurs shifts le même jour
    List<Shift> multiShiftsPerDay =
        List.of(
            new Shift("Lundi-MATIN", week1.getDay(0), TestDataFactory.MORNING_SHIFT, 1, 1),
            new Shift("Lundi-SOIR", week1.getDay(0), TestDataFactory.EVENING_SHIFT, 1, 1),
            new Shift("Mardi-NORMAL", week1.getDay(1), TestDataFactory.NORMAL_SHIFT, 1, 1),
            new Shift("Mercredi-MATIN", week1.getDay(2), TestDataFactory.MORNING_SHIFT, 1, 1),
            new Shift("Mercredi-SOIR", week1.getDay(2), TestDataFactory.EVENING_SHIFT, 1, 1));

    SchedulingContext multiShiftContext =
        TestDataFactory.createContext(employees, multiShiftsPerDay);

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(multiShiftContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, "minHoursPerShift", 5 * 60)
    ).applyHardConstraint(multiShiftContext);
    // WorkingDaysConstraint supprimée - logique maintenant dans SchedulingContext
    constraint.applyHardConstraint(multiShiftContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(multiShiftContext);

    // Un employé qui travaille matin ET soir le même jour compte comme 1 jour travaillé
    SolverAssertions.assertWorkingDaysRespected(
        solver, multiShiftContext.getWorkingDaysPerWeek(), employees, 6);

    // Tous les shifts doivent être couverts
    SolverAssertions.assertAllShiftsCovered(
        solver, multiShiftContext.getAssignments(), multiShiftsPerDay);
  }

  @Test
  void restDaysCalculationAccuracyTest() {
    // Test spécifique pour vérifier le calcul des jours travaillés
    List<Employee> oneEmployee = TestDataFactory.createEmployees(1);
    List<Shift> exactlyFiveShifts =
        TestDataFactory.createWeekShifts(week1, 1, 1).subList(0, 5); // Lun-Ven
    SchedulingContext fiveShiftContext =
        TestDataFactory.createContext(oneEmployee, exactlyFiveShifts);

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(fiveShiftContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, "minHoursPerShift", 5 * 60)
    ).applyHardConstraint(fiveShiftContext);
    // WorkingDaysConstraint supprimée - logique maintenant dans SchedulingContext
    constraint.applyHardConstraint(fiveShiftContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(fiveShiftContext);

    // L'employé devrait travailler exactement 5 jours
    long workingDays = solver.value(fiveShiftContext.getWorkingDaysPerWeek()[0][0]);
    assertEquals(5, workingDays, "L'employé devrait travailler exactement 5 jours");

    // Ce qui respecte la contrainte de maximum 6 jours
    assertTrue(workingDays <= 6, "5 jours <= 6 jours maximum");
  }

  @Test
  void extremeRestDaysConstraintTest() {
    // Tester avec contrainte extrême: 6 jours de repos (max 1 jour travaillé)
    MinimumRestDaysConstraint extremeConstraint = new MinimumRestDaysConstraint(6);

    // Même avec beaucoup d'employés, très restrictif
    List<Employee> manyEmployees = TestDataFactory.createEmployees(8);
    List<Shift> fewShifts = shifts.subList(0, 3); // Seulement 3 shifts
    SchedulingContext extremeContext = TestDataFactory.createContext(manyEmployees, fewShifts);

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(extremeContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, "minHoursPerShift", 5 * 60)
    ).applyHardConstraint(extremeContext);
    // WorkingDaysConstraint supprimée - logique maintenant dans SchedulingContext
    extremeConstraint.applyHardConstraint(extremeContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(extremeContext);

    // Chaque employé ne peut travailler qu'1 jour maximum
    SolverAssertions.assertWorkingDaysRespected(
        solver, extremeContext.getWorkingDaysPerWeek(), manyEmployees, 1);

    assertEquals("MinimumRestDays(6 rest days min, SOFT)", extremeConstraint.getName());
  }
}
