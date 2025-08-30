package com.cricri;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.config.ParameterKey;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ConstraintType;
import com.cricri.factory.ConstraintFactory;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.model.ShiftDay;
import com.cricri.model.ShiftType;
import com.cricri.model.Week;
import com.cricri.service.SchedulingConfiguration;
import com.cricri.service.ShiftScheduler;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;

@SpringBootApplication
public class ScheduleApplication {

  private static final Logger logger = LoggerFactory.getLogger(ScheduleApplication.class);

  // Configuration du modèle
  private static final int MIN_EMPLOYES_PAR_SHIFT = 1;
  private static final int MAX_EMPLOYES_PAR_SHIFT = 2;
  private static final int MAX_HEURES_PAR_SEMAINE = 39 * 60; // 40h en minutes
  private static final LocalTime HEURE_DEBUT_MATIN = LocalTime.of(7, 0); // 7h00
  private static final LocalTime HEURE_FIN_MATIN = LocalTime.of(15, 45); // 15h45
  private static final LocalTime HEURE_DEBUT_SOIR = LocalTime.of(15, 0); // 15h00
  private static final LocalTime HEURE_FIN_SOIR = LocalTime.of(23, 45); // 23h45
  private static final Duration DUREE_PAUSE = Duration.ofMinutes(45);
  private static final String[] JOURS = {
    "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"
  };

