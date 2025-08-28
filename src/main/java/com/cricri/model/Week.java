package com.cricri.model;

import com.cricri.service.SchedulingConfiguration;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

public record Week(
    int weekNumber, // 0, 1, 2...
    List<Day> days, // jours de la semaine selon la configuration
    SchedulingConfiguration config) {
  public static Week create(int weekNumber, SchedulingConfiguration config) {
    List<Day> days = new ArrayList<>();
    List<DayOfWeek> cyclePattern = config.getCyclePattern();
    int daysPerCycle = config.getDaysPerCycle();

    Week week = new Week(weekNumber, days, config);

    for (int i = 0; i < daysPerCycle; i++) {
      int dayNumber = weekNumber * daysPerCycle + i; // 0-indexé
      days.add(new Day(dayNumber, cyclePattern.get(i), week));
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
    int daysPerCycle = config.getDaysPerCycle();
    if (dayIndex < 0 || dayIndex >= daysPerCycle) {
      throw new IllegalArgumentException("Day index must be between 0 and " + (daysPerCycle - 1));
    }
    return days.get(dayIndex);
  }

  public List<Day> getWeekends() {
    return days.stream().filter(Day::isWeekend).toList();
  }

  public List<Day> getWeekdays() {
    return days.stream().filter(day -> !day.isWeekend()).toList();
  }
}
