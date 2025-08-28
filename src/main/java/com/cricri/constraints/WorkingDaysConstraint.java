package com.cricri.constraints;

import com.cricri.model.Shift;
import com.cricri.service.SchedulingContext;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;

public class WorkingDaysConstraint implements Constraint {

  @Override
  public void apply(SchedulingContext context) {
    context.ensureVariablesInitialized();

    // Lier les assignments aux workingDays
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int s = 0; s < context.getShiftCount(); s++) {
        Shift shift = context.getShifts().get(s);
        int weekNumber = shift.day().getWeekNumber();
        int dayOfWeek = shift.day().getDayInWeek(); // 0-6 pour lundi-dimanche

        // Si l'employé est assigné à ce shift, alors il travaille ce jour
        // workingDays[e][w][d] >= assignments[e][s]
        context
            .getModel()
            .addGreaterOrEqual(
                context.getWorkingDays()[e][weekNumber][dayOfWeek],
                context.getAssignments()[e][s]);
      }
    }

    // Lier workingDays à workingDaysPerWeek
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int w = 0; w < context.getWorkingDaysPerWeek()[e].length; w++) {
        LinearExprBuilder sumDaysWorked = LinearExpr.newBuilder();
        int daysPerCycle = context.getConfig().getDaysPerCycle();
        for (int d = 0; d < daysPerCycle; d++) {
          sumDaysWorked.add(context.getWorkingDays()[e][w][d]);
        }
        context.getModel().addEquality(context.getWorkingDaysPerWeek()[e][w], sumDaysWorked);
      }
    }
  }

  @Override
  public String getName() {
    return "WorkingDays";
  }

  @Override
  public int getPriority() {
    return ConstraintPriority.CONSISTENCY;
  }
}
