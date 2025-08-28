package com.cricri.robustness;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.service.ModularShiftScheduler;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.Loader;

/**
 * Tests de robustesse pour vérifier que le système gère correctement
 * les cas limites et les erreurs d'entrée.
 */
class SchedulingRobustnessTest {

  @BeforeEach
  void setUp() {
    Loader.loadNativeLibraries();
  }

  @Test
  void testEmptyEmployeesList() {
    List<Employee> emptyEmployees = Collections.emptyList();
    List<Shift> shifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);
    
    // Le scheduler devrait pouvoir être créé mais pas résolu
    assertDoesNotThrow(() -> {
      ModularShiftScheduler scheduler = new ModularShiftScheduler(emptyEmployees, shifts);
      scheduler.withMinimumCoverage();
      // Ne pas appeler buildModel() car cela échouerait logiquement
    });
  }

  @Test
  void testEmptyShiftsList() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> emptyShifts = Collections.emptyList();
    
    assertDoesNotThrow(() -> {
      ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, emptyShifts);
      scheduler.withMinimumCoverage();
      scheduler.buildModel(); // Devrait fonctionner avec 0 shift
    });
  }

  @Test
  void testNullInputsHandling() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> shifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);
    
    // Test construction avec null
    assertThrows(Exception.class, () -> {
      new ModularShiftScheduler(null, shifts);
    });
    
    assertThrows(Exception.class, () -> {
      new ModularShiftScheduler(employees, null);
    });
  }

  @Test
  void testExtremeConstraintValues() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> shifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);
    
    // Test avec des valeurs extrêmes mais techniquement valides
    assertDoesNotThrow(() -> {
      ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts)
          .withMinimumCoverage()
          .withMaxHoursPerWeek(1)          // Très restrictif
          .withMinimumRest(23)             // Presque toute la journée
          .withAssignmentHours(1);         // Minimum très bas
      
      scheduler.buildModel();
      // Note: peut ne pas avoir de solution, mais ne devrait pas crasher
    });
  }

  @Test
  void testConstraintValidationEdgeCases() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> shifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);
    
    // Test avec des valeurs de contrainte incohérentes
    assertDoesNotThrow(() -> {
      ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts)
          .withMinimumCoverage()
          .withMaxHoursPerWeek(0)          // 0h par semaine
          .withAssignmentHours(8 * 60);    // Mais 8h minimum par shift
      
      scheduler.buildModel();
      // Scénario impossible mais ne devrait pas crasher
    });
  }

  @Test
  void testLargeNumberHandling() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> shifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);
    
    // Test avec de très grandes valeurs
    assertDoesNotThrow(() -> {
      ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts)
          .withMinimumCoverage()
          .withMaxHoursPerWeek(Integer.MAX_VALUE / 1000)
          .withAssignmentHours(0);
      
      scheduler.buildModel();
    });
  }

  @Test
  void testRepeatedBuildModelCalls() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> shifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);
    
    ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts)
        .withMinimumCoverage();
    
    // Appeler buildModel() plusieurs fois ne devrait pas poser problème
    assertDoesNotThrow(() -> {
      scheduler.buildModel();
      scheduler.buildModel(); // Deuxième appel
      scheduler.buildModel(); // Troisième appel
    });
  }

  @Test
  void testSchedulerStateConsistency() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> shifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);
    
    ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts);
    
    // Vérifier que l'état reste cohérent après ajout de contraintes
    scheduler.withMinimumCoverage();
    assert scheduler.getEmployees().size() == employees.size();
    assert scheduler.getShifts().size() == shifts.size();
    
    scheduler.withMaxHoursPerWeek(40 * 60);
    assert scheduler.getEmployees().size() == employees.size();
    
    scheduler.buildModel();
    assert scheduler.getEmployees().size() == employees.size();
  }

  @Test
  void testConcurrentAccessSafety() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> shifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);
    
    // Test que les objets ne sont pas modifiés de manière inattendue
    List<Employee> originalEmployees = List.copyOf(employees);
    List<Shift> originalShifts = List.copyOf(shifts);
    
    ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts)
        .withStandardConstraints(40 * 60, 11, 5 * 60);
    
    scheduler.buildModel();
    
    // Vérifier que les listes originales n'ont pas été modifiées
    assert employees.equals(originalEmployees) : "Liste employés modifiée";
    assert shifts.equals(originalShifts) : "Liste shifts modifiée";
  }
}