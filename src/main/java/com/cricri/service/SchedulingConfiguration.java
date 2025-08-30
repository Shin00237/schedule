package com.cricri.service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import com.cricri.constants.Constants;
import com.cricri.constraints.exceptions.NegativeParameterException;
import com.cricri.model.ShiftDay;
import com.cricri.model.Week;

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

    public static Week createWeek(int weekNumber) {
      List<ShiftDay> days = new ArrayList<>();
      for (int i = 0; i < Constants.DAYS_IN_A_WEEK; i++) {
          int dayNumber = weekNumber * Constants.DAYS_IN_A_WEEK + i;
          days.add(new ShiftDay(dayNumber, DayOfWeek.of((i % 7) + 1))); // DayOfWeek 1=Monday ... 7=Sunday
      }
      return new Week(weekNumber, days);
  }

  public List<DayOfWeek> getCyclePattern() {
    return cyclePattern;
  }

  public String getDisplayName() {
    return displayName;
  }
}
