package com.cricri.testutils;

import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ConstraintType;
import com.cricri.factory.ConstraintFactory;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.model.ShiftType;
import com.cricri.model.Week;
import com.cricri.service.ModularShiftScheduler;
import com.cricri.service.SchedulingConfiguration;
import com.cricri.service.SchedulingContext;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Factory pour créer des données de test standardisées et réutilisables.
 *
 * <p>Cette classe centralise la création d'objets de test pour éviter la duplication et assurer la
 * cohérence entre les tests.
 */
public class TestDataFactory {

  // Types de shifts standards
  public static final ShiftType NORMAL_SHIFT =
      new ShiftType("NORMAL", 480, 960, 480, 45); // 8h-16h, 8h effectives, 45min pause

  public static final ShiftType MORNING_SHIFT =
      new ShiftType("MATIN", 420, 945, 525, 45); // 7h-15h45, 8h45 effectives

  public static final ShiftType EVENING_SHIFT =
      new ShiftType("SOIR", 900, 1425, 525, 45); // 15h-23h45, 8h45 effectives

  public static final ShiftType NIGHT_SHIFT =
      new ShiftType("NUIT", 1350, 420, 510, 30); // 22h30-7h, 8h30 effectives

  // Employés standards
  private static final String[] EMPLOYEE_NAMES = {
    "Alice", "Bob", "Charlie", "David", "Eve", "Frank", "Grace", "Henry"
  };

  /**
   * Crée une liste d'employés avec des noms standards.
   *
   * @param count Nombre d'employés à créer (max 8)
   * @return Liste d'employés avec IDs "E1", "E2", etc.
   */
  public static List<Employee> createEmployees(int count) {
    if (count <= 0 || count > EMPLOYEE_NAMES.length) {
      throw new IllegalArgumentException(
          "Le nombre d'employés doit être entre 1 et " + EMPLOYEE_NAMES.length);
    }

    return IntStream.range(0, count)
        .mapToObj(i -> new Employee("E" + (i + 1), EMPLOYEE_NAMES[i]))
        .toList();
  }

  /**
   * Crée des employés de test standard (Alice et Bob).
   *
   * @return Liste de 2 employés
   */
  public static List<Employee> createStandardEmployees() {
    return createEmployees(2);
  }

  /**
   * Crée une semaine complète de shifts normaux (lundi à dimanche).
   *
   * @param week La semaine de référence
   * @param minEmployees Nombre minimum d'employés par shift
   * @param maxEmployees Nombre maximum d'employés par shift
   * @return Liste de 7 shifts (un par jour)
   */
  public static List<Shift> createWeekShifts(Week week, int minEmployees, int maxEmployees) {
    String[] dayNames = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"};
    int daysInWeek = week.config().getDaysPerCycle();

    return IntStream.range(0, daysInWeek)
        .mapToObj(
            dayIndex ->
                new Shift(
                    dayNames[dayIndex % dayNames.length] + "-NORMAL",
                    week.getDay(dayIndex),
                    NORMAL_SHIFT,
                    minEmployees,
                    maxEmployees))
        .toList();
  }

  /**
   * Crée une semaine complète de shifts normaux avec 1-2 employés par shift.
   *
   * @param week La semaine de référence
   * @return Liste de 7 shifts
   */
  public static List<Shift> createStandardWeekShifts(Week week) {
    return createWeekShifts(week, 1, 2);
  }

  /**
   * Crée des shifts sur deux semaines pour tester les contraintes d'heures hebdomadaires.
   *
   * @param week1 Première semaine
   * @param week2 Deuxième semaine
   * @return Liste de shifts répartis sur 2 semaines
   */
  public static List<Shift> createTwoWeekShifts(Week week1, Week week2) {
    return Arrays.asList(
        // Semaine 1 : lundi à vendredi
        new Shift("Lundi1-NORMAL", week1.getDay(0), NORMAL_SHIFT, 1, 1),
        new Shift("Mardi1-NORMAL", week1.getDay(1), NORMAL_SHIFT, 1, 1),
        new Shift("Mercredi1-NORMAL", week1.getDay(2), NORMAL_SHIFT, 1, 1),
        new Shift("Jeudi1-NORMAL", week1.getDay(3), NORMAL_SHIFT, 1, 1),
        new Shift("Vendredi1-NORMAL", week1.getDay(4), NORMAL_SHIFT, 1, 1),
        // Semaine 2 : lundi à mardi
        new Shift("Lundi2-NORMAL", week2.getDay(0), NORMAL_SHIFT, 1, 1),
        new Shift("Mardi2-NORMAL", week2.getDay(1), NORMAL_SHIFT, 1, 1));
  }

  /**
   * Crée des shifts avec potentiels conflits de temps de repos.
   *
   * @param week La semaine de référence
   * @return Liste de shifts avec chevauchements
   */
  public static List<Shift> createConflictingShifts(Week week) {
    return Arrays.asList(
        // Vendredi matin et soir : chevauchement de 15h à 15h45
        new Shift("Vendredi-MATIN", week.getDay(4), MORNING_SHIFT, 1, 1),
        new Shift("Vendredi-SOIR", week.getDay(4), EVENING_SHIFT, 1, 1),
        // Samedi matin : pas de conflit
        new Shift("Samedi-MATIN", week.getDay(5), MORNING_SHIFT, 1, 1));
  }

