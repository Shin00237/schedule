package com.cricri.model;

import java.time.DayOfWeek;
import java.util.List;
import com.cricri.constants.Constants;

public record Week(int weekNumber, List<ShiftDay> days) {
  public Week {
      days = List.copyOf(days); // Immutabilité garantie
  }

  public ShiftDay getDay(DayOfWeek dayOfWeek) {
    return days.stream()
      .filter(day -> day.dayOfWeek() == dayOfWeek)
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("No day found for " + dayOfWeek));
  }

  public ShiftDay getDay(int dayIndex) {
    if (dayIndex < 0 || dayIndex >= Constants.DAYS_IN_A_WEEK) {
      throw new IllegalArgumentException("Day index must be between 0 and " + (Constants.DAYS_IN_A_WEEK - 1));
    }
    return days.get(dayIndex);
  }
}
