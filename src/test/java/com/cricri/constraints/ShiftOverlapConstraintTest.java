package com.cricri.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.cricri.model.ShiftDay;
import com.cricri.model.ShiftType;
import com.cricri.model.Week;
import com.cricri.service.ObjectiveCollector;
import com.cricri.service.SchedulingContext;
import com.cricri.testutils.ConstraintTestBase;
import com.cricri.testutils.SolverAssertions;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;

/**
 * Tests pour la contrainte de chevauchement entre shifts.
 *
 * <p>Cette contrainte s'applique aux shifts qui se chevauchent temporellement
 * pour garantir qu'au moins N employés soient présents dans les deux shifts
 * pendant la période de chevauchement pour assurer la transmission d'informations.
 */
class ShiftOverlapConstraintTest extends ConstraintTestBase {

  @Test
  void testConstraintProperties() {
    // Given
    ShiftOverlapConstraint constraint =
        new ShiftOverlapConstraint(
            ConstraintConfig.of(ConstraintType.SHIFT_OVERLAP, ConstraintNature.HARD, ParameterKey.MIN_OVERLAP_EMPLOYEES.getKeyName(), 1)
        );

    // Then
    testConstraintProperties(constraint);
    assertEquals("ShiftOverlap(minOverlap=1emp, HARD)", constraint.getName());
    assertEquals(ConstraintNature.HARD, constraint.getNature());
  }

  @Test
  void testHardConstraintWithOverlappingShifts() {
    // Given - Deux shifts qui se chevauchent réellement (matin 8h-13h, après-midi 12h-17h)
    Week week = TestDataFactory.WEEK_1;
    ShiftDay monday = week.getDay(0);

    // Shift du matin 8h-13h
    ShiftType morningType = new ShiftType("MATIN", LocalTime.of(8, 0), LocalTime.of(13, 0), Duration.ofMinutes(0)); // 8h-13h, 5h effectives
    Shift morningShift = new Shift("morning", monday, morningType, 2, 2);

    // Shift d'après-midi 12h-17h (chevauchement 12h-13h)
    ShiftType afternoonType = new ShiftType("APREM", LocalTime.of(12, 0), LocalTime.of(17, 0), Duration.ofMinutes(0)); // 12h-17h, 5h effectives
    Shift afternoonShift = new Shift("afternoon", monday, afternoonType, 2, 2);

    List<Shift> overlappingShifts = Arrays.asList(morningShift, afternoonShift);
    SchedulingContext context = TestDataFactory.createContext(employees, overlappingShifts);

    ShiftOverlapConstraint constraint =
        new ShiftOverlapConstraint(
            ConstraintConfig.of(ConstraintType.SHIFT_OVERLAP, ConstraintNature.HARD, ParameterKey.MIN_OVERLAP_EMPLOYEES.getKeyName(), 1)
        ); // 1 employé dans les deux shifts

    // When - Appliquer la contrainte HARD
    constraint.applyHardConstraint(context);

    // Then - Le modèle doit avoir une solution qui respecte le chevauchement
    SolverAssertions.assertSolutionExists(
        solver,
        context.getModel(),
        "La contrainte de chevauchement HARD devrait garantir qu'au moins 1 employé soit dans les deux shifts chevauchants");
  }

  @Test
  void testHardConstraintWithNonOverlappingShifts() {
    // Given - Deux shifts qui ne se chevauchent pas (contrainte ne s'applique pas)
    Week week = TestDataFactory.WEEK_1;
    ShiftDay monday = week.getDay(0);

    // Shift du matin 8h-12h
    ShiftType morningType = new ShiftType("MATIN", LocalTime.of(8, 0), LocalTime.of(12, 0), Duration.ofMinutes(0)); // 8h-12h
    Shift morningShift = new Shift("morning", monday, morningType, 2, 2);

    // Shift du soir 18h-22h (gap de 6h, pas de chevauchement)
    ShiftType eveningType = new ShiftType("SOIR", LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(0)); // 18h-22h
    Shift eveningShift = new Shift("evening", monday, eveningType, 2, 2);

    List<Shift> separateShifts = Arrays.asList(morningShift, eveningShift);
    SchedulingContext context = TestDataFactory.createContext(employees, separateShifts);

    ShiftOverlapConstraint constraint =
        new ShiftOverlapConstraint(
            ConstraintConfig.of(ConstraintType.SHIFT_OVERLAP, ConstraintNature.HARD, ParameterKey.MIN_OVERLAP_EMPLOYEES.getKeyName(), 1)
        );

    // When
    constraint.applyHardConstraint(context);

    // Then - Doit fonctionner car pas de chevauchement = pas de contrainte
    SolverAssertions.assertSolutionExists(
        solver,
        context.getModel(),
        "La contrainte ne devrait pas s'appliquer aux shifts qui ne se chevauchent pas");
  }

