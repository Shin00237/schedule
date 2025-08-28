package com.cricri.constraints;

import com.cricri.service.SchedulingContext;
import com.google.ortools.sat.LinearExpr;

public class AssignmentHoursConstraint implements Constraint {
  private final int minHoursPerShift;

  public AssignmentHoursConstraint(int minHoursPerShift) {
    this.minHoursPerShift = minHoursPerShift;
  }

  @Override
  public void apply(SchedulingContext context) {
    context.ensureVariablesInitialized();

    // Si un employé n'est pas assigné à un shift, ses heures réelles doivent être 0
    // Si un employé est assigné à un shift, ses heures réelles doivent être >= minHoursPerShift
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int s = 0; s < context.getShiftCount(); s++) {
        int maxShiftDuration = context.getShifts().get(s).type().dureeEffectiveMinutes();

        // Contrainte de cohérence : si assigné, minimum minHoursPerShift, sinon 0
        // actualHours[e][s] >= assignments[e][s] * minHoursPerShift
        context
            .getModel()
            .addGreaterOrEqual(
                context.getActualHours()[e][s],
                LinearExpr.newBuilder()
                    .addTerm(context.getAssignments()[e][s], minHoursPerShift)
                    .build());

        // actualHours[e][s] <= assignments[e][s] * maxShiftDuration (si non assigné, alors 0)
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
  public String getName() {
    return "AssignmentHours(min=" + (minHoursPerShift / 60.0) + "h)";
  }

  @Override
  public int getPriority() {
    return -5; // Prioritaire pour la cohérence
  }
}