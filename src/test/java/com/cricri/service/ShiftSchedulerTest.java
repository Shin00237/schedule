package com.cricri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.model.ShiftType;
import com.cricri.model.Week;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;

class ShiftSchedulerTest {

  // Éléments communs
  private ShiftScheduler scheduler;
  private List<Employee> baseEmployees;
  private Week baseWeek;
  private Week baseWeek2;
  private ShiftType normalShift;

  // Shifts communs réutilisables
  private List<Shift> weekShifts; // Semaine complète (lundi-dimanche)
  private List<Shift> twoWeekShifts; // 2 semaines pour tests d'heures
  private List<Shift> conflictShifts; // Shifts avec conflits pour tests de repos

  // Pour compatibilité avec tests existants
  private List<Employee> employees;
  private List<Shift> shifts;

  @BeforeEach
  void setUp() {
    Loader.loadNativeLibraries();

    // Employés de base (réutilisables)
    Employee alice = new Employee("E1", "Alice");
    Employee bob = new Employee("E2", "Bob");
    Employee charlie = new Employee("E3", "Charlie");
    Employee david = new Employee("E4", "David");
    baseEmployees = Arrays.asList(alice, bob, charlie, david);

    // Types de shifts courants
    normalShift = new ShiftType("NORMAL", 480, 960, 480); // 8h-16h = 8h

    // Semaines de base
    baseWeek = Week.create(0);
    baseWeek2 = Week.create(1);

    // Créer les shifts communs réutilisables
    createCommonShifts();

    // Setup par défaut pour compatibilité
    employees = Arrays.asList(alice, bob);
    shifts = Arrays.asList(weekShifts.get(0), weekShifts.get(1)); // Lundi et mardi

    // Scheduler par défaut
    scheduler = createScheduler(employees, shifts);
  }

  // Méthode utilitaire pour créer un scheduler
  private ShiftScheduler createScheduler(List<Employee> employees, List<Shift> shifts) {
    ShiftScheduler scheduler = new ShiftScheduler();
    scheduler.setEmployees(employees);
    scheduler.setShifts(shifts);
    scheduler.setShiftIndexMap(new HashMap<>());

    for (int i = 0; i < shifts.size(); i++) {
      scheduler.getShiftIndexMap().put(shifts.get(i).id(), i);
    }

    return scheduler;
  }