  public static void main(String[] args) {
    SpringApplication.run(ScheduleApplication.class, args);

    // Charger les bibliothèques OR-Tools
    Loader.loadNativeLibraries();

    // Configuration
    ShiftScheduler scheduler = createTestScheduler();
    printConfiguration(scheduler);
    scheduler.printConstraints();

    // Construire le modèle
    scheduler.buildModel();
    logger.info("Modèle construit avec contraintes de couverture minimale");

    // Résoudre
    logger.info("\n=== Résolution ===");
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(scheduler.getModel());

    logger.info("Status: {}", status);

    if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
      printSolution(scheduler, solver);
    } else {
      logger.warn("❌ Aucune solution trouvée!");
    }
  }

  private static ShiftScheduler createTestScheduler() {
    // Créer des employés
    Employee alice = new Employee("E1", "Alice");
    Employee bob = new Employee("E2", "Bob");
    Employee charlie = new Employee("E3", "Charlie");
    Employee david = new Employee("E4", "David");
    Employee eva = new Employee("E5", "Eva");
    List<Employee> employees = Arrays.asList(alice, bob, charlie, david, eva);

    // Créer des types de shift (avec 45 minutes de pause)
    ShiftType matin =
        new ShiftType("MATIN", HEURE_DEBUT_MATIN, HEURE_FIN_MATIN, DUREE_PAUSE);
    ShiftType soir =
        new ShiftType("SOIR", HEURE_DEBUT_SOIR, HEURE_FIN_SOIR, DUREE_PAUSE);

    // Créer une semaine
    Week week = SchedulingConfiguration.createWeek(0);

    // Créer des shifts pour une semaine complète
    List<Shift> shifts = new java.util.ArrayList<>();

    for (int i = 0; i < JOURS.length; i++) {
      ShiftDay day = week.getDay(i);
      // Shift du matin
      shifts.add(
          new Shift(
              JOURS[i] + "-MATIN", day, matin, MIN_EMPLOYES_PAR_SHIFT, MAX_EMPLOYES_PAR_SHIFT));
      // Shift du soir
      shifts.add(
          new Shift(JOURS[i] + "-SOIR", day, soir, MIN_EMPLOYES_PAR_SHIFT, MAX_EMPLOYES_PAR_SHIFT));
    }

    // Créer le scheduler modulaire
    ShiftScheduler scheduler = new ShiftScheduler(employees, shifts);

    // Ajouter les contraintes exactement comme dans withStandardConstraints()
    List<ConstraintConfig> constraintConfigs =
        Arrays.asList(
            // 1. withMinimumCoverage() - par défaut HARD, FUNDAMENTAL
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD),

            // 2. withAssignmentHours(minHoursPerShift) - par défaut HARD, CONSISTENCY
            ConstraintConfig.of(
                ConstraintType.ASSIGNMENT_HOURS,
                ConstraintNature.HARD,
                ParameterKey.MIN_HOURS_PER_SHIFT.getKeyName(),
                5 * 60), // 5h minimum par shift

            // 3. withMaxHoursPerWeek(maxHoursPerWeek) - par défaut HARD, NORMAL
            ConstraintConfig.of(
                ConstraintType.MAX_HOURS_PER_WEEK,
                ConstraintNature.HARD,
                ParameterKey.MAX_HOURS_PER_WEEK.getKeyName(),
                MAX_HEURES_PAR_SEMAINE), // 39h par semaine

            // 4. withMinimumRest(minRestHours) - par défaut HARD, SAFETY
            ConstraintConfig.of(
                ConstraintType.MINIMUM_REST,
                ConstraintNature.HARD,
                ParameterKey.MIN_REST_HOURS.getKeyName(),
                11), // 11h de repos minimum

            // 5. withMinimumRestDays(1) - par défaut SOFT, COMFORT dans la classe
            ConstraintConfig.of(
                ConstraintType.MINIMUM_REST_DAYS,
                ConstraintNature.HARD,
                ParameterKey.MIN_REST_DAYS_PER_WEEK.getKeyName(),
                2), // 1 jour de repos minimum

            // 6. withMaximizeWorkingHours() - reproduit l'ancienne addWeekdayStaffingObjective()
            ConstraintConfig.of(
                ConstraintType.MAXIMIZE_WORKING_HOURS,
                ConstraintNature.SOFT,
                ParameterKey.WEEKDAY_MULTIPLIER.getKeyName(),
                2));

    // Créer et ajouter toutes les contraintes
    for (ConstraintConfig config : constraintConfigs) {
      scheduler.withConstraint(ConstraintFactory.create(config));
    }

    return scheduler;
  }

  private static void printConfiguration(ShiftScheduler scheduler) {
    logger.info("=== Configuration ===");
    logger.info("Employés : {}", scheduler.getEmployees().size());
    scheduler.getEmployees().forEach(e -> logger.info("  - {} ({})", e.nom(), e.id()));

    logger.info("\nShifts :");
    scheduler
        .getShifts()
        .forEach(
            s -> logger.info("  - {} : {}-{} employés", s.id(), s.minEmployes(), s.maxEmployes()));
  }

  private static void printSolution(ShiftScheduler scheduler, CpSolver solver) {
    List<Employee> employees = scheduler.getEmployees();
    List<Shift> shifts = scheduler.getShifts();

    logger.info("\n=== Solution trouvée ===");

    // Afficher les assignations
    for (int s = 0; s < shifts.size(); s++) {
      Shift shift = shifts.get(s);
      logger.info("\n{} (requis: {}-{}):", shift.id(), shift.minEmployes(), shift.maxEmployes());

      int assignedCount = 0;
      for (int e = 0; e < employees.size(); e++) {
        if (solver.value(scheduler.getAssignments()[e][s]) == 1) {
          long actualMinutes = solver.value(scheduler.getActualHours()[e][s]);
          double actualHours = actualMinutes / 60.0;

          // Calculer les heures de début et fin réelles
          String startEndTime = calculateWorkingHours(shift, actualMinutes);

          logger.info(
              "  [OK] {} ({}h effective / {}h présence) - {}",
              employees.get(e).nom(),
              actualHours,
              shift.type().dureeMinutes() / 60.0,
              startEndTime);

          assignedCount++;
        }
      }

      logger.info("  Total assignés: {}", assignedCount);

      // Vérifier la contrainte
      if (assignedCount >= shift.minEmployes() && assignedCount <= shift.maxEmployes()) {
        logger.info("  [OK] Contrainte respectée");
      } else {
        logger.error("  [ERREUR] Contrainte violée!");
      }
    }

    // Afficher les heures par employé par semaine
    printHoursPerEmployee(scheduler, solver, employees, shifts);

    // Afficher les jours de repos par employé
    printRestDaysPerEmployee(scheduler, solver, employees);
  }

  private static void printHoursPerEmployee(
      ShiftScheduler scheduler, CpSolver solver, List<Employee> employees, List<Shift> shifts) {
    logger.info("\n=== Heures par employé par semaine ===");

    // Calculer le nombre de semaines
    int nbWeeks =
        shifts.stream().mapToInt(shift -> shift.day().getWeekNumber()).max().orElse(0) + 1;

    for (int e = 0; e < employees.size(); e++) {
      Employee employee = employees.get(e);
      logger.info("\n{} ({}):", employee.nom(), employee.id());

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
        String weekInfo = String.format("  Semaine %d: %.1fh", (w + 1), weekHoursDouble);

        // Vérifier si la limite est dépassée
        if (weekHours > MAX_HEURES_PAR_SEMAINE) {
          logger.error(weekInfo + " [ERREUR - Limite dépassée!]");
        } else if (weekHours > 0) {
          logger.info(weekInfo + " [OK]");
        } else {
          logger.info(weekInfo);
        }

        totalHours += weekHours;
      }

      double totalHoursDouble = totalHours / 60.0;
      logger.info("  TOTAL: {}h", totalHoursDouble);
    }
  }

  private static void printRestDaysPerEmployee(
      ShiftScheduler scheduler, CpSolver solver, List<Employee> employees) {
    logger.info("\n=== Jours de repos par employé par semaine ===");

    int nbWeeks = scheduler.getWorkingDaysPerWeek()[0].length;

    for (int e = 0; e < employees.size(); e++) {
      Employee employee = employees.get(e);
      logger.info("\n{} ({}):", employee.nom(), employee.id());

      for (int w = 0; w < nbWeeks; w++) {
        long workingDays = solver.value(scheduler.getWorkingDaysPerWeek()[e][w]);
        long restDays = 7 - workingDays;

        StringBuilder restDaysInfo = new StringBuilder();
        restDaysInfo.append(String.format("  Semaine %d: %d jours de repos", (w + 1), restDays));

        // Afficher les jours de repos spécifiques
        restDaysInfo.append(" (");
        boolean first = true;
        for (int d = 0; d < 7; d++) {
          if (solver.value(scheduler.getWorkingDays()[e][w][d]) == 0) {
            if (!first) restDaysInfo.append(", ");
            restDaysInfo.append(JOURS[d]);
            first = false;
          }
        }
        restDaysInfo.append(")");

        // Vérifier si la contrainte de repos est respectée
        if (restDays >= 1) {
          logger.info(restDaysInfo + " [OK]");
        } else {
          logger.error(restDaysInfo + " [ERREUR - Pas assez de repos!]");
        }
      }
    }
  }

  private static String calculateWorkingHours(Shift shift, long actualMinutes) {
    int startMinutes = shift.type().heureDebut().toSecondOfDay() / 60;
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
