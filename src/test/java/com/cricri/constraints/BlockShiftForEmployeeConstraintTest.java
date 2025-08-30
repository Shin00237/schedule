package com.cricri.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.config.ParameterKey;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires pour BlockShiftForEmployeeConstraint.
 *
 * <p>Cette contrainte empêche l'assignation d'employés spécifiques à des shifts spécifiques.
 */
class BlockShiftForEmployeeConstraintTest extends ConstraintTestBase {

  private BlockShiftForEmployeeConstraint constraint;

  @Override
  protected void setupSpecific() {
    // Créer plus d'employés pour permettre la couverture des shifts
    employees = TestDataFactory.createEmployees(4); // E1 à E4
    context = TestDataFactory.createContext(employees, shifts);

    // Cas nominal : bloquer l'employé E1 sur les shifts "Lundi-NORMAL" et "Mardi-NORMAL"
    Map<String, List<String>> blockedAssignments =
        Map.of("E1", List.of("Lundi-NORMAL", "Mardi-NORMAL"));

    constraint =
        new BlockShiftForEmployeeConstraint(
            ConstraintConfig.of(
                ConstraintType.BLOCKED_SHIFT_EMPLOYEE,
                ConstraintNature.HARD,
                ParameterKey.BLOCKED_ASSIGNMENTS.getKeyName(),
                blockedAssignments));
  }

  @Test
  void constraintPropertiesTest() {
    constraintPropertiesTest(constraint);
    assertEquals("BlockedEmployee(HARD)", constraint.getName());
  }

  @Test
  void basicBlockShiftForEmployeeConstraintTest() {
    // Appliquer la contrainte de blocage
    constraint.applyHardConstraint(context);

    // Ajouter contrainte de couverture minimum pour forcer l'assignation des autres employés
    MinimumCoverageConstraint coverage =
        new MinimumCoverageConstraint(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD));
    coverage.applyHardConstraint(context);

    // Vérifier qu'une solution existe
    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // Vérifier que les blocages sont respectés
    assertEmployeeNotAssignedToShift(solver, "E1", "Lundi-NORMAL");
    assertEmployeeNotAssignedToShift(solver, "E1", "Mardi-NORMAL");

