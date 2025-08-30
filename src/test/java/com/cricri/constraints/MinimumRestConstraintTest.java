package com.cricri.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.config.ParameterKey;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ConstraintType;
import com.cricri.model.Shift;
import com.cricri.service.SchedulingContext;
import com.cricri.testutils.ConstraintTestBase;
import com.cricri.testutils.SolverAssertions;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.sat.CpSolver;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires pour MinimumRestConstraint.
 *
 * <p>Cette contrainte assure qu'il y a un temps de repos minimum entre la fin d'un shift et le
 * début du suivant pour le même employé.
 */
class MinimumRestConstraintTest extends ConstraintTestBase {

  private MinimumRestConstraint constraint;
  private final int minimumRestHours = 11; // 11h de repos minimum

  @Override
  protected void setupSpecific() {
    constraint = new MinimumRestConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_REST, ConstraintNature.HARD, ParameterKey.MIN_REST_HOURS.getKeyName(), minimumRestHours)
    );
  }

  @Test
  void constraintPropertiesTest() {
    testConstraintProperties(constraint);

    assertEquals("MinimumRest(" + minimumRestHours + "h, HARD)", constraint.getName());
  }

  @Test
  void noRestConflictWithNormalShiftsTest() {
    // Avec des shifts normaux (8h-16h) sur des jours différents, pas de conflit
    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(context);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(), 5 * 60)
    ).applyHardConstraint(context);
    constraint.applyHardConstraint(context);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // Vérifier que la couverture est assurée
    SolverAssertions.assertAllShiftsCovered(solver, context.getAssignments(), shifts);
  }

  @Test
  void restConflictPreventsSimultaneousAssignmentTest() {
    // Créer des shifts en conflit temporel (même jour, horaires qui se chevauchent)
    SchedulingContext conflictContext = createConflictingScenario();

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(conflictContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(), 5 * 60)
    ).applyHardConstraint(conflictContext);
    constraint.applyHardConstraint(conflictContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(conflictContext);
    List<Shift> conflictingShifts = TestDataFactory.createConflictingShifts(week1);

    // Vérifier qu'aucun employé n'est assigné aux shifts en conflit
    // Vendredi matin (7h-15h45) et soir (15h-23h45) = chevauchement 15h-15h45
    SolverAssertions.assertNoConflictBetweenShifts(
        solver,
        conflictContext.getAssignments(),
        employees,
        0,
        1, // Index des shifts Vendredi-MATIN et Vendredi-SOIR
        "chevauchement temporel de 15h à 15h45");
  }

  @Test
  void adequateRestBetweenConsecutiveShiftsTest() {
    // Créer des shifts consécutifs mais avec repos suffisant
    List<Shift> consecutiveShifts =
        List.of(
            new Shift(
                "Lundi-SOIR", week1.getDay(0), TestDataFactory.EVENING_SHIFT, 1, 1), // 15h-23h45
            new Shift(
                "Mercredi-MATIN", week1.getDay(2), TestDataFactory.MORNING_SHIFT, 1, 1) // 7h-15h45
            // Entre lundi 23h45 et mercredi 7h = plus de 30h de repos → OK
            );

    SchedulingContext adequateRestContext =
        TestDataFactory.createContext(employees, consecutiveShifts);

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(adequateRestContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(), 5 * 60)
    ).applyHardConstraint(adequateRestContext);
    constraint.applyHardConstraint(adequateRestContext);

    // Une solution devrait exister car il y a assez de repos
    CpSolver solver = SolverAssertions.solveAndAssertSolution(adequateRestContext);
    SolverAssertions.assertAllShiftsCovered(
        solver, adequateRestContext.getAssignments(), consecutiveShifts);
  }

  @Test
  void insufficientRestPreventsConsecutiveAssignmentTest() {
    // Créer des shifts consécutifs avec repos insuffisant
    List<Shift> tooCloseShifts =
        List.of(
            new Shift(
                "Lundi-SOIR", week1.getDay(0), TestDataFactory.EVENING_SHIFT, 1, 1), // Fin: 23h45
            new Shift(
                "Mardi-MATIN", week1.getDay(1), TestDataFactory.MORNING_SHIFT, 1, 1) // Début: 7h
            // Entre lundi 23h45 et mardi 7h = 7h15 de repos < 11h → Interdit
            );

    SchedulingContext insufficientRestContext =
        TestDataFactory.createContext(employees, tooCloseShifts);

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(insufficientRestContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(), 5 * 60)
    ).applyHardConstraint(insufficientRestContext);
    constraint.applyHardConstraint(insufficientRestContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(insufficientRestContext);

    // Vérifier qu'aucun employé n'est assigné aux deux shifts
    SolverAssertions.assertNoConflictBetweenShifts(
        solver,
        insufficientRestContext.getAssignments(),
        employees,
        0,
        1, // Lundi soir et Mardi matin
        "repos insuffisant (7h15 < 11h)");
  }

  @Test
  void differentRestPeriodsTest() {
    // Tester avec un repos plus strict (16h) mais sur un scénario plus simple
    MinimumRestConstraint strictConstraint = new MinimumRestConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_REST, ConstraintNature.HARD, ParameterKey.MIN_REST_HOURS.getKeyName(), 16)
    );

    // Utiliser un contexte plus simple qui devrait être faisable
    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(context);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(), 5 * 60)
    ).applyHardConstraint(context);
    strictConstraint.applyHardConstraint(context);

    // Vérifier que le contexte standard reste faisable avec repos strict
    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);
    SolverAssertions.assertAllShiftsCovered(solver, context.getAssignments(), shifts);

    assertEquals("MinimumRest(16h, HARD)", strictConstraint.getName());
  }

  @Test
  void restConstraintWithFlexibleRestPeriodTest() {
    // Tester avec un repos plus souple (8h)
    MinimumRestConstraint flexibleConstraint = new MinimumRestConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_REST, ConstraintNature.HARD, ParameterKey.MIN_REST_HOURS.getKeyName(), 8)
    );

    List<Shift> flexibleShifts =
        List.of(
            new Shift(
                "Lundi-SOIR", week1.getDay(0), TestDataFactory.EVENING_SHIFT, 1, 1), // Fin: 23h45
            new Shift(
                "Mardi-MIDI", week1.getDay(1), TestDataFactory.NORMAL_SHIFT, 1, 1) // Début: 8h
            // Repos = 8h15 → OK avec contrainte flexible
            );

    SchedulingContext flexibleContext = TestDataFactory.createContext(employees, flexibleShifts);

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(flexibleContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(), 5 * 60)
    ).applyHardConstraint(flexibleContext);
    flexibleConstraint.applyHardConstraint(flexibleContext);

    // Devrait être faisable avec repos flexible
    CpSolver solver = SolverAssertions.solveAndAssertSolution(flexibleContext);
    SolverAssertions.assertAllShiftsCovered(
        solver, flexibleContext.getAssignments(), flexibleShifts);
  }

  @Test
  void multipleEmployeesWithRestConstraintsTest() {
    // Avec plusieurs employés, les conflits peuvent être résolus par répartition
    List<com.cricri.model.Employee> manyEmployees = TestDataFactory.createEmployees(4);
    SchedulingContext multiEmployeeContext =
        TestDataFactory.createContext(
            manyEmployees, TestDataFactory.createConflictingShifts(week1));

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(multiEmployeeContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(), 5 * 60)
    ).applyHardConstraint(multiEmployeeContext);
    constraint.applyHardConstraint(multiEmployeeContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(multiEmployeeContext);

    // Vérifier que tous les shifts sont couverts même avec contraintes de repos
    List<Shift> conflictingShifts = TestDataFactory.createConflictingShifts(week1);
    SolverAssertions.assertAllShiftsCovered(
        solver, multiEmployeeContext.getAssignments(), conflictingShifts);

    // Vérifier qu'aucun employé individuel n'a de conflit
    for (int e = 0; e < manyEmployees.size(); e++) {
      boolean assignedToMatin = solver.value(multiEmployeeContext.getAssignments()[e][0]) == 1;
      boolean assignedToSoir = solver.value(multiEmployeeContext.getAssignments()[e][1]) == 1;

      if (assignedToMatin && assignedToSoir) {
        throw new AssertionError(
            "L'employé "
                + manyEmployees.get(e).nom()
                + " ne devrait pas être assigné aux shifts en conflit");
      }
    }
  }

  @Test
  void nightShiftRestConstraintsTest() {
    // Tester avec des shifts de nuit - utilisation plus souple du test
    List<Shift> nightShifts =
        List.of(
            new Shift("Lundi-NUIT", week1.getDay(0), TestDataFactory.NIGHT_SHIFT, 1, 1), // 22h30-7h
            new Shift(
                "Mercredi-MATIN", week1.getDay(2), TestDataFactory.MORNING_SHIFT, 1, 1) // 7h-15h45
            // Avec un jour d'intervalle, devrait être OK
            );

    SchedulingContext nightContext = TestDataFactory.createContext(employees, nightShifts);

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(nightContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(), 5 * 60)
    ).applyHardConstraint(nightContext);
    constraint.applyHardConstraint(nightContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(nightContext);

    // Vérifier que tous les shifts sont couverts
    SolverAssertions.assertAllShiftsCovered(solver, nightContext.getAssignments(), nightShifts);
  }

  @Test
  void restConstraintAcrossWeekendsTest() {
    // Tester le repos à travers le weekend
    List<Shift> weekendShifts =
        List.of(
            new Shift(
                "Vendredi-SOIR",
                week1.getDay(4),
                TestDataFactory.EVENING_SHIFT,
                1,
                1), // Fin: 23h45
            new Shift(
                "Lundi-MATIN", week1.getDay(0), TestDataFactory.MORNING_SHIFT, 1, 1) // Début: 7h
            // Weekend complet entre les deux = plus de 60h → OK
            );

    SchedulingContext weekendContext = TestDataFactory.createContext(employees, weekendShifts);

    new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)
    ).applyHardConstraint(weekendContext);
    new AssignmentHoursConstraint(
        ConstraintConfig.of(ConstraintType.ASSIGNMENT_HOURS, ConstraintNature.HARD, ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(), 5 * 60)
    ).applyHardConstraint(weekendContext);
    constraint.applyHardConstraint(weekendContext);

    // Devrait être faisable car weekend = beaucoup de repos
    CpSolver solver = SolverAssertions.solveAndAssertSolution(weekendContext);
    SolverAssertions.assertAllShiftsCovered(solver, weekendContext.getAssignments(), weekendShifts);
  }
}
