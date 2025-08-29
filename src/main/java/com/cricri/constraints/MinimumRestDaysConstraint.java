package com.cricri.constraints;

import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ConstraintPriority;
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
  public void applyHardConstraint(SchedulingContext context) {
    context.ensureVariablesInitialized();

    int daysPerCycle = context.getConfig().getDaysPerCycle();
    int maxWorkingDays = daysPerCycle - minRestDaysPerWeek;

    // Contrainte HARD : au moins X jours de repos par semaine (max Y jours travaillés)
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int w = 0; w < context.getWorkingDaysPerWeek()[e].length; w++) {
        context.getModel().addLessOrEqual(context.getWorkingDaysPerWeek()[e][w], maxWorkingDays);
      }
    }
  }

  @Override
  public void applySoftConstraint(SchedulingContext context) {
    context.ensureVariablesInitialized();

    int daysPerCycle = context.getConfig().getDaysPerCycle();
    int maxWorkingDays = daysPerCycle - minRestDaysPerWeek;

    // Contrainte SOFT : variable de violation pour mesurer l'écart
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int w = 0; w < context.getWorkingDaysPerWeek()[e].length; w++) {
        // Variable de violation : jours travaillés au-delà du maximum autorisé
        var violationVar =
            context.getModel().newIntVar(0, daysPerCycle, "rest_days_violation_e" + e + "_w" + w);

        // violationVar >= workingDays - maxWorkingDays
        context
            .getModel()
            .addGreaterOrEqual(
                violationVar,
                com.google.ortools.sat.LinearExpr.newBuilder()
                    .add(context.getWorkingDaysPerWeek()[e][w])
                    .add(-maxWorkingDays)
                    .build());

        // violationVar >= 0 (implicite car défini comme IntVar(0, daysPerCycle))

        // TODO: Ajouter cette violation à un objectif global de minimisation
        // Pour l'instant, la variable est créée mais pas utilisée dans l'optimisation
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