  /**
   * Crée des shifts de différents types pour tester la variété.
   *
   * @param week La semaine de référence
   * @return Liste de shifts variés
   */
  public static List<Shift> createMixedShifts(Week week) {
    return Arrays.asList(
        new Shift("Lundi-MATIN", week.getDay(0), MORNING_SHIFT, 1, 2),
        new Shift("Lundi-SOIR", week.getDay(0), EVENING_SHIFT, 1, 1),
        new Shift("Mardi-NORMAL", week.getDay(1), NORMAL_SHIFT, 2, 3),
        new Shift("Mercredi-NUIT", week.getDay(2), NIGHT_SHIFT, 1, 1));
  }

  /**
   * Crée un contexte de planification avec des données standard.
   *
   * @param employees Liste des employés
   * @param shifts Liste des shifts
   * @return Contexte prêt à utiliser
   */
  public static SchedulingContext createContext(List<Employee> employees, List<Shift> shifts) {
    return createContext(employees, shifts, SchedulingConfiguration.STANDARD_WEEK);
  }

  /**
   * Crée un contexte de planification avec configuration spécifique.
   *
   * @param employees Liste des employés
   * @param shifts Liste des shifts
   * @param config Configuration temporelle
   * @return Contexte prêt à utiliser
   */
  public static SchedulingContext createContext(
      List<Employee> employees, List<Shift> shifts, SchedulingConfiguration config) {
    Map<String, Integer> shiftIndexMap = new HashMap<>();
    for (int i = 0; i < shifts.size(); i++) {
      shiftIndexMap.put(shifts.get(i).id(), i);
    }
    return new SchedulingContext(employees, shifts, shiftIndexMap, config);
  }

  /**
   * Crée un scheduler modulaire avec configuration standard.
   *
   * @param employees Liste des employés
   * @param shifts Liste des shifts
   * @return Scheduler avec contraintes standard
   */
  public static ModularShiftScheduler createStandardScheduler(
      List<Employee> employees, List<Shift> shifts) {
    return createStandardSchedulerWithConfig(employees, shifts, 40 * 60, 11, 5 * 60);
  }

  /**
   * Crée un scheduler avec contraintes standard configurables. Remplace l'ancienne méthode
   * withStandardConstraints().
   */
  public static ModularShiftScheduler createStandardSchedulerWithConfig(
      List<Employee> employees,
      List<Shift> shifts,
      int maxHoursPerWeek,
      int minRestHours,
      int minHoursPerShift) {

    ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts);

    // Reproduire exactement la logique de withStandardConstraints()
    List<ConstraintConfig> constraintConfigs =
        Arrays.asList(
            ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD),
            ConstraintConfig.of(
                ConstraintType.ASSIGNMENT_HOURS,
                ConstraintNature.HARD,
                "minHoursPerShift",
                minHoursPerShift),
            ConstraintConfig.of(
                ConstraintType.MAX_HOURS_PER_WEEK,
                ConstraintNature.HARD,
                "maxHoursPerWeek",
                maxHoursPerWeek),
            ConstraintConfig.of(
                ConstraintType.MINIMUM_REST, ConstraintNature.HARD, "minRestHours", minRestHours),
            ConstraintConfig.of(
                ConstraintType.MINIMUM_REST_DAYS, ConstraintNature.SOFT, "minRestDaysPerWeek", 1));

    for (ConstraintConfig config : constraintConfigs) {
      scheduler.withConstraint(ConstraintFactory.create(config));
    }

    return scheduler;
  }

  /**
   * Crée un scheduler avec uniquement la contrainte de couverture minimum. Remplace l'ancienne
   * méthode withMinimumCoverage().
   */
  public static ModularShiftScheduler createMinimumCoverageScheduler(
      List<Employee> employees, List<Shift> shifts) {

    ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts);
    ConstraintConfig config =
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD);
    scheduler.withConstraint(ConstraintFactory.create(config));
    return scheduler;
  }

  /**
   * Crée un scheduler modulaire vide (sans contraintes).
   *
   * @param employees Liste des employés
   * @param shifts Liste des shifts
   * @return Scheduler sans contraintes
   */
  public static ModularShiftScheduler createEmptyScheduler(
      List<Employee> employees, List<Shift> shifts) {
    return new ModularShiftScheduler(employees, shifts);
  }

  /**
   * Crée des semaines de test standard.
   *
   * @return Array de 2 semaines consécutives
   */
  public static Week[] createStandardWeeks() {
    return new Week[] {Week.create(0), Week.create(1)};
  }

  /**
   * Crée un scénario de test complet avec employés et shifts standard.
   *
   * @return Scheduler prêt avec données de test cohérentes
   */
  public static ModularShiftScheduler createCompleteTestScenario() {
    List<Employee> employees = createStandardEmployees();
    Week[] weeks = createStandardWeeks();
    List<Shift> shifts = createStandardWeekShifts(weeks[0]);

    return createStandardScheduler(employees, shifts);
  }
}