  @Test
  void testHardConstraintWithImpossibleStaffingRequirement() {
    // Given - Shifts qui se chevauchent mais pas assez d'employés pour satisfaire les deux équipes
    Week week = TestDataFactory.WEEK_1;
    ShiftDay monday = week.getDay(0);

    // Shift du matin 8h-13h (3 employés exactement)
    ShiftType morningType = new ShiftType("MATIN", LocalTime.of(8, 0), LocalTime.of(13, 0), Duration.ofMinutes(0));
    Shift morningShift = new Shift("morning", monday, morningType, 3, 3);

    // Shift d'après-midi 12h-17h chevauchant (3 employés exactement)
    ShiftType afternoonType = new ShiftType("APREM", LocalTime.of(12, 0), LocalTime.of(17, 0), Duration.ofMinutes(0)); // 12h-17h, chevauche 12h-13h
    Shift afternoonShift = new Shift("afternoon", monday, afternoonType, 3, 3);

    // On a seulement 4 employés au total, mais on veut 5 employés dans les deux shifts
    List<Employee> totalEmployees = TestDataFactory.createEmployees(4);
    List<Employee> limitedEmployees = totalEmployees.subList(0, 4);
    List<Shift> impossibleShifts = Arrays.asList(morningShift, afternoonShift);
    SchedulingContext impossibleContext = TestDataFactory.createContext(limitedEmployees, impossibleShifts);

    ShiftOverlapConstraint constraint =
        new ShiftOverlapConstraint(
            ConstraintConfig.of(ConstraintType.SHIFT_OVERLAP, ConstraintNature.HARD, ParameterKey.MIN_OVERLAP_EMPLOYEES.getKeyName(), 5)
        ); // 5 employés dans les deux shifts (impossible)

    // When/Then - Devrait rendre le scénario infaisable
    testConstraintMakesScenarioInfeasible(
        constraint,
        impossibleContext,
        "Impossible d'avoir 5 employés dans les deux shifts avec seulement 4 employés total");
  }

  @Test
  void testSoftConstraintCreatesObjectiveTerms() {
    // Given - Créer des shifts qui se chevauchent pour que la contrainte s'applique
    Week week = TestDataFactory.WEEK_1;
    ShiftDay monday = week.getDay(0);

    // Shift du matin 8h-13h
    ShiftType morningType = new ShiftType("MATIN", LocalTime.of(8, 0), LocalTime.of(13, 0), Duration.ofMinutes(0));
    Shift morningShift = new Shift("morning", monday, morningType, 2, 2);

    // Shift d'après-midi 12h-17h (chevauchement 12h-13h)
    ShiftType afternoonType = new ShiftType("APREM", LocalTime.of(12, 0), LocalTime.of(17, 0), Duration.ofMinutes(0));
    Shift afternoonShift = new Shift("afternoon", monday, afternoonType, 2, 2);

    List<Shift> overlappingShifts = Arrays.asList(morningShift, afternoonShift);
    SchedulingContext context = TestDataFactory.createContext(employees, overlappingShifts);
    ObjectiveCollector collector = new ObjectiveCollector();
    ConstraintConfig config =
        ConstraintConfig.of(
            ConstraintType.SHIFT_OVERLAP, ConstraintNature.SOFT, ParameterKey.MIN_OVERLAP_EMPLOYEES.getKeyName(), 1);

    ShiftOverlapConstraint constraint =
        new ShiftOverlapConstraint(config);

    // When
    constraint.applySoftConstraint(context, collector);

    // Then - Doit ajouter des termes d'objectif
    assertFalse(collector.isEmpty(), "La contrainte SOFT doit ajouter des termes d'objectif");
    assertTrue(collector.getTermCount() > 0, "Des termes doivent être ajoutés au collecteur");
  }

  @Test
  void testSoftConstraintIntegration() {
    // Given - Configuration avec chevauchement en SOFT
    Week week = TestDataFactory.WEEK_1;
    ShiftDay monday = week.getDay(0);

    // Shift du matin 8h-13h
    ShiftType morningType = new ShiftType("MATIN", LocalTime.of(8, 0), LocalTime.of(13, 0), Duration.ofMinutes(0));
    Shift morningShift = new Shift("morning", monday, morningType, 2, 2);

    // Shift d'après-midi 12h-17h (chevauchement 12h-13h)
    ShiftType afternoonType = new ShiftType("APREM", LocalTime.of(12, 0), LocalTime.of(17, 0), Duration.ofMinutes(0));
    Shift afternoonShift = new Shift("afternoon", monday, afternoonType, 2, 2);

    List<Shift> shifts = Arrays.asList(morningShift, afternoonShift);
    SchedulingContext context = TestDataFactory.createContext(employees, shifts);
    ObjectiveCollector collector = new ObjectiveCollector();

    ShiftOverlapConstraint constraint =
        new ShiftOverlapConstraint(
            ConstraintConfig.of(ConstraintType.SHIFT_OVERLAP, ConstraintNature.SOFT, ParameterKey.MIN_OVERLAP_EMPLOYEES.getKeyName(), 1)
        ); // 1 employé dans les deux shifts

    // When
    constraint.applySoftConstraint(context, collector);

    // Then - Test d'intégration avec solver
    context.getModel().maximize(collector.build());
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(context.getModel());
    assertTrue(status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE);
  }

