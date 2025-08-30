package com.cricri.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.DayOfWeek;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires pour SchedulingConfiguration.
 *
 * <p>Valide les différentes configurations de cycles de travail et leur impact sur la création des
 * semaines et jours.
 */
class SchedulingConfigurationTest {

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
}
