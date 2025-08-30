package com.cricri.model;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import com.cricri.constants.Constants;
import com.cricri.service.SchedulingConfiguration;

public record Week(
    int weekNumber, // 0, 1, 2...
    List<Day> days, // jours de la semaine selon la configuration
    SchedulingConfiguration config) {
  public static Week create(int weekNumber, SchedulingConfiguration config) {
    List<Day> days = new ArrayList<>();
    List<DayOfWeek> cyclePattern = config.getCyclePattern();

    Week week = new Week(weekNumber, days, config);

    for (int i = 0; i < Constants.DAYS_IN_A_WEEK; i++) {
      int dayNumber = weekNumber * Constants.DAYS_IN_A_WEEK + i; // 0-indexé
      days.add(new Day(dayNumber, cyclePattern.get(i)));
    }

    return week;
  }

  // Méthode de compatibilité avec configuration par défaut
  public static Week create(int weekNumber) {
    return create(weekNumber, SchedulingConfiguration.STANDARD_WEEK);
  }

  public Day getDay(DayOfWeek dayOfWeek) {
    return days.stream().filter(day -> day.dayOfWeek() == dayOfWeek).findFirst().orElseThrow();
  }

  public Day getDay(int dayIndex) {
    if (dayIndex < 0 || dayIndex >= Constants.DAYS_IN_A_WEEK) {
      throw new IllegalArgumentException("Day index must be between 0 and " + (Constants.DAYS_IN_A_WEEK - 1));
    }
    return days.get(dayIndex);
  }
}
