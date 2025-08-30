package com.cricri.constraints;

import com.cricri.constants.Constants;
import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.config.ParameterKey;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ObjectiveWeight;
import com.cricri.service.ObjectiveCollector;
import com.cricri.service.SchedulingContext;

public class MinimumRestDaysConstraint implements Constraint {
  private final int minRestDaysPerWeek;
  private final ConstraintNature nature;
  private final ConstraintConfig config;

  public MinimumRestDaysConstraint(ConstraintConfig config) {
    this.minRestDaysPerWeek = config.getParameter(ParameterKey.MIN_REST_DAYS_PER_WEEK);
    this.nature = config.nature();
    this.config = config;
  }

  @Override
  public void applyHardConstraint(SchedulingContext context) {
    context.ensureVariablesInitialized();

    int maxWorkingDays = Constants.DAYS_IN_A_WEEK - minRestDaysPerWeek;

    // Contrainte HARD : au moins X jours de repos par semaine (max Y jours travaillés)
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int w = 0; w < context.getWorkingDaysPerWeek()[e].length; w++) {
        context.getModel().addLessOrEqual(context.getWorkingDaysPerWeek()[e][w], maxWorkingDays);
      }
    }
  }

  @Override
  public void applySoftConstraint(SchedulingContext context, ObjectiveCollector collector) {
    context.ensureVariablesInitialized();

    int maxWorkingDays = Constants.DAYS_IN_A_WEEK - minRestDaysPerWeek;

    // Contrainte SOFT : variable de violation pour mesurer l'écart
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int w = 0; w < context.getWorkingDaysPerWeek()[e].length; w++) {
        // Variable de violation : jours travaillés au-delà du maximum autorisé
        var violationVar =
            context.getModel().newIntVar(0, Constants.DAYS_IN_A_WEEK, "rest_days_violation_e" + e + "_w" + w);

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

        // Ajouter cette violation au collecteur d'objectif avec pénalité moyenne
        ObjectiveWeight weight =
            (config != null) ? config.getObjectiveWeight() : ObjectiveWeight.MINIMIZE_MEDIUM;
        collector.addTerm(violationVar, weight.getWeight()); // Poids négatif = minimisation
      }
    }
  }

  @Override
  public String getName() {
    return "MinimumRestDays(" + minRestDaysPerWeek + " rest days min, " + nature + ")";
  }

  @Override
  public ConstraintNature getNature() {
    return nature;
  }
}
