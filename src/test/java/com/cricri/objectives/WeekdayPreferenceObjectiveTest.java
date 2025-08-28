package com.cricri.objectives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cricri.constraints.AssignmentHoursConstraint;
import com.cricri.constraints.MinimumCoverageConstraint;
import com.cricri.constraints.WorkingDaysConstraint;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.service.SchedulingContext;
import com.cricri.testutils.SolverAssertions;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;

/**
 * Tests unitaires pour WeekdayPreferenceObjective.
 * 
 * Cette fonction objectif privilégie l'assignation de plus d'employés
 * aux shifts en semaine par rapport aux shifts de weekend.
 */
class WeekdayPreferenceObjectiveTest {

  private List<Employee> employees;
  private List<Shift> weekShifts;
  private SchedulingContext context;
  private WeekdayPreferenceObjective objective;

  @BeforeEach
  void setUp() {
    Loader.loadNativeLibraries();

    // Utiliser plusieurs employés pour avoir des choix d'optimisation
    employees = TestDataFactory.createEmployees(4);
    
    // Créer une semaine complète de shifts pour tester la préférence weekday/weekend
    weekShifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);
    
    context = TestDataFactory.createContext(employees, weekShifts);
    objective = new WeekdayPreferenceObjective();
  }

  @Test
  void testObjectiveProperties() {
    assertEquals("WeekdayPreference(x2)", objective.getName());
    assertEquals(10, objective.getWeight(), "Poids par défaut devrait être 10");
  }

  @Test
  void testObjectiveWithCustomMultiplier() {
    WeekdayPreferenceObjective customObjective = new WeekdayPreferenceObjective(5);
    
    assertEquals("WeekdayPreference(x5)", customObjective.getName());
    assertEquals(10, customObjective.getWeight(), "Le poids est toujours 10 dans cette implémentation");
  }

  @Test
  void testWeekdayPreferenceOptimization() {
    // Appliquer les contraintes de base
    new MinimumCoverageConstraint().apply(context);
    new AssignmentHoursConstraint(5 * 60).apply(context);
    new WorkingDaysConstraint().apply(context);
    
    // Appliquer l'objectif
    objective.apply(context);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    // Analyser la répartition weekday vs weekend
    int weekdayEmployees = 0;
    int weekendEmployees = 0;
    int weekdayShiftsWithExtraStaff = 0;
    int weekendShiftsWithExtraStaff = 0;

    for (int s = 0; s < weekShifts.size(); s++) {
      Shift shift = weekShifts.get(s);
      int employeesAssigned = countAssignedEmployees(solver, s);
      
      if (shift.day().isWeekend()) {
        weekendEmployees += employeesAssigned;
        if (employeesAssigned > shift.minEmployes()) {
          weekendShiftsWithExtraStaff++;
        }
      } else {
        weekdayEmployees += employeesAssigned;
        if (employeesAssigned > shift.minEmployes()) {
          weekdayShiftsWithExtraStaff++;
        }
      }
    }

    // L'optimisation devrait privilégier les jours de semaine
    assertTrue(weekdayEmployees >= weekendEmployees,
        String.format("Optimisation weekday: %d employés en semaine vs %d en weekend", 
            weekdayEmployees, weekendEmployees));

    System.out.printf("Résultat optimisation:%n");
    System.out.printf("  Employés en semaine: %d%n", weekdayEmployees);
    System.out.printf("  Employés en weekend: %d%n", weekendEmployees);
    System.out.printf("  Shifts surprofil semaine: %d%n", weekdayShiftsWithExtraStaff);
    System.out.printf("  Shifts surprofil weekend: %d%n", weekendShiftsWithExtraStaff);
  }

  @Test
  void testComparisonWithoutObjective() {
    // Test sans objectif pour comparaison
    SchedulingContext contextWithoutObjective = TestDataFactory.createContext(employees, weekShifts);
    
    new MinimumCoverageConstraint().apply(contextWithoutObjective);
    new AssignmentHoursConstraint(5 * 60).apply(contextWithoutObjective);
    new WorkingDaysConstraint().apply(contextWithoutObjective);

    CpSolver solverWithoutObjective = SolverAssertions.solveAndAssertSolution(contextWithoutObjective);

    // Test avec objectif
    new MinimumCoverageConstraint().apply(context);
    new AssignmentHoursConstraint(5 * 60).apply(context);
    new WorkingDaysConstraint().apply(context);
    objective.apply(context);

    CpSolver solverWithObjective = SolverAssertions.solveAndAssertSolution(context);

    // Comparer les solutions
    int[] statsWithout = calculateWeekdayWeekendStats(solverWithoutObjective, contextWithoutObjective);
    int[] statsWith = calculateWeekdayWeekendStats(solverWithObjective, context);

    System.out.printf("Sans objectif - Semaine: %d, Weekend: %d%n", statsWithout[0], statsWithout[1]);
    System.out.printf("Avec objectif - Semaine: %d, Weekend: %d%n", statsWith[0], statsWith[1]);

    // L'objectif devrait au minimum ne pas dégrader la préférence weekday
    assertTrue(statsWith[0] >= statsWithout[0],
        "L'objectif WeekdayPreference ne devrait pas réduire les assignations en semaine");
  }

  @Test
  void testStrongWeekdayPreference() {
    // Tester avec un multiplier plus élevé
    WeekdayPreferenceObjective strongObjective = new WeekdayPreferenceObjective(10);

    new MinimumCoverageConstraint().apply(context);
    new AssignmentHoursConstraint(5 * 60).apply(context);
    new WorkingDaysConstraint().apply(context);
    strongObjective.apply(context);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(context);

    int[] stats = calculateWeekdayWeekendStats(solver, context);
    
    // Avec un multiplier fort, la différence devrait être plus marquée
    assertTrue(stats[0] > stats[1],
        String.format("Préférence forte: %d semaine > %d weekend", stats[0], stats[1]));
  }

  @Test
  void testObjectiveWithLimitedStaff() {
    // Tester avec moins d'employés pour voir l'effet de l'optimisation
    List<Employee> limitedEmployees = TestDataFactory.createEmployees(2);
    SchedulingContext limitedContext = TestDataFactory.createContext(limitedEmployees, weekShifts);

    new MinimumCoverageConstraint().apply(limitedContext);
    new AssignmentHoursConstraint(5 * 60).apply(limitedContext);
    new WorkingDaysConstraint().apply(limitedContext);
    objective.apply(limitedContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(limitedContext);

    // Même avec peu d'employés, l'objectif devrait orienter les décisions
    SolverAssertions.assertAllShiftsCovered(solver, limitedContext.getAssignments(), weekShifts);
    
    int[] stats = calculateWeekdayWeekendStats(solver, limitedContext);
    System.out.printf("Staff limité - Semaine: %d, Weekend: %d%n", stats[0], stats[1]);
    
    // Au minimum, pas moins d'employés en semaine qu'en weekend
    assertTrue(stats[0] >= stats[1], "Même avec staff limité, privilégier la semaine");
  }

  @Test
  void testObjectiveWithSurplusStaff() {
    // Tester avec beaucoup d'employés pour voir l'effet sur les assignations supplémentaires
    List<Employee> surplusEmployees = TestDataFactory.createEmployees(8);
    List<Shift> flexibleShifts = weekShifts.stream()
        .map(s -> new Shift(s.id(), s.day(), s.type(), s.minEmployes(), 3)) // Max 3 employés par shift
        .toList();
    
    SchedulingContext surplusContext = TestDataFactory.createContext(surplusEmployees, flexibleShifts);

    new MinimumCoverageConstraint().apply(surplusContext);
    new AssignmentHoursConstraint(5 * 60).apply(surplusContext);
    new WorkingDaysConstraint().apply(surplusContext);
    objective.apply(surplusContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(surplusContext);

    // Avec surplus d'employés, l'objectif devrait privilégier le surprofil en semaine
    int weekdayExtraStaff = 0;
    int weekendExtraStaff = 0;

    for (int s = 0; s < flexibleShifts.size(); s++) {
      Shift shift = flexibleShifts.get(s);
      int assignedCount = countAssignedEmployees(solver, surplusContext, s);
      int extraStaff = Math.max(0, assignedCount - shift.minEmployes());

      if (shift.day().isWeekend()) {
        weekendExtraStaff += extraStaff;
      } else {
        weekdayExtraStaff += extraStaff;
      }
    }

    System.out.printf("Staff supplémentaire - Semaine: %d, Weekend: %d%n", 
        weekdayExtraStaff, weekendExtraStaff);
    
    assertTrue(weekdayExtraStaff >= weekendExtraStaff,
        "Le staff supplémentaire devrait être privilégié en semaine");
  }

  @Test
  void testObjectiveConsistencyAcrossRuns() {
    // Tester que l'objectif donne des résultats cohérents
    new MinimumCoverageConstraint().apply(context);
    new AssignmentHoursConstraint(5 * 60).apply(context);
    new WorkingDaysConstraint().apply(context);
    objective.apply(context);

    // Première exécution
    CpSolver solver1 = new CpSolver();
    solver1.getParameters().setRandomSeed(12345); // Seed fixe pour reproductibilité
    SolverAssertions.assertSolutionExists(solver1, context.getModel());
    int[] stats1 = calculateWeekdayWeekendStats(solver1, context);

    // Deuxième exécution avec même seed
    CpSolver solver2 = new CpSolver();
    solver2.getParameters().setRandomSeed(12345);
    SolverAssertions.assertSolutionExists(solver2, context.getModel());
    int[] stats2 = calculateWeekdayWeekendStats(solver2, context);

    // Les résultats devraient être identiques avec le même seed
    assertEquals(stats1[0], stats2[0], "Résultats weekday devraient être reproductibles");
    assertEquals(stats1[1], stats2[1], "Résultats weekend devraient être reproductibles");
  }

  @Test
  void testObjectiveWithMixedShiftTypes() {
    // Tester avec différents types de shifts
    List<Shift> mixedShifts = TestDataFactory.createMixedShifts(TestDataFactory.createStandardWeeks()[0]);
    SchedulingContext mixedContext = TestDataFactory.createContext(employees, mixedShifts);

    new MinimumCoverageConstraint().apply(mixedContext);
    new AssignmentHoursConstraint(5 * 60).apply(mixedContext);
    new WorkingDaysConstraint().apply(mixedContext);
    objective.apply(mixedContext);

    CpSolver solver = SolverAssertions.solveAndAssertSolution(mixedContext);

    // L'objectif devrait fonctionner même avec des types de shifts variés
    SolverAssertions.assertAllShiftsCovered(solver, mixedContext.getAssignments(), mixedShifts);

    int[] stats = calculateWeekdayWeekendStats(solver, mixedContext);
    System.out.printf("Shifts mixtes - Semaine: %d, Weekend: %d%n", stats[0], stats[1]);
  }

  /**
   * Compte le nombre d'employés assignés à un shift donné
   */
  private int countAssignedEmployees(CpSolver solver, int shiftIndex) {
    int count = 0;
    for (int e = 0; e < employees.size(); e++) {
      if (solver.value(context.getAssignments()[e][shiftIndex]) == 1) {
        count++;
      }
    }
    return count;
  }
  
  private int countAssignedEmployees(CpSolver solver, SchedulingContext contextToUse, int shiftIndex) {
    int count = 0;
    for (int e = 0; e < contextToUse.getEmployeeCount(); e++) {
      if (solver.value(contextToUse.getAssignments()[e][shiftIndex]) == 1) {
        count++;
      }
    }
    return count;
  }

  /**
   * Calcule les statistiques weekday vs weekend
   * @return [employés en semaine, employés en weekend]
   */
  private int[] calculateWeekdayWeekendStats(CpSolver solver, SchedulingContext contextToUse) {
    int weekdayEmployees = 0;
    int weekendEmployees = 0;
    
    // Utiliser les shifts du contexte passé en paramètre
    List<Shift> shiftsToUse = contextToUse.getShifts();
    int employeeCount = contextToUse.getEmployeeCount();

    for (int s = 0; s < shiftsToUse.size(); s++) {
      Shift shift = shiftsToUse.get(s);
      int employeesAssigned = 0;
      
      for (int e = 0; e < employeeCount; e++) {
        if (solver.value(contextToUse.getAssignments()[e][s]) == 1) {
          employeesAssigned++;
        }
      }

      if (shift.day().isWeekend()) {
        weekendEmployees += employeesAssigned;
      } else {
        weekdayEmployees += employeesAssigned;
      }
    }

    return new int[]{weekdayEmployees, weekendEmployees};
  }
}