    // Vérifier que les autres assignations sont possibles
    SolverAssertions.assertAllShiftsCovered(solver, context.getAssignments(), shifts);
  }

  @Test
  void multipleEmployeesBlockedTest() {
    // Bloquer plusieurs employés sur différents shifts
    Map<String, List<String>> multipleBlocks =
        Map.of(
            "E1", List.of("Lundi-NORMAL"),
            "E2", List.of("Mardi-NORMAL", "Mercredi-NORMAL"),
            "E3", List.of("Jeudi-NORMAL"));

    BlockShiftForEmployeeConstraint multiConstraint =
        new BlockShiftForEmployeeConstraint(
            ConstraintConfig.of(
                ConstraintType.BLOCKED_SHIFT_EMPLOYEE,
                ConstraintNature.HARD,
                ParameterKey.BLOCKED_ASSIGNMENTS.getKeyName(),
                multipleBlocks));

    multiConstraint.applyHardConstraint(context);

    // Ajouter contrainte de couverture minimum
    MinimumCoverageConstraint coverage =
        new MinimumCoverageConstraint(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD));
    coverage.applyHardConstraint(context);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // Vérifier tous les blocages
    assertEmployeeNotAssignedToShift(solver, "E1", "Lundi-NORMAL");
    assertEmployeeNotAssignedToShift(solver, "E2", "Mardi-NORMAL");
    assertEmployeeNotAssignedToShift(solver, "E2", "Mercredi-NORMAL");
    assertEmployeeNotAssignedToShift(solver, "E3", "Jeudi-NORMAL");

    SolverAssertions.assertAllShiftsCovered(solver, context.getAssignments(), shifts);
  }

  @Test
  void noBlockedAssignmentsTest() {
    // Tester avec une map vide (aucun blocage)
    Map<String, List<String>> noBlocks = Map.of();

    BlockShiftForEmployeeConstraint noBlockConstraint =
        new BlockShiftForEmployeeConstraint(
            ConstraintConfig.of(
                ConstraintType.BLOCKED_SHIFT_EMPLOYEE,
                ConstraintNature.HARD,
                ParameterKey.BLOCKED_ASSIGNMENTS.getKeyName(),
                noBlocks));

    noBlockConstraint.applyHardConstraint(context);

    // Ajouter contrainte de couverture minimum
    MinimumCoverageConstraint coverage =
        new MinimumCoverageConstraint(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD));
    coverage.applyHardConstraint(context);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // Tous les shifts devraient pouvoir être couverts normalement
    SolverAssertions.assertAllShiftsCovered(solver, context.getAssignments(), shifts);
  }

  @Test
  void softConstraintPreferenceTest() {
    // Test version SOFT : préférence de non-assignation mais pas interdiction
    Map<String, List<String>> preferredBlocks = Map.of("E1", List.of("Lundi-NORMAL"));

    BlockShiftForEmployeeConstraint softConstraint =
        new BlockShiftForEmployeeConstraint(
            ConstraintConfig.of(
                ConstraintType.BLOCKED_SHIFT_EMPLOYEE,
                ConstraintNature.SOFT,
                ParameterKey.BLOCKED_ASSIGNMENTS.getKeyName(),
                preferredBlocks));

    // Appliquer seulement la contrainte SOFT (pas de HARD)
    ObjectiveCollector collector = new ObjectiveCollector();
    softConstraint.applySoftConstraint(context, collector);

    // Ajouter contrainte de couverture minimum pour forcer assignations
    MinimumCoverageConstraint coverage =
        new MinimumCoverageConstraint(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD));
    coverage.applyHardConstraint(context);

    // Finaliser l'objectif et résoudre
    if (!collector.isEmpty()) {
      context.getModel().maximize(collector.build());
    }
    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // Vérifier que tous les shifts sont couverts (contrainte HARD respectée)
    SolverAssertions.assertAllShiftsCovered(solver, context.getAssignments(), shifts);

    // La contrainte SOFT devrait avoir influencé l'optimisation
    // Si possible, E1 devrait éviter le Lundi-NORMAL, mais ce n'est pas garanti
    // Le test vérifie surtout que la solution reste faisable
  }

  @Test
  void infeasibleScenarioTest() {
    // Scénario impossible : bloquer tous les employés sur un shift qui ne peut être couvert
    List<Employee> twoEmployees = TestDataFactory.createEmployees(2);
    List<Shift> oneShift =
        List.of(new Shift("CriticalShift", week1.getDay(0), TestDataFactory.NORMAL_SHIFT, 1, 1));

    // Bloquer les 2 employés sur le seul shift
    Map<String, List<String>> impossibleBlocks =
        Map.of(
            "E1", List.of("CriticalShift"),
            "E2", List.of("CriticalShift"));

    SchedulingContext impossibleContext = TestDataFactory.createContext(twoEmployees, oneShift);

    BlockShiftForEmployeeConstraint impossibleConstraint =
        new BlockShiftForEmployeeConstraint(
            ConstraintConfig.of(
                ConstraintType.BLOCKED_SHIFT_EMPLOYEE,
                ConstraintNature.HARD,
                ParameterKey.BLOCKED_ASSIGNMENTS.getKeyName(),
                impossibleBlocks));

    // Appliquer les deux contraintes pour rendre le scénario infaisable
    impossibleConstraint.applyHardConstraint(impossibleContext);

    // Ajouter contrainte de couverture minimum - rend le scénario impossible
    MinimumCoverageConstraint coverage =
        new MinimumCoverageConstraint(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD));
    coverage.applyHardConstraint(impossibleContext);

    // Maintenant le scénario devrait être infaisable
    SolverAssertions.assertNoSolutionExists(
        solver,
        impossibleContext.getModel(),
        "Tous les employés sont bloqués sur le seul shift disponible, mais celui-ci doit être couvert");
  }

  /** Utilitaire pour vérifier qu'un employé n'est pas assigné à un shift. */
  private void assertEmployeeNotAssignedToShift(
      CpSolver solver, String employeeId, String shiftId) {
    int employeeIndex = getEmployeeIndex(employeeId);
    int shiftIndex = getShiftIndex(shiftId);

    assertEquals(
        0,
        solver.value(context.getAssignments()[employeeIndex][shiftIndex]),
        String.format("L'employé %s ne devrait pas être assigné au shift %s", employeeId, shiftId));
  }

  private int getEmployeeIndex(String employeeId) {
    for (int i = 0; i < employees.size(); i++) {
      if (employees.get(i).id().equals(employeeId)) {
        return i;
      }
    }
    throw new IllegalArgumentException("Employé non trouvé: " + employeeId);
  }

  private int getShiftIndex(String shiftId) {
    for (int i = 0; i < shifts.size(); i++) {
      if (shifts.get(i).id().equals(shiftId)) {
        return i;
      }
    }
    throw new IllegalArgumentException("Shift non trouvé: " + shiftId);
  }
}
