package com.cricri.performance;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.model.Week;
import com.cricri.service.ModularShiftScheduler;
import com.cricri.testutils.SolverAssertions;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;

/**
 * Tests de performance pour s'assurer que le système reste performant
 * même avec des volumes de données importants.
 */
class SchedulingPerformanceTest {

  @BeforeEach
  void setUp() {
    Loader.loadNativeLibraries();
  }

  @Test
  @Timeout(5) // Maximum 5 secondes
  void testSmallScalePerformance() {
    List<Employee> employees = TestDataFactory.createEmployees(4);
    List<Shift> shifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);
    
    long startTime = System.nanoTime();
    
    ModularShiftScheduler scheduler = TestDataFactory.createStandardScheduler(employees, shifts);
    scheduler.buildModel();
    CpSolver solver = SolverAssertions.solveAndAssertSolution(scheduler);
    
    long duration = (System.nanoTime() - startTime) / 1_000_000; // ms
    
    System.out.printf("Petite échelle: %d employés, %d shifts, %d ms%n", 
        employees.size(), shifts.size(), duration);
    
    SolverAssertions.assertAllShiftsCovered(solver, scheduler.getAssignments(), shifts);
  }

  @Test
  @Timeout(10) // Maximum 10 secondes
  void testMediumScalePerformance() {
    List<Employee> employees = TestDataFactory.createEmployees(8);
    Week[] weeks = TestDataFactory.createStandardWeeks();
    
    // Créer 2 semaines de shifts
    List<Shift> shifts = List.of(
        // Semaine 1
        new Shift("S1-L", weeks[0].getDay(0), TestDataFactory.MORNING_SHIFT, 2, 3),
        new Shift("S1-Ma", weeks[0].getDay(1), TestDataFactory.NORMAL_SHIFT, 1, 2),
        new Shift("S1-Me", weeks[0].getDay(2), TestDataFactory.EVENING_SHIFT, 2, 2),
        new Shift("S1-J", weeks[0].getDay(3), TestDataFactory.MORNING_SHIFT, 1, 2),
        new Shift("S1-V", weeks[0].getDay(4), TestDataFactory.NORMAL_SHIFT, 2, 3),
        new Shift("S1-S", weeks[0].getDay(5), TestDataFactory.EVENING_SHIFT, 1, 2),
        new Shift("S1-D", weeks[0].getDay(6), TestDataFactory.NORMAL_SHIFT, 1, 1),
        // Semaine 2
        new Shift("S2-L", weeks[1].getDay(0), TestDataFactory.NORMAL_SHIFT, 1, 2),
        new Shift("S2-Ma", weeks[1].getDay(1), TestDataFactory.EVENING_SHIFT, 1, 1),
        new Shift("S2-Me", weeks[1].getDay(2), TestDataFactory.MORNING_SHIFT, 2, 2),
        new Shift("S2-J", weeks[1].getDay(3), TestDataFactory.NORMAL_SHIFT, 1, 2),
        new Shift("S2-V", weeks[1].getDay(4), TestDataFactory.EVENING_SHIFT, 2, 2)
    );
    
    long startTime = System.nanoTime();
    
    ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts)
        .withStandardConstraints(40 * 60, 11, 5 * 60);
    
    scheduler.buildModel();
    CpSolver solver = SolverAssertions.solveAndAssertSolution(scheduler);
    
    long duration = (System.nanoTime() - startTime) / 1_000_000;
    
    System.out.printf("Moyenne échelle: %d employés, %d shifts, %d ms%n", 
        employees.size(), shifts.size(), duration);
    
    SolverAssertions.assertAllShiftsCovered(solver, scheduler.getAssignments(), shifts);
  }

  @Test
  @Timeout(30) // Maximum 30 secondes pour grosse charge
  void testConstraintComplexityPerformance() {
    // Test avec toutes les contraintes activées
    List<Employee> employees = TestDataFactory.createEmployees(6);
    List<Shift> shifts = TestDataFactory.createMixedShifts(TestDataFactory.createStandardWeeks()[0]);
    
    long startTime = System.nanoTime();
    
    ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts)
        .withMinimumCoverage()
        .withAssignmentHours(5 * 60)
        .withMaxHoursPerWeek(35 * 60)
        .withMinimumRest(11)
        .withMinimumRestDays(2)
        .withWorkingDays()
        .withWeekdayPreference(3);
    
    scheduler.buildModel();
    CpSolver solver = SolverAssertions.solveAndAssertSolution(scheduler);
    
    long duration = (System.nanoTime() - startTime) / 1_000_000;
    
    System.out.printf("Complexité maximale: %d contraintes, %d ms%n", 
        7, duration); // Nombre de contraintes appliquées
    
    SolverAssertions.assertAllShiftsCovered(solver, scheduler.getAssignments(), shifts);
  }

  @Test
  void testMemoryUsageBenchmark() {
    // Test de création/destruction répétée
    Runtime runtime = Runtime.getRuntime();
    long initialMemory = runtime.totalMemory() - runtime.freeMemory();
    
    for (int i = 0; i < 10; i++) {
      List<Employee> employees = TestDataFactory.createEmployees(4);
      List<Shift> shifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);
      
      ModularShiftScheduler scheduler = TestDataFactory.createStandardScheduler(employees, shifts);
      scheduler.buildModel();
      SolverAssertions.solveAndAssertSolution(scheduler);
      
      // Force garbage collection
      System.gc();
    }
    
    long finalMemory = runtime.totalMemory() - runtime.freeMemory();
    long memoryIncrease = finalMemory - initialMemory;
    
    System.out.printf("Utilisation mémoire après 10 itérations: +%d bytes%n", memoryIncrease);
    
    // Vérifier qu'il n'y a pas de fuite mémoire majeure (< 50MB d'augmentation)
    assert memoryIncrease < 50_000_000 : 
        "Possible fuite mémoire détectée: " + (memoryIncrease / 1_000_000) + "MB";
  }
}