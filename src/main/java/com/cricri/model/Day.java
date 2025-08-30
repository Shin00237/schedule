package com.cricri.model;

import java.time.DayOfWeek;
import com.cricri.constants.Constants;

public record Day(
    int dayNumber, // 0-indexé (0-6 dans la semaine, global pour les contraintes)
    DayOfWeek dayOfWeek // MONDAY, TUESDAY, etc.
    ) {
  public int getWeekNumber() {
    return dayNumber / Constants.DAYS_IN_A_WEEK; // 0-indexé
  }

  public int getDayInWeek() {
    return dayNumber % Constants.DAYS_IN_A_WEEK; // 0-n selon configuration
  }

  public boolean isWeekend() {
    return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
  }
}
