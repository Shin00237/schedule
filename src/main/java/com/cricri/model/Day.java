package com.cricri.model;

import java.time.DayOfWeek;

public record Day(
    int dayNumber, // 1-indexé comme actuellement
    DayOfWeek dayOfWeek, // MONDAY, TUESDAY, etc.
    Week week // référence vers la semaine parent
    ) {
  public int getWeekNumber() {
    return week.weekNumber();
  }

  public int getDayNumber() {
    return (dayNumber - 1) % 7;
  }

  public boolean isWeekend() {
    return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
  }
}
