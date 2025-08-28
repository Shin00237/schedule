package com.cricri.constraints;

import com.cricri.service.SchedulingContext;

public class MinimumRestDaysConstraint implements Constraint {
  private final int minRestDaysPerWeek;
  private final ConstraintNature nature;
  private final ConstraintPriority priority;

  public MinimumRestDaysConstraint(int minRestDaysPerWeek) {
    this(minRestDaysPerWeek, ConstraintNature.SOFT, ConstraintPriority.COMFORT);
  }

  public MinimumRestDaysConstraint(
      int minRestDaysPerWeek, ConstraintNature nature, ConstraintPriority priority) {
    this.minRestDaysPerWeek = minRestDaysPerWeek;
    this.nature = nature;
    this.priority = priority;
  }

  @Override
  public void apply(SchedulingContext context) {
    context.ensureVariablesInitialized();

    int daysPerCycle = context.getConfig().getDaysPerCycle();
    int maxWorkingDays = daysPerCycle - minRestDaysPerWeek;

    // Contrainte : au moins X jours de repos par semaine (max Y jours travaillés)
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int w = 0; w < context.getWorkingDaysPerWeek()[e].length; w++) {
        context.getModel().addLessOrEqual(context.getWorkingDaysPerWeek()[e][w], maxWorkingDays);
      }
    }
  }

  @Override
  public String getName() {
    return "MinimumRestDays(" + minRestDaysPerWeek + " rest days min, " + nature + ")";
  }

  @Override
  public ConstraintPriority getPriority() {
    return priority;
  }

  @Override
  public ConstraintNature getNature() {
    return nature;
  }
}
