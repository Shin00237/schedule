package com.cricri.constraints;

import com.cricri.service.SchedulingContext;

public class MinimumRestDaysConstraint implements Constraint {
  private final int minRestDaysPerWeek;

  public MinimumRestDaysConstraint(int minRestDaysPerWeek) {
    this.minRestDaysPerWeek = minRestDaysPerWeek;
  }

  @Override
  public void apply(SchedulingContext context) {
    context.ensureVariablesInitialized();

    int daysPerCycle = context.getConfig().getDaysPerCycle();
    int maxWorkingDays = daysPerCycle - minRestDaysPerWeek;

    // Contrainte : au moins X jours de repos par semaine (max Y jours travaillés)
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int w = 0; w < context.getWorkingDaysPerWeek()[e].length; w++) {
        context
            .getModel()
            .addLessOrEqual(context.getWorkingDaysPerWeek()[e][w], maxWorkingDays);
      }
    }
  }

  @Override
  public String getName() {
    return "MinimumRestDays(" + minRestDaysPerWeek + " rest days min)";
  }

  @Override
  public int getPriority() {
    return ConstraintPriority.COMFORT;
  }
}