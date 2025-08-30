package com.cricri.service;

import java.time.DayOfWeek;
import java.util.List;
import com.cricri.constraints.exceptions.NegativeParameterException;

/**
 * Configuration pour définir la structure temporelle du planning. Permet de supporter différents
 * types de cycles de travail.
 */
public class SchedulingConfiguration {
  private final List<DayOfWeek> cyclePattern;
  private final String displayName;

  public SchedulingConfiguration(
      int daysPerCycle, List<DayOfWeek> cyclePattern, String displayName) {
    if (daysPerCycle <= 0) {
      throw new NegativeParameterException("daysPerCycle");
    }
    if (cyclePattern.size() != daysPerCycle) {
      throw new IllegalArgumentException(
          "cyclePattern doit contenir exactement " + daysPerCycle + " jours");
    }

    this.cyclePattern = List.copyOf(cyclePattern); // Immutable
    this.displayName = displayName;
  }

  // Presets couramment utilisés

  /** Configuration standard : semaine complète de 7 jours */
  public static final SchedulingConfiguration STANDARD_WEEK =
      new SchedulingConfiguration(
          7,
          List.of(
              DayOfWeek.MONDAY,
              DayOfWeek.TUESDAY,
              DayOfWeek.WEDNESDAY,
              DayOfWeek.THURSDAY,
              DayOfWeek.FRIDAY,
              DayOfWeek.SATURDAY,
              DayOfWeek.SUNDAY),
          "Semaine standard (7j)");

  /** Configuration jours ouvrés : lundi à vendredi uniquement */
  public static final SchedulingConfiguration WEEKDAYS_ONLY =
      new SchedulingConfiguration(
          5,
          List.of(
              DayOfWeek.MONDAY,
              DayOfWeek.TUESDAY,
              DayOfWeek.WEDNESDAY,
              DayOfWeek.THURSDAY,
              DayOfWeek.FRIDAY),
          "Jours ouvrés seulement (5j)");

  /** Configuration 6 jours : semaine sans dimanche */
  public static final SchedulingConfiguration SIX_DAYS_WEEK =
      new SchedulingConfiguration(
          6,
          List.of(
              DayOfWeek.MONDAY,
              DayOfWeek.TUESDAY,
              DayOfWeek.WEDNESDAY,
              DayOfWeek.THURSDAY,
              DayOfWeek.FRIDAY,
              DayOfWeek.SATURDAY),
          "Semaine 6 jours (sans dimanche)");

  // Getters


  public List<DayOfWeek> getCyclePattern() {
    return cyclePattern;
  }

  public String getDisplayName() {
    return displayName;
  }
}
