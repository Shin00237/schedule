package com.cricri.constraints;

import com.cricri.model.Shift;
import com.cricri.service.SchedulingContext;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;

public class MaxHoursPerWeekConstraint implements Constraint {
  private final int maxHoursPerWeek;

  public MaxHoursPerWeekConstraint(int maxHoursPerWeek) {
    this.maxHoursPerWeek = maxHoursPerWeek;
  }

  @Override
  public void apply(SchedulingContext context) {
    context.ensureVariablesInitialized();

    int nbWeeks = context.getWeekCount();

    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int w = 0; w < nbWeeks; w++) {
        LinearExprBuilder hoursInWeek = LinearExpr.newBuilder();

        // Pour chaque shift de cette semaine
        for (int s = 0; s < context.getShiftCount(); s++) {
          Shift shift = context.getShifts().get(s);
          int shiftWeek = shift.day().getWeekNumber();

          if (shiftWeek == w) {
            // Ajouter les heures réelles travaillées pour ce shift
            hoursInWeek.add(context.getActualHours()[e][s]);
          }
        }

        // Lier avec la variable hoursPerEmployeePerWeek
        context
            .getModel()
            .addEquality(context.getHoursPerEmployeePerWeek()[e][w], hoursInWeek);

        // Contrainte : ne pas dépasser le maximum d'heures par semaine
        context
            .getModel()
            .addLessOrEqual(context.getHoursPerEmployeePerWeek()[e][w], maxHoursPerWeek);
      }
    }
  }

  @Override
  public String getName() {
    return "MaxHoursPerWeek(" + (maxHoursPerWeek / 60.0) + "h)";
  }

  @Override
  public int getPriority() {
    return 0; // Priorité normale
  }
}