package com.cricri.constraints;

import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ObjectiveWeight;
import com.cricri.service.ObjectiveCollector;
import com.cricri.service.SchedulingContext;
import com.google.ortools.sat.LinearExpr;

public class AssignmentHoursConstraint implements Constraint {
  private final int minHoursPerShift;
  private final ConstraintNature nature;
  private final ConstraintConfig config;

  public AssignmentHoursConstraint(int minHoursPerShift) {
    this(minHoursPerShift, ConstraintNature.HARD, null);
  }

  public AssignmentHoursConstraint(int minHoursPerShift, ConstraintNature nature) {
    this(minHoursPerShift, nature, null);
  }

  public AssignmentHoursConstraint(
      int minHoursPerShift, ConstraintNature nature, ConstraintConfig config) {
    this.minHoursPerShift = minHoursPerShift;
    this.nature = nature;
    this.config = config;
  }

  @Override
  public void applyHardConstraint(SchedulingContext context) {
    context.ensureVariablesInitialized();

    // Contrainte HARD : cohérence stricte entre assignations et heures
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int s = 0; s < context.getShiftCount(); s++) {
        int maxShiftDuration = context.getShifts().get(s).type().dureeEffectiveMinutes();

        // Si assigné, minimum minHoursPerShift, sinon 0
        context
            .getModel()
            .addGreaterOrEqual(
                context.getActualHours()[e][s],
                LinearExpr.newBuilder()
                    .addTerm(context.getAssignments()[e][s], minHoursPerShift)
                    .build());

        // actualHours[e][s] <= assignments[e][s] * maxShiftDuration
        context
            .getModel()
            .addLessOrEqual(
                context.getActualHours()[e][s],
                LinearExpr.newBuilder()
                    .addTerm(context.getAssignments()[e][s], maxShiftDuration)
                    .build());
      }
    }
  }

  @Override
  public void applySoftConstraint(SchedulingContext context, ObjectiveCollector collector) {
    context.ensureVariablesInitialized();

    // Contrainte SOFT : permet une certaine flexibilité sur les heures minimum
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int s = 0; s < context.getShiftCount(); s++) {
        int maxShiftDuration = context.getShifts().get(s).type().dureeEffectiveMinutes();

        // Lien strict pour les heures maximum (toujours respecté)
        context
            .getModel()
            .addLessOrEqual(
                context.getActualHours()[e][s],
                LinearExpr.newBuilder()
                    .addTerm(context.getAssignments()[e][s], maxShiftDuration)
                    .build());

        // Variable de violation pour les heures en dessous du minimum
        var violationVar =
            context
                .getModel()
                .newIntVar(0, minHoursPerShift, "assignment_hours_violation_e" + e + "_s" + s);

        // violationVar >= minHours * assigned - actualHours
        context
            .getModel()
            .addGreaterOrEqual(
                violationVar,
                LinearExpr.newBuilder()
                    .addTerm(context.getAssignments()[e][s], minHoursPerShift)
                    .addTerm(context.getActualHours()[e][s], -1)
                    .build());

        // Ajouter cette violation au collecteur d'objectif avec pénalité faible
        ObjectiveWeight weight =
            (config != null) ? config.getObjectiveWeight() : ObjectiveWeight.MINIMIZE_LOW;
        collector.addTerm(violationVar, weight.getWeight()); // Poids négatif = minimisation
      }
    }
  }

  @Override
  public String getName() {
    return "AssignmentHours(min=" + (minHoursPerShift / 60.0) + "h, " + nature + ")";
  }

  @Override
  public ConstraintNature getNature() {
    return nature;
  }
}
