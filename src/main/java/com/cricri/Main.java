package com.cricri;

import java.util.Arrays;
import java.util.List;
import com.cricri.model.Day;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.model.ShiftType;
import com.cricri.model.Week;
import com.cricri.service.ModularShiftScheduler;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;

public class Main {

  // Configuration du modèle
  private static final int NB_EMPLOYES = 5;
  private static final int MIN_EMPLOYES_PAR_SHIFT = 1;
  private static final int MAX_EMPLOYES_PAR_SHIFT = 2;
  private static final int MAX_HEURES_PAR_SEMAINE = 39 * 60; // 40h en minutes
  private static final int HEURE_DEBUT_MATIN = 420; // 7h00
  private static final int HEURE_FIN_MATIN = 945; // 15h45
  private static final int DUREE_SHIFT_MATIN = 525; // 8h45
  private static final int HEURE_DEBUT_SOIR = 900; // 15h00
  private static final int HEURE_FIN_SOIR = 1425; // 23h45
  private static final int DUREE_SHIFT_SOIR = 525; // 8h45
  private static final int DUREE_PAUSE = 45; // 4h en minutes
  private static final String[] JOURS = {
    "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"
  };

  public static void main(String[] args) {
    // Charger les bibliothèques OR-Tools
    Loader.loadNativeLibraries();

    // Configuration
    ModularShiftScheduler scheduler = createTestScheduler();
    printConfiguration(scheduler);
    scheduler.printConstraints();
    scheduler.printObjectives();

    // Construire le modèle
    scheduler.buildModel();
    System.out.println("Modèle construit avec contraintes de couverture minimale");

    // Résoudre
    System.out.println("\n=== Résolution ===");
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(scheduler.getModel());

    System.out.println("Status: " + status);

    if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
      printSolution(scheduler, solver);
    } else {
      System.out.println("❌ Aucune solution trouvée!");
    }
  }

  private static ModularShiftScheduler createTestScheduler() {
    // Créer des employés
    Employee alice = new Employee("E1", "Alice");
    Employee bob = new Employee("E2", "Bob");
    Employee charlie = new Employee("E3", "Charlie");
    Employee david = new Employee("E4", "David");
    Employee eva = new Employee("E5", "Eva");
    List<Employee> employees = Arrays.asList(alice, bob, charlie, david, eva);

    // Créer des types de shift (avec 45 minutes de pause)
    ShiftType matin = new ShiftType("MATIN", HEURE_DEBUT_MATIN, HEURE_FIN_MATIN, DUREE_SHIFT_MATIN, DUREE_PAUSE);
    ShiftType soir = new ShiftType("SOIR", HEURE_DEBUT_SOIR, HEURE_FIN_SOIR, DUREE_SHIFT_SOIR, DUREE_PAUSE);

    // Créer une semaine
    Week week = Week.create(0);

    // Créer des shifts pour une semaine complète
    List<Shift> shifts = new java.util.ArrayList<>();

    for (int i = 0; i < JOURS.length; i++) {
      Day day = week.getDay(i);
      // Shift du matin
      shifts.add(
          new Shift(
              JOURS[i] + "-MATIN", day, matin, MIN_EMPLOYES_PAR_SHIFT, MAX_EMPLOYES_PAR_SHIFT));
      // Shift du soir
      shifts.add(
          new Shift(JOURS[i] + "-SOIR", day, soir, MIN_EMPLOYES_PAR_SHIFT, MAX_EMPLOYES_PAR_SHIFT));
    }

    // Créer le scheduler modulaire avec contraintes standard
    ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts)
        .withStandardConstraints(
            MAX_HEURES_PAR_SEMAINE, // 39h par semaine
            11, // 11h de repos minimum
            5 * 60); // 5h minimum par shift

    return scheduler;
  }

  private static void printConfiguration(ModularShiftScheduler scheduler) {
    System.out.println("=== Configuration ===");
    System.out.println("Employés : " + scheduler.getEmployees().size());
    scheduler
        .getEmployees()
        .forEach(e -> System.out.println("  - " + e.nom() + " (" + e.id() + ")"));

    System.out.println("\nShifts :");
    scheduler
        .getShifts()
        .forEach(
            s ->
                System.out.println(
                    "  - "
                        + s.id()
                        + " : "
                        + s.minEmployes()
                        + "-"
                        + s.maxEmployes()
                        + " employés"));
  }

  private static void printSolution(ModularShiftScheduler scheduler, CpSolver solver) {
    List<Employee> employees = scheduler.getEmployees();
    List<Shift> shifts = scheduler.getShifts();

    System.out.println("\n=== Solution trouvée ===");

    // Afficher les assignations
    for (int s = 0; s < shifts.size(); s++) {
      Shift shift = shifts.get(s);
      System.out.println(
          "\n"
              + shift.id()
              + " (requis: "
              + shift.minEmployes()
              + "-"
              + shift.maxEmployes()
              + "):");

      int assignedCount = 0;
      for (int e = 0; e < employees.size(); e++) {
        if (solver.value(scheduler.getAssignments()[e][s]) == 1) {
          long actualMinutes = solver.value(scheduler.getActualHours()[e][s]);
          double actualHours = actualMinutes / 60.0;

          // Calculer les heures de début et fin réelles
          String startEndTime = calculateWorkingHours(shift, actualMinutes);

          System.out.printf("  [OK] %s (%.1fh effective / %.1fh présence) - %s%n",
              employees.get(e).nom(),
              actualHours,
              shift.type().dureeMinutes() / 60.0,
              startEndTime);
          assignedCount++;
        }
      }

      System.out.println("  Total assignés: " + assignedCount);

      // Vérifier la contrainte
      if (assignedCount >= shift.minEmployes() && assignedCount <= shift.maxEmployes()) {
        System.out.println("  [OK] Contrainte respectée");
      } else {
        System.out.println("  [ERREUR] Contrainte violée!");
      }
    }

    // Afficher les heures par employé par semaine
    printHoursPerEmployee(scheduler, solver, employees, shifts);

    // Afficher les jours de repos par employé
    printRestDaysPerEmployee(scheduler, solver, employees);
  }

  private static void printHoursPerEmployee(
      ModularShiftScheduler scheduler, CpSolver solver, List<Employee> employees, List<Shift> shifts) {
    System.out.println("\n=== Heures par employé par semaine ===");

    // Calculer le nombre de semaines
    int nbWeeks =
        shifts.stream().mapToInt(shift -> shift.day().getWeekNumber()).max().orElse(0) + 1;

    for (int e = 0; e < employees.size(); e++) {
      Employee employee = employees.get(e);
      System.out.println("\n" + employee.nom() + " (" + employee.id() + "):");

      int totalHours = 0;

      for (int w = 0; w < nbWeeks; w++) {
        int weekHours = 0;

        // Calculer les heures pour cette semaine
        for (int s = 0; s < shifts.size(); s++) {
          Shift shift = shifts.get(s);
          int shiftWeek = shift.day().getWeekNumber();

          if (shiftWeek == w && solver.value(scheduler.getAssignments()[e][s]) == 1) {
            // Utiliser les heures réelles au lieu de la durée complète du shift
            weekHours += solver.value(scheduler.getActualHours()[e][s]);
          }
        }

        double weekHoursDouble = weekHours / 60.0;
        System.out.printf("  Semaine %d: %.1fh", (w + 1), weekHoursDouble);

        // Vérifier si la limite est dépassée
        if (weekHours > MAX_HEURES_PAR_SEMAINE) {
          System.out.print(" [ERREUR - Limite dépassée!]");
        } else if (weekHours > 0) {
          System.out.print(" [OK]");
        }
        System.out.println();

        totalHours += weekHours;
      }

      double totalHoursDouble = totalHours / 60.0;
      System.out.printf("  TOTAL: %.1fh\n", totalHoursDouble);
    }
  }

  private static void printRestDaysPerEmployee(
      ModularShiftScheduler scheduler, CpSolver solver, List<Employee> employees) {
    System.out.println("\n=== Jours de repos par employé par semaine ===");

    int nbWeeks = scheduler.getWorkingDaysPerWeek()[0].length;

    for (int e = 0; e < employees.size(); e++) {
      Employee employee = employees.get(e);
      System.out.println("\n" + employee.nom() + " (" + employee.id() + "):");

      for (int w = 0; w < nbWeeks; w++) {
        long workingDays = solver.value(scheduler.getWorkingDaysPerWeek()[e][w]);
        long restDays = 7 - workingDays;

        System.out.printf("  Semaine %d: %d jours de repos", (w + 1), restDays);

        // Afficher les jours de repos spécifiques
        System.out.print(" (");
        boolean first = true;
        for (int d = 0; d < 7; d++) {
          if (solver.value(scheduler.getWorkingDays()[e][w][d]) == 0) {
            if (!first) System.out.print(", ");
            System.out.print(JOURS[d]);
            first = false;
          }
        }
        System.out.print(")");

        // Vérifier si la contrainte de repos est respectée
        if (restDays >= 1) {
          System.out.print(" [OK]");
        } else {
          System.out.print(" [ERREUR - Pas assez de repos!]");
        }
        System.out.println();
      }
    }
  }

  private static String calculateWorkingHours(Shift shift, long actualMinutes) {
    int startMinutes = shift.type().heureDebutMinutes();
    int endMinutes = startMinutes + (int) actualMinutes;

    // Convertir en format HH:MM
    String startTime = formatTime(startMinutes);
    String endTime = formatTime(endMinutes);

    return startTime + " - " + endTime;
  }

  private static String formatTime(int minutes) {
    int hours = minutes / 60;
    int mins = minutes % 60;
    return String.format("%02d:%02d", hours, mins);
  }
}
