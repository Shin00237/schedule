package com.cricri.service;

import static org.junit.jupiter.api.Assertions.*;

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

    // Créer des shifts avec conflit de repos (< 11h entre eux)
    ShiftType soir = new ShiftType("SOIR", 1320, 1440, 120); // 22h-24h = 2h
    ShiftType matin = new ShiftType("MATIN", 420, 540, 120); // 7h-9h = 2h

    // Shift du soir jour 1 (finit à 24h) + shift matin jour 2 (commence à 7h) = 7h de repos
    // seulement
    Shift soirJ1 = new Shift("Jour1-SOIR", week.getDay(0), soir, 1, 1); // Lundi
    Shift matinJ2 = new Shift("Jour2-MATIN", week.getDay(1), matin, 1, 1); // Mardi

    // Shift sans conflit pour comparaison
    Shift matinJ3 =
        new Shift("Jour3-MATIN", week.getDay(2), matin, 1, 1); // Mercredi, 48h après le soir J1, OK

    List<Shift> testShifts = Arrays.asList(soirJ1, matinJ2, matinJ3);

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
      boolean assignedToSoirJ1 = solver.value(testScheduler.getAssignments()[e][0]) == 1;
      boolean assignedToMatinJ2 = solver.value(testScheduler.getAssignments()[e][1]) == 1;

      // Un même employé ne peut pas faire à la fois le soir J1 ET le matin J2 (< 11h de repos)
      assertFalse(
          assignedToSoirJ1 && assignedToMatinJ2,
          "L'employé "
              + testEmployees.get(e).nom()
              + " ne peut pas faire le shift soir J1 ET matin J2 (repos insuffisant)");
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