  private void createCommonShifts() {
    // Semaine complète (7 jours) - pour tests d'optimisation weekday/weekend
    weekShifts = Arrays.asList(
        new Shift("Lundi-NORMAL", baseWeek.getDay(0), normalShift, 1, 2),
        new Shift("Mardi-NORMAL", baseWeek.getDay(1), normalShift, 1, 2),
        new Shift("Mercredi-NORMAL", baseWeek.getDay(2), normalShift, 1, 2),
        new Shift("Jeudi-NORMAL", baseWeek.getDay(3), normalShift, 1, 2),
        new Shift("Vendredi-NORMAL", baseWeek.getDay(4), normalShift, 1, 2),
        new Shift("Samedi-NORMAL", baseWeek.getDay(5), normalShift, 1, 2),
        new Shift("Dimanche-NORMAL", baseWeek.getDay(6), normalShift, 1, 2)
    );

    // Shifts sur 2 semaines - pour tests de contraintes d'heures
    twoWeekShifts = Arrays.asList(
        new Shift("Lundi1-NORMAL", baseWeek.getDay(0), normalShift, 1, 1),
        new Shift("Mardi1-NORMAL", baseWeek.getDay(1), normalShift, 1, 1),
        new Shift("Mercredi1-NORMAL", baseWeek.getDay(2), normalShift, 1, 1),
        new Shift("Jeudi1-NORMAL", baseWeek.getDay(3), normalShift, 1, 1),
        new Shift("Vendredi1-NORMAL", baseWeek.getDay(4), normalShift, 1, 1),
        new Shift("Lundi2-NORMAL", baseWeek2.getDay(0), normalShift, 1, 1),
        new Shift("Mardi2-NORMAL", baseWeek2.getDay(1), normalShift, 1, 1)
    );

    // Shifts avec conflits - pour tests de repos minimum
    ShiftType matinLong = new ShiftType("MATIN_LONG", 420, 945, 525); // 7h00-15h45
    ShiftType soirLong = new ShiftType("SOIR_LONG", 900, 1425, 525); // 15h00-23h45
    conflictShifts = Arrays.asList(
        new Shift("Vendredi-MATIN", baseWeek.getDay(4), matinLong, 1, 1),
        new Shift("Vendredi-SOIR", baseWeek.getDay(4), soirLong, 1, 1), // Conflit avec matin
        new Shift("Samedi-MATIN", baseWeek.getDay(5), matinLong, 1, 1) // Pas de conflit
    );
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
    // Utiliser les employés de base et les shifts prédéfinis
    List<Employee> testEmployees = baseEmployees.subList(0, 2);

    // Créer un scheduler avec limite réaliste
    ShiftScheduler testScheduler = createScheduler(testEmployees, twoWeekShifts);
    testScheduler.setMaxHoursPerWeek(40 * 60); // Limite: 40h par semaine (5 shifts max)

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
      for (int s = 0; s < twoWeekShifts.size(); s++) {
        if (solver.value(testScheduler.getAssignments()[e][s]) == 1) {
          Shift shift = twoWeekShifts.get(s);
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
    for (int s = 0; s < twoWeekShifts.size(); s++) {
      for (int e = 0; e < 2; e++) {
        if (solver.value(testScheduler.getAssignments()[e][s]) == 1) {
          coveredShifts++;
          break; // Un seul employé par shift suffit
        }
      }
    }
    assertEquals(twoWeekShifts.size(), coveredShifts, "Tous les shifts doivent être couverts");
  }

  @Test
  void addMinimumRestConstraintTest() {
    // Utiliser les employés de base et les shifts avec conflits
    List<Employee> testEmployees = baseEmployees.subList(0, 2);

    // Créer le scheduler
    ShiftScheduler testScheduler = createScheduler(testEmployees, conflictShifts);
    testScheduler.setMinRestHours(11); // 11h de repos minimum

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
    for (int s = 0; s < conflictShifts.size(); s++) {
      for (int e = 0; e < testEmployees.size(); e++) {
        if (solver.value(testScheduler.getAssignments()[e][s]) == 1) {
          coveredShifts++;
          break;
        }
      }
    }
    assertEquals(conflictShifts.size(), coveredShifts, "Tous les shifts doivent être couverts");
  }

  @Test
  void addWorkingDaysConstraintsTest() {
    // Utiliser les employés de base
    List<Employee> testEmployees = baseEmployees.subList(0, 2);

    // Créer des shifts sur différents jours de la semaine
    Shift lundiMatin = new Shift("Lundi-MATIN", baseWeek.getDay(0), normalShift, 1, 1);
    Shift lundiSoir = new Shift("Lundi-SOIR", baseWeek.getDay(0), normalShift, 1, 1);
    Shift mardiMatin = new Shift("Mardi-MATIN", baseWeek.getDay(1), normalShift, 1, 1);
    Shift mercrediMatin = new Shift("Mercredi-MATIN", baseWeek.getDay(2), normalShift, 1, 1);

    List<Shift> testShifts = Arrays.asList(lundiMatin, lundiSoir, mardiMatin, mercrediMatin);

    // Créer le scheduler
    ShiftScheduler testScheduler = createScheduler(testEmployees, testShifts);

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
    // Un seul employé pour le premier test
    List<Employee> testEmployees = baseEmployees.subList(0, 1);

    // Utiliser les shifts de la semaine complète (7 jours)
    // Modifier les contraintes pour le test (1 employé minimum au lieu de 2)
    List<Shift> testShifts = weekShifts.stream()
        .map(s -> new Shift(s.id(), s.day(), s.type(), 1, 1)) // min=1, max=1
        .toList();

    // Créer le scheduler
    ShiftScheduler testScheduler = createScheduler(testEmployees, testShifts);

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
    testEmployees = baseEmployees.subList(0, 2);

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

  @Test
  void testWeekdayStaffingObjective() {
    // Utiliser tous les employés de base pour l'optimisation
    List<Employee> testEmployees = baseEmployees;

    // Utiliser les shifts de la semaine complète (déjà configurés avec min=1, max=2)
    // Créer le scheduler
    ShiftScheduler testScheduler = createScheduler(testEmployees, weekShifts);
    testScheduler.setMaxHoursPerWeek(40 * 60); // 40h par semaine

    // Construire le modèle
    testScheduler.buildModel();

    // Résoudre
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(testScheduler.getModel());

    // Vérifier qu'une solution existe
    assertTrue(status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE,
              "Une solution doit exister");

    // Analyser la solution
    int weekdayEmployees = 0;
    int weekendEmployees = 0;
    int weekdayShiftsWithTwoOrMore = 0;
    int weekendShiftsWithTwoOrMore = 0;

    for (int s = 0; s < weekShifts.size(); s++) {
        Shift shift = weekShifts.get(s);
        int employeesAssigned = 0;

        for (int e = 0; e < testEmployees.size(); e++) {
            if (solver.value(testScheduler.getAssignments()[e][s]) == 1) {
                employeesAssigned++;
            }
        }

        if (shift.day().isWeekend()) {
            weekendEmployees += employeesAssigned;
            if (employeesAssigned >= 2) weekendShiftsWithTwoOrMore++;
        } else {
            weekdayEmployees += employeesAssigned;
            if (employeesAssigned >= 2) weekdayShiftsWithTwoOrMore++;
        }

        // Vérifier que les contraintes minimales sont respectées
        assertTrue(employeesAssigned >= shift.minEmployes(),
                  "Le shift " + shift.id() + " doit avoir au moins " + shift.minEmployes() + " employé(s)");
    }

    // Vérifier que l'optimisation privilégie bien les jours en semaine
    // Avec 4 employés et des limites d'heures, on devrait avoir plus d'employés en semaine
    System.out.println("Employés en semaine: " + weekdayEmployees + ", weekend: " + weekendEmployees);
    System.out.println("Shifts avec 2+ employés - semaine: " + weekdayShiftsWithTwoOrMore + ", weekend: " + weekendShiftsWithTwoOrMore);

    // L'objectif devrait favoriser plus d'employés en semaine qu'en weekend
    assertTrue(weekdayEmployees >= weekendEmployees,
              "L'optimisation devrait privilégier les jours en semaine");
  }
}