  @Test
  void testMultipleEmployeeOverlap() {
    // Given - Test avec requirement de 2 employés de chaque équipe
    Week week = TestDataFactory.WEEK_1;
    ShiftDay monday = week.getDay(0);

    // Shift du matin 8h-13h (3 employés)
    ShiftType morningType = new ShiftType("MATIN", LocalTime.of(8, 0), LocalTime.of(13, 0), Duration.ofMinutes(0));
    Shift morningShift = new Shift("morning", monday, morningType, 3, 3);

    // Shift d'après-midi 12h-17h chevauchant (3 employés)
    ShiftType afternoonType = new ShiftType("APREM", LocalTime.of(12, 0), LocalTime.of(17, 0), Duration.ofMinutes(0)); // 12h-17h, chevauche 12h-13h
    Shift afternoonShift = new Shift("afternoon", monday, afternoonType, 3, 3);

    List<Shift> shifts = Arrays.asList(morningShift, afternoonShift);
    SchedulingContext context = TestDataFactory.createContext(employees, shifts);

    ShiftOverlapConstraint constraint =
        new ShiftOverlapConstraint(
            ConstraintConfig.of(ConstraintType.SHIFT_OVERLAP, ConstraintNature.HARD, ParameterKey.MIN_OVERLAP_EMPLOYEES.getKeyName(), 2)
        ); // 2 employés dans les deux shifts

    // When
    constraint.applyHardConstraint(context);

    // Then
    SolverAssertions.assertSolutionExists(
        solver,
        context.getModel(),
        "La contrainte devrait fonctionner avec 2 employés dans les deux shifts chevauchants");
  }

  @Test
  void testDifferentDaysNoOverlapRequired() {
    // Given - Shifts sur des jours différents (contrainte ne s'applique pas)
    Week week = TestDataFactory.WEEK_1;
    ShiftDay monday = week.getDay(0);
    ShiftDay tuesday = week.getDay(1);

    // Shift de fin lundi 20h-23h59
    ShiftType mondayType = new ShiftType("LUNDI", LocalTime.of(20, 0), LocalTime.of(23, 59), Duration.ofMinutes(0)); // 20h-23h59
    Shift mondayShift = new Shift("monday", monday, mondayType, 2, 2);

    // Shift de début mardi 0h-4h (pas de chevauchement)
    ShiftType tuesdayType = new ShiftType("MARDI", LocalTime.of(0, 0), LocalTime.of(4, 0), Duration.ofMinutes(0)); // 00h-04h
    Shift tuesdayShift = new Shift("tuesday", tuesday, tuesdayType, 2, 2);

    List<Shift> differentDayShifts = Arrays.asList(mondayShift, tuesdayShift);
    SchedulingContext context = TestDataFactory.createContext(employees, differentDayShifts);

    ShiftOverlapConstraint constraint =
        new ShiftOverlapConstraint(
            ConstraintConfig.of(ConstraintType.SHIFT_OVERLAP, ConstraintNature.HARD, ParameterKey.MIN_OVERLAP_EMPLOYEES.getKeyName(), 1)
        );

    // When
    constraint.applyHardConstraint(context);

    // Then - Doit fonctionner car contrainte ne s'applique pas entre jours différents
    SolverAssertions.assertSolutionExists(
        solver,
        context.getModel(),
        "La contrainte ne devrait pas s'appliquer entre différents jours");
  }

  @Test
  void testOverlapDetection() {
    // Given - Test spécifique pour la détection de chevauchement
    Week week = TestDataFactory.WEEK_1;
    ShiftDay monday = week.getDay(0);

    // Shifts qui se chevauchent de 12h30 à 13h
    ShiftType type1 = new ShiftType("TYPE1", LocalTime.of(8, 0), LocalTime.of(13, 0), Duration.ofMinutes(0)); // 8h-13h
    Shift shift1 = new Shift("shift1", monday, type1, 2, 2);

    ShiftType type2 = new ShiftType("TYPE2", LocalTime.of(12, 30), LocalTime.of(17, 30), Duration.ofMinutes(0)); // 12h30-17h30 (chevauche avec shift1)
    Shift shift2 = new Shift("shift2", monday, type2, 2, 2);

    // Shift qui ne chevauche avec aucun autre
    ShiftType type3 = new ShiftType("TYPE3", LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(0)); // 18h-22h
    Shift shift3 = new Shift("shift3", monday, type3, 2, 2);

    List<Shift> mixedShifts = Arrays.asList(shift1, shift2, shift3);
    SchedulingContext context = TestDataFactory.createContext(employees, mixedShifts);

    ShiftOverlapConstraint constraint = new ShiftOverlapConstraint(
        ConstraintConfig.of(ConstraintType.SHIFT_OVERLAP, ConstraintNature.HARD, ParameterKey.MIN_OVERLAP_EMPLOYEES.getKeyName(), 1)
    );

    // When
    constraint.applyHardConstraint(context);

    // Then - Seuls shift1 et shift2 devraient être contraints (ils se chevauchent)
    SolverAssertions.assertSolutionExists(
        solver,
        context.getModel(),
        "La contrainte devrait détecter correctement les shifts qui se chevauchent");
  }
}
