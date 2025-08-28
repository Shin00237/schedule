package com.cricri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.DayOfWeek;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.cricri.model.Week;
import com.cricri.testutils.TestDataFactory;

/**
 * Tests unitaires pour SchedulingConfiguration.
 *
 * <p>Valide les différentes configurations de cycles de travail et leur impact sur la création des
 * semaines et jours.
 */
class SchedulingConfigurationTest {

  @Test
  void standardWeekConfigurationTest() {
    SchedulingConfiguration config = SchedulingConfiguration.STANDARD_WEEK;

    assertEquals(7, config.getDaysPerCycle());
    assertEquals(DayOfWeek.MONDAY, config.getDayOfWeek(0));
    assertEquals(DayOfWeek.SUNDAY, config.getDayOfWeek(6));
    assertEquals("Semaine standard (7j)", config.getDisplayName());

    assertTrue(config.isWeekend(5)); // Samedi
    assertTrue(config.isWeekend(6)); // Dimanche
    assertFalse(config.isWeekend(0)); // Lundi
    assertFalse(config.isWeekend(4)); // Vendredi
  }

  @Test
  void weekdaysOnlyConfigurationTest() {
    SchedulingConfiguration config = SchedulingConfiguration.WEEKDAYS_ONLY;

    assertEquals(5, config.getDaysPerCycle());
    assertEquals(DayOfWeek.MONDAY, config.getDayOfWeek(0));
    assertEquals(DayOfWeek.FRIDAY, config.getDayOfWeek(4));
    assertEquals("Jours ouvrés seulement (5j)", config.getDisplayName());

    // Aucun weekend dans cette configuration
    for (int i = 0; i < 5; i++) {
      assertFalse(config.isWeekend(i), "Jour " + i + " ne devrait pas être weekend");
      assertTrue(config.isWeekday(i), "Jour " + i + " devrait être jour ouvré");
    }
  }

  @Test
  void sixDaysWeekConfigurationTest() {
    SchedulingConfiguration config = SchedulingConfiguration.SIX_DAYS_WEEK;

    assertEquals(6, config.getDaysPerCycle());
    assertEquals(DayOfWeek.SATURDAY, config.getDayOfWeek(5));

    assertTrue(config.isWeekend(5)); // Samedi
    assertFalse(config.isWeekend(0)); // Lundi
  }

  @Test
  void customConfigurationTest() {
    // Configuration personnalisée : 3 jours seulement
    List<DayOfWeek> customPattern =
        List.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY);
    SchedulingConfiguration config = new SchedulingConfiguration(3, customPattern, "Test 3 jours");

    assertEquals(3, config.getDaysPerCycle());
    assertEquals(DayOfWeek.TUESDAY, config.getDayOfWeek(0));
    assertEquals(DayOfWeek.THURSDAY, config.getDayOfWeek(1));
    assertEquals(DayOfWeek.SATURDAY, config.getDayOfWeek(2));

    assertTrue(config.isWeekend(2)); // Samedi
    assertFalse(config.isWeekend(0)); // Mardi
    assertFalse(config.isWeekend(1)); // Jeudi
  }

  @Test
  void configurationValidationTest() {
    // Tester les validations du constructeur
    assertThrows(
        IllegalArgumentException.class, () -> new SchedulingConfiguration(0, List.of(), "Invalid"));

    assertThrows(
        IllegalArgumentException.class,
        () -> new SchedulingConfiguration(-1, List.of(DayOfWeek.MONDAY), "Invalid"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SchedulingConfiguration(
                2, List.of(DayOfWeek.MONDAY), "Mismatch")); // 2 jours attendus, 1 fourni
  }

  @Test
  void getDayOfWeekValidationTest() {
    SchedulingConfiguration config = SchedulingConfiguration.WEEKDAYS_ONLY;

    assertThrows(IllegalArgumentException.class, () -> config.getDayOfWeek(-1));
    assertThrows(IllegalArgumentException.class, () -> config.getDayOfWeek(5)); // Index hors limite
  }

  @Test
  void weekCreationWithDifferentConfigurationsTest() {
    // Tester création de semaines avec différentes configurations
    Week standardWeek = Week.create(0, SchedulingConfiguration.STANDARD_WEEK);
    assertEquals(7, standardWeek.days().size());

    Week weekdaysWeek = Week.create(0, SchedulingConfiguration.WEEKDAYS_ONLY);
    assertEquals(5, weekdaysWeek.days().size());

    // Vérifier que les jours correspondent à la configuration
    assertEquals(DayOfWeek.MONDAY, weekdaysWeek.days().get(0).dayOfWeek());
    assertEquals(DayOfWeek.FRIDAY, weekdaysWeek.days().get(4).dayOfWeek());
  }

  @Test
  void shiftCreationWithWeekdaysOnlyTest() {
    // Tester création de shifts avec configuration 5 jours
    Week weekdaysWeek = Week.create(0, SchedulingConfiguration.WEEKDAYS_ONLY);
    var employees = TestDataFactory.createEmployees(3);
    var shifts = TestDataFactory.createWeekShifts(weekdaysWeek, 1, 2);

    // Devrait créer 5 shifts seulement (pas 7)
    assertEquals(5, shifts.size());

    // Vérifier que tous les shifts sont sur des jours ouvrés
    shifts.forEach(
        shift -> {
          int dayInWeek = shift.jourDansLaSemaine();
          assertTrue(
              dayInWeek >= 0 && dayInWeek < 5,
              "Shift devrait être sur jour ouvré (0-4), trouvé: " + dayInWeek);
        });

    // Vérifier que le contexte fonctionne avec cette configuration
    var context =
        TestDataFactory.createContext(employees, shifts, SchedulingConfiguration.WEEKDAYS_ONLY);
    assertEquals(SchedulingConfiguration.WEEKDAYS_ONLY, context.getConfig());

    context.ensureVariablesInitialized();
    // Les variables workingDays devraient avoir 5 colonnes (pas 7)
    assertEquals(5, context.getWorkingDays()[0][0].length);
  }

  @Test
  void toStringRepresentationTest() {
    assertEquals(
        "Semaine standard (7j) (7 jours)", SchedulingConfiguration.STANDARD_WEEK.toString());
    assertEquals(
        "Jours ouvrés seulement (5j) (5 jours)", SchedulingConfiguration.WEEKDAYS_ONLY.toString());
  }
}
