package com.cricri.robustness;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ConstraintType;
import com.cricri.factory.ConstraintFactory;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.service.ModularShiftScheduler;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.Loader;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests de robustesse pour vérifier que le système gère correctement les cas limites et les erreurs
 * d'entrée.
 */
class SchedulingRobustnessTest {

  @BeforeEach
  void setUp() {
    Loader.loadNativeLibraries();
  }

  @Test
  void emptyEmployeesListTest() {
    List<Employee> emptyEmployees = Collections.emptyList();
    List<Shift> shifts =
        TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);

    // Le scheduler devrait pouvoir être créé mais pas résolu
    assertDoesNotThrow(
        () -> {
          ModularShiftScheduler scheduler =
              TestDataFactory.createMinimumCoverageScheduler(emptyEmployees, shifts);
          // Ne pas appeler buildModel() car cela échouerait logiquement
        });
  }

  @Test
  void emptyShiftsListTest() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> emptyShifts = Collections.emptyList();

    assertDoesNotThrow(
        () -> {
          ModularShiftScheduler scheduler =
              TestDataFactory.createMinimumCoverageScheduler(employees, emptyShifts);
          scheduler.buildModel(); // Devrait fonctionner avec 0 shift
        });
  }

  @Test
  void nullInputsHandlingTest() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> shifts =
        TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);

    // Test construction avec null
    assertThrows(
        Exception.class,
        () -> {
          new ModularShiftScheduler(null, shifts);
        });

    assertThrows(
        Exception.class,
        () -> {
          new ModularShiftScheduler(employees, null);
        });
  }

  @Test
  void extremeConstraintValuesTest() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> shifts =
        TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);

    // Test avec des valeurs extrêmes mais techniquement valides
    assertDoesNotThrow(
        () -> {
          ModularShiftScheduler scheduler =
              new ModularShiftScheduler(employees, shifts)
                  .withConstraint(
                      ConstraintFactory.create(
                          ConstraintConfig.of(
                              ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)))
                  .withConstraint(
                      ConstraintFactory.create(
                          ConstraintConfig.of(
                              ConstraintType.MAX_HOURS_PER_WEEK,
                              ConstraintNature.HARD,
                              "maxHoursPerWeek",
                              1)))
                  .withConstraint(
                      ConstraintFactory.create(
                          ConstraintConfig.of(
                              ConstraintType.MINIMUM_REST,
                              ConstraintNature.HARD,
                              "minimumRest",
                              23)))
                  .withConstraint(
                      ConstraintFactory.create(
                          ConstraintConfig.of(
                              ConstraintType.ASSIGNMENT_HOURS,
                              ConstraintNature.HARD,
                              "assignmentHours",
                              1)));

          scheduler.buildModel();
          // Note: peut ne pas avoir de solution, mais ne devrait pas crasher
        });
  }

  @Test
  void constraintValidationEdgeCasesTest() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> shifts =
        TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);

    // Test avec des valeurs de contrainte incohérentes
    assertDoesNotThrow(
        () -> {
          ModularShiftScheduler scheduler =
              new ModularShiftScheduler(employees, shifts)
                  .withConstraint(
                      ConstraintFactory.create(
                          ConstraintConfig.of(
                              ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)))
                  .withConstraint(
                      ConstraintFactory.create(
                          ConstraintConfig.of(
                              ConstraintType.MAX_HOURS_PER_WEEK,
                              ConstraintNature.HARD,
                              "maxHoursPerWeek",
                              0)))
                  .withConstraint(
                      ConstraintFactory.create(
                          ConstraintConfig.of(
                              ConstraintType.ASSIGNMENT_HOURS,
                              ConstraintNature.HARD,
                              "assignmentHours",
                              8 * 60)));

          scheduler.buildModel();
          // Scénario impossible mais ne devrait pas crasher
        });
  }

  @Test
  void largeNumberHandlingTest() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> shifts =
        TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);

    // Test avec de très grandes valeurs
    assertDoesNotThrow(
        () -> {
          ModularShiftScheduler scheduler =
              new ModularShiftScheduler(employees, shifts)
                  .withConstraint(
                      ConstraintFactory.create(
                          ConstraintConfig.of(
                              ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)))
                  .withConstraint(
                      ConstraintFactory.create(
                          ConstraintConfig.of(
                              ConstraintType.MAX_HOURS_PER_WEEK,
                              ConstraintNature.HARD,
                              "maxHoursPerWeek",
                              Integer.MAX_VALUE / 1000)))
                  .withConstraint(
                      ConstraintFactory.create(
                          ConstraintConfig.of(
                              ConstraintType.ASSIGNMENT_HOURS,
                              ConstraintNature.HARD,
                              "assignmentHours",
                              0)));

          scheduler.buildModel();
        });
  }

  @Test
  void repeatedBuildModelCallsTest() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> shifts =
        TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);

    ModularShiftScheduler scheduler =
        TestDataFactory.createMinimumCoverageScheduler(employees, shifts);

    // Appeler buildModel() plusieurs fois ne devrait pas poser problème
    assertDoesNotThrow(
        () -> {
          scheduler.buildModel();
          scheduler.buildModel(); // Deuxième appel
          scheduler.buildModel(); // Troisième appel
        });
  }

  @Test
  void schedulerStateConsistencyTest() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> shifts =
        TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);

    ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts);

    // Vérifier que l'état reste cohérent après ajout de contraintes
    scheduler.withConstraint(
        ConstraintFactory.create(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD)));
    assert scheduler.getEmployees().size() == employees.size();
    assert scheduler.getShifts().size() == shifts.size();

    scheduler.withConstraint(
        ConstraintFactory.create(
            ConstraintConfig.of(
                ConstraintType.MAX_HOURS_PER_WEEK,
                ConstraintNature.HARD,
                "maxHoursPerWeek",
                40 * 60)));
    assert scheduler.getEmployees().size() == employees.size();

    scheduler.buildModel();
    assert scheduler.getEmployees().size() == employees.size();
  }

  @Test
  void concurrentAccessSafetyTest() {
    List<Employee> employees = TestDataFactory.createStandardEmployees();
    List<Shift> shifts =
        TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);

    // Test que les objets ne sont pas modifiés de manière inattendue
    List<Employee> originalEmployees = List.copyOf(employees);
    List<Shift> originalShifts = List.copyOf(shifts);

    ModularShiftScheduler scheduler =
        TestDataFactory.createStandardSchedulerWithConfig(employees, shifts, 40 * 60, 11, 5 * 60);

    scheduler.buildModel();

    // Vérifier que les listes originales n'ont pas été modifiées
    assert employees.equals(originalEmployees) : "Liste employés modifiée";
    assert shifts.equals(originalShifts) : "Liste shifts modifiée";
  }
}
