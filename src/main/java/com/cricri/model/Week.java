package com.cricri.model;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

public record Week(
    int weekNumber, // 0, 1, 2...
    List<Day> days // 7 jours de la semaine
    ) {
  public static Week create(int weekNumber) {
    List<Day> days = new ArrayList<>();
    DayOfWeek[] daysOfWeek = {
      DayOfWeek.MONDAY,
      DayOfWeek.TUESDAY,
      DayOfWeek.WEDNESDAY,
      DayOfWeek.THURSDAY,
      DayOfWeek.FRIDAY,
      DayOfWeek.SATURDAY,
      DayOfWeek.SUNDAY
    };

    Week week = new Week(weekNumber, days);

    for (int i = 0; i < 7; i++) {
      int dayNumber = weekNumber * 7 + i; // 0-indexé
      days.add(new Day(dayNumber, daysOfWeek[i], week));
    }

    return week;
  }

  public Day getDay(DayOfWeek dayOfWeek) {
    return days.stream().filter(day -> day.dayOfWeek() == dayOfWeek).findFirst().orElseThrow();
  }

  public Day getDay(int dayIndex) {
    if (dayIndex < 0 || dayIndex >= 7) {
      throw new IllegalArgumentException("Day index must be between 0 and 6");
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
