package com.cricri.model;

import java.time.DayOfWeek;

public record Day(
    int dayNumber, // 0-indexé (0-6 dans la semaine, global pour les contraintes)
    DayOfWeek dayOfWeek, // MONDAY, TUESDAY, etc.
    Week week // référence vers la semaine parent
    ) {
  public int getWeekNumber() {
    return week.weekNumber();
  }

  public int getDayInWeek() {
    return dayNumber % week.config().getDaysPerCycle(); // 0-n selon configuration
  }

  public boolean isWeekend() {
    return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
  }
}
