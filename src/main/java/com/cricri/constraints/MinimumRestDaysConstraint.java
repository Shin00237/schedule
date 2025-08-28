package com.cricri.constraints;

import com.cricri.service.SchedulingContext;

public class MinimumRestDaysConstraint implements Constraint {
  private final int maxWorkingDays;

  public MinimumRestDaysConstraint(int minRestDaysPerWeek) {
    this.maxWorkingDays = 7 - minRestDaysPerWeek;
  }

  @Override
  public void apply(SchedulingContext context) {
    context.ensureVariablesInitialized();

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
    return "MinimumRestDays(max " + maxWorkingDays + " working days)";
  }

  @Override
  public int getPriority() {
    return 5; // Moins prioritaire que les contraintes de base
  }
}