package com.cricri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.model.ShiftType;
import com.cricri.model.Week;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShiftSchedulerTest {

  private ShiftScheduler scheduler;
  private List<Employee> employees;
  private List<Shift> shifts;
  private List<ShiftType> shiftTypes;

  @BeforeEach
  void setUp() {
    Loader.loadNativeLibraries();

    // Créer des employés
    Employee emp1 = new Employee("E1", "Alice");
    Employee emp2 = new Employee("E2", "Bob");

    employees = Arrays.asList(emp1, emp2);

    // Créer des types de shift
    ShiftType matin = new ShiftType("MATIN", 390, 870, 480); // 6h30-14h30, 8h
    shiftTypes = Arrays.asList(matin);

    // Créer une semaine
    Week week = Week.create(0);

    // Créer des shifts
    Shift shift1 = new Shift("Jour1-MATIN", week.getDay(0), matin, 1, 2); // Lundi
    Shift shift2 = new Shift("Jour2-MATIN", week.getDay(1), matin, 1, 1); // Mardi
    shifts = Arrays.asList(shift1, shift2);

    // Initialiser le scheduler
    scheduler = new ShiftScheduler();
    scheduler.setEmployees(employees);
    scheduler.setShifts(shifts);
    scheduler.setShiftIndexMap(new HashMap<>());
    for (int i = 0; i < shifts.size(); i++) {
      scheduler.getShiftIndexMap().put(shifts.get(i).id(), i);
    }
  }

  @Test
  void addMinimumEmployeesConstraintTest() {
    // Construire le modèle
    scheduler.buildModel();

    // Résoudre
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(scheduler.getModel());

    // Vérifier qu'une solution existe
    assertEquals(CpSolverStatus.OPTIMAL, status, "Une solution doit exister");

    // Vérifier que chaque shift a au moins un employé assigné
    for (int s = 0; s < shifts.size(); s++) {
      long employeesAssigned = 0;
      for (int e = 0; e < employees.size(); e++) {
        if (solver.value(scheduler.getAssignments()[e][s]) == 1) {
          employeesAssigned++;
        }
      }
      assertTrue(
          employeesAssigned >= shifts.get(s).minEmployes(),
          "Le shift "
              + shifts.get(s).id()
              + " doit avoir au moins "
              + shifts.get(s).minEmployes()
              + " employé(s), mais n'en a que "
              + employeesAssigned);
    }
  }

  @Test
  void addMaxHoursPerWeekConstraintTest() {
    // Configuration pour tester la contrainte hebdomadaire - plus réaliste
    Employee emp1 = new Employee("E1", "Alice");
    Employee emp2 = new Employee("E2", "Bob");
    List<Employee> testEmployees = Arrays.asList(emp1, emp2);

    ShiftType normalShift = new ShiftType("NORMAL", 480, 960, 480); // 8h-16h = 8h

    // Créer 2 semaines
    Week week1 = Week.create(0);
    Week week2 = Week.create(1);

    // Créer des shifts sur 2 semaines - faisable avec 2 employés
    Shift lundi1 = new Shift("Lundi1-NORMAL", week1.getDay(0), normalShift, 1, 1); // Semaine 1
    Shift mardi1 = new Shift("Mardi1-NORMAL", week1.getDay(1), normalShift, 1, 1); // Semaine 1
    Shift mercredi1 =
        new Shift("Mercredi1-NORMAL", week1.getDay(2), normalShift, 1, 1); // Semaine 1
    Shift jeudi1 = new Shift("Jeudi1-NORMAL", week1.getDay(3), normalShift, 1, 1); // Semaine 1
    Shift vendredi1 =
        new Shift("Vendredi1-NORMAL", week1.getDay(4), normalShift, 1, 1); // Semaine 1
    Shift lundi2 = new Shift("Lundi2-NORMAL", week2.getDay(0), normalShift, 1, 1); // Semaine 2
    Shift mardi2 = new Shift("Mardi2-NORMAL", week2.getDay(1), normalShift, 1, 1); // Semaine 2

    List<Shift> testShifts =
        Arrays.asList(lundi1, mardi1, mercredi1, jeudi1, vendredi1, lundi2, mardi2);

    // Créer un scheduler avec limite réaliste
    ShiftScheduler testScheduler = new ShiftScheduler();
    testScheduler.setEmployees(testEmployees);
    testScheduler.setShifts(testShifts);
    testScheduler.setMaxHoursPerWeek(40 * 60); // Limite: 40h par semaine (5 shifts max)
    testScheduler.setShiftIndexMap(new HashMap<>());

    for (int i = 0; i < testShifts.size(); i++) {
      testScheduler.getShiftIndexMap().put(testShifts.get(i).id(), i);
    }

    // Construire le modèle
    testScheduler.buildModel();

    // Résoudre
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(testScheduler.getModel());

    // Vérifier qu'une solution existe
    assertTrue(
        status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE,
        "Une solution doit exister avec la contrainte d'heures");

    // Vérifier que la contrainte d'heures par semaine est respectée
    int[][] hoursPerEmployeePerWeek = new int[2][2]; // [employé][semaine]

    for (int e = 0; e < 2; e++) {
      for (int s = 0; s < testShifts.size(); s++) {
        if (solver.value(testScheduler.getAssignments()[e][s]) == 1) {
          Shift shift = testShifts.get(s);
          int week = shift.day().getWeekNumber();
          hoursPerEmployeePerWeek[e][week] += shift.type().dureeMinutes();
        }
      }
    }

    // Vérifier que chaque employé respecte la limite par semaine
    for (int e = 0; e < 2; e++) {
      for (int w = 0; w < 2; w++) {
        assertTrue(
            hoursPerEmployeePerWeek[e][w] <= 40 * 60,
            "L'employé "
                + (e + 1)
                + " semaine "
                + (w + 1)
                + " dépasse la limite: "
                + (hoursPerEmployeePerWeek[e][w] / 60.0)
                + "h > 40h");
      }
    }

    // Vérifier que tous les shifts sont couverts
    int coveredShifts = 0;
    for (int s = 0; s < testShifts.size(); s++) {
      for (int e = 0; e < 2; e++) {
        if (solver.value(testScheduler.getAssignments()[e][s]) == 1) {
          coveredShifts++;
          break; // Un seul employé par shift suffit
        }
      }
    }
    assertEquals(testShifts.size(), coveredShifts, "Tous les shifts doivent être couverts");
  }

  @Test
  void addMinimumRestConstraintTest() {
    // Configuration spécifique pour tester la contrainte de repos minimum
    Employee emp1 = new Employee("E1", "Alice");
    Employee emp2 = new Employee("E2", "Bob");
    List<Employee> testEmployees = Arrays.asList(emp1, emp2);

    // Créer une semaine
    Week week = Week.create(0);

    // Test simple: 3 shifts seulement pour que le problème reste faisable
    // Test 1: Shifts qui se chevauchent le même jour (comme dans Main.java)
    ShiftType matin = new ShiftType("MATIN", 420, 945, 525); // 7h00-15h45 = 8h45
    ShiftType soir = new ShiftType("SOIR", 900, 1425, 525); // 15h00-23h45 = 8h45

    // Vendredi matin et soir qui se chevauchent (15h00-15h45)
    Shift vendrediMatin = new Shift("Vendredi-MATIN", week.getDay(4), matin, 1, 1);
    Shift vendrediSoir = new Shift("Vendredi-SOIR", week.getDay(4), soir, 1, 1);

    // Shift sans conflit
    Shift samediMatin = new Shift("Samedi-MATIN", week.getDay(5), matin, 1, 1);

    List<Shift> testShifts = Arrays.asList(vendrediMatin, vendrediSoir, samediMatin);

    // Créer le scheduler
    ShiftScheduler testScheduler = new ShiftScheduler();
    testScheduler.setEmployees(testEmployees);
    testScheduler.setShifts(testShifts);
    testScheduler.setMinRestHours(11); // 11h de repos minimum
    testScheduler.setShiftIndexMap(new HashMap<>());

    for (int i = 0; i < testShifts.size(); i++) {
      testScheduler.getShiftIndexMap().put(testShifts.get(i).id(), i);
    }

    // Construire le modèle
    testScheduler.buildModel();

    // Résoudre
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(testScheduler.getModel());

    // Vérifier qu'une solution existe
    assertTrue(
        status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE,
        "Une solution doit exister avec la contrainte de repos");

    // Vérifier que la contrainte de repos est respectée
    for (int e = 0; e < testEmployees.size(); e++) {
      // Un employé ne peut pas faire Vendredi matin ET soir (chevauchement)
      boolean assignedToVendrediMatin = solver.value(testScheduler.getAssignments()[e][0]) == 1;
      boolean assignedToVendrediSoir = solver.value(testScheduler.getAssignments()[e][1]) == 1;

      assertFalse(
          assignedToVendrediMatin && assignedToVendrediSoir,
          "L'employé "
              + testEmployees.get(e).nom()
              + " ne peut pas faire Vendredi matin ET soir (chevauchement 15h00-15h45)");
    }

    // Vérifier que tous les shifts sont couverts
    int coveredShifts = 0;
    for (int s = 0; s < testShifts.size(); s++) {
      for (int e = 0; e < testEmployees.size(); e++) {
        if (solver.value(testScheduler.getAssignments()[e][s]) == 1) {
          coveredShifts++;
          break;
        }
      }
    }
    assertEquals(testShifts.size(), coveredShifts, "Tous les shifts doivent être couverts");
  }

  @Test
  void addWorkingDaysConstraintsTest() {
    // Configuration pour tester la liaison assignments -> workingDays
    Employee emp1 = new Employee("E1", "Alice");
    Employee emp2 = new Employee("E2", "Bob");
    List<Employee> testEmployees = Arrays.asList(emp1, emp2);

    ShiftType normalShift = new ShiftType("NORMAL", 480, 960, 480); // 8h-16h = 8h

    // Créer une semaine avec plusieurs jours
    Week week = Week.create(0);

    // Créer des shifts sur différents jours de la semaine
    Shift lundiMatin = new Shift("Lundi-MATIN", week.getDay(0), normalShift, 1, 1); // Lundi
    Shift lundiSoir =
        new Shift("Lundi-SOIR", week.getDay(0), normalShift, 1, 1); // Lundi (même jour)
    Shift mardiMatin = new Shift("Mardi-MATIN", week.getDay(1), normalShift, 1, 1); // Mardi
    Shift mercrediMatin =
        new Shift("Mercredi-MATIN", week.getDay(2), normalShift, 1, 1); // Mercredi

    List<Shift> testShifts = Arrays.asList(lundiMatin, lundiSoir, mardiMatin, mercrediMatin);

    // Créer le scheduler
    ShiftScheduler testScheduler = new ShiftScheduler();
    testScheduler.setEmployees(testEmployees);
    testScheduler.setShifts(testShifts);
    testScheduler.setShiftIndexMap(new HashMap<>());

    for (int i = 0; i < testShifts.size(); i++) {
      testScheduler.getShiftIndexMap().put(testShifts.get(i).id(), i);
    }

    // Construire le modèle
    testScheduler.buildModel();

    // Résoudre
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(testScheduler.getModel());

    // Vérifier qu'une solution existe
    assertTrue(
        status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE,
        "Une solution doit exister");

    // Vérifier la liaison assignments -> workingDays
    for (int e = 0; e < testEmployees.size(); e++) {
      boolean[] expectedWorkingDays = new boolean[7]; // Pour chaque jour de la semaine

      // Calculer les jours travaillés attendus basés sur les assignments
      for (int s = 0; s < testShifts.size(); s++) {
        if (solver.value(testScheduler.getAssignments()[e][s]) == 1) {
          Shift shift = testShifts.get(s);
          int dayOfWeek = shift.day().getDayNumber(); // 0-6
          expectedWorkingDays[dayOfWeek] = true;
        }
      }

      // Vérifier que workingDays correspond
      for (int d = 0; d < 7; d++) {
        boolean actualWorkingDay = solver.value(testScheduler.getWorkingDays()[e][0][d]) == 1;
        if (expectedWorkingDays[d]) {
          assertTrue(
              actualWorkingDay,
              "L'employé " + testEmployees.get(e).nom() + " devrait travailler le jour " + d);
        }
        // Note: workingDay peut être true même si expectedWorkingDays[d] est false
        // car la contrainte est workingDays[e][w][d] >= assignments[e][s]
      }

      // Vérifier workingDaysPerWeek
      int expectedWorkingDaysCount = 0;
      for (boolean working : expectedWorkingDays) {
        if (working) expectedWorkingDaysCount++;
      }

      long actualWorkingDaysCount = solver.value(testScheduler.getWorkingDaysPerWeek()[e][0]);
      assertTrue(
          actualWorkingDaysCount >= expectedWorkingDaysCount,
          "L'employé "
              + testEmployees.get(e).nom()
              + " devrait avoir au moins "
              + expectedWorkingDaysCount
              + " jours travaillés, mais en a "
              + actualWorkingDaysCount);
    }
  }

  @Test
  void addMinimumRestDaysConstraintTest() {
    // Configuration pour tester la contrainte de jours de repos minimum
    Employee emp1 = new Employee("E1", "Alice");
    List<Employee> testEmployees = Arrays.asList(emp1);

    ShiftType normalShift = new ShiftType("NORMAL", 480, 960, 480); // 8h-16h = 8h

    // Créer une semaine
    Week week = Week.create(0);

    // Créer 7 shifts (un par jour) - impossible de tous les faire avec 1 employé
    // car la contrainte impose max 6 jours travaillés
    Shift lundi = new Shift("Lundi-NORMAL", week.getDay(0), normalShift, 1, 1);
    Shift mardi = new Shift("Mardi-NORMAL", week.getDay(1), normalShift, 1, 1);
    Shift mercredi = new Shift("Mercredi-NORMAL", week.getDay(2), normalShift, 1, 1);
    Shift jeudi = new Shift("Jeudi-NORMAL", week.getDay(3), normalShift, 1, 1);
    Shift vendredi = new Shift("Vendredi-NORMAL", week.getDay(4), normalShift, 1, 1);
    Shift samedi = new Shift("Samedi-NORMAL", week.getDay(5), normalShift, 1, 1);
    Shift dimanche = new Shift("Dimanche-NORMAL", week.getDay(6), normalShift, 1, 1);

    List<Shift> testShifts =
        Arrays.asList(lundi, mardi, mercredi, jeudi, vendredi, samedi, dimanche);

    // Créer le scheduler
    ShiftScheduler testScheduler = new ShiftScheduler();
    testScheduler.setEmployees(testEmployees);
    testScheduler.setShifts(testShifts);
    testScheduler.setShiftIndexMap(new HashMap<>());

    for (int i = 0; i < testShifts.size(); i++) {
      testScheduler.getShiftIndexMap().put(testShifts.get(i).id(), i);
    }

    // Construire le modèle
    testScheduler.buildModel();

    // Résoudre
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(testScheduler.getModel());

    // Avec un seul employé et 7 shifts requis, il ne devrait pas y avoir de solution
    // car l'employé ne peut travailler que 6 jours max (contrainte de repos)
    assertEquals(
        CpSolverStatus.INFEASIBLE,
        status,
        "Le problème devrait être infaisable avec 1 employé, 7 shifts requis et contrainte de repos");

    // Test avec 2 employés - devrait être faisable
    Employee emp2 = new Employee("E2", "Bob");
    testEmployees = Arrays.asList(emp1, emp2);

    testScheduler.setEmployees(testEmployees);
    testScheduler.buildModel();

    status = solver.solve(testScheduler.getModel());

    assertTrue(
        status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE,
        "Le problème devrait être faisable avec 2 employés");

    // Vérifier que chaque employé respecte la contrainte de repos (max 6 jours travaillés)
    for (int e = 0; e < testEmployees.size(); e++) {
      long workingDays = solver.value(testScheduler.getWorkingDaysPerWeek()[e][0]);
      assertTrue(
          workingDays <= 6,
          "L'employé "
              + testEmployees.get(e).nom()
              + " ne devrait pas travailler plus de 6 jours, "
              + "mais travaille "
              + workingDays
              + " jours");
    }

    // Vérifier que tous les shifts sont couverts
    int coveredShifts = 0;
    for (int s = 0; s < testShifts.size(); s++) {
      for (int e = 0; e < testEmployees.size(); e++) {
        if (solver.value(testScheduler.getAssignments()[e][s]) == 1) {
          coveredShifts++;
          break;
        }
      }
    }
    assertEquals(testShifts.size(), coveredShifts, "Tous les shifts doivent être couverts");
  }
}
