package com.cricri.objectives;

import com.cricri.model.Shift;
import com.cricri.service.SchedulingContext;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;

public class WeekdayPreferenceObjective implements ObjectiveFunction {
  private final int weekdayMultiplier;

  public WeekdayPreferenceObjective() {
    this.weekdayMultiplier = 2;
  }

  public WeekdayPreferenceObjective(int weekdayMultiplier) {
    this.weekdayMultiplier = weekdayMultiplier;
  }

  @Override
  public void apply(SchedulingContext context) {
    context.ensureVariablesInitialized();

    LinearExprBuilder objective = LinearExpr.newBuilder();

    // Objectif principal : maximiser les heures réelles travaillées
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int s = 0; s < context.getShiftCount(); s++) {
        objective.add(context.getActualHours()[e][s]);
      }
    }

    // Objectif secondaire : favoriser les jours de semaine
    for (int s = 0; s < context.getShiftCount(); s++) {
      Shift shift = context.getShifts().get(s);
      if (!shift.day().isWeekend()) {
        for (int e = 0; e < context.getEmployeeCount(); e++) {
          objective.addTerm(
              context.getActualHours()[e][s], weekdayMultiplier); // Pondération pour semaine
        }
      }
    }

    context.getModel().maximize(objective);
  }

  @Override
  public String getName() {
    return "WeekdayPreference(x" + weekdayMultiplier + ")";
  }

  @Override
  public int getWeight() {
    return 10;
  }
}