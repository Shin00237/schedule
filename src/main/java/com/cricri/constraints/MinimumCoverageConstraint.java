package com.cricri.constraints;

import com.cricri.model.Shift;
import com.cricri.service.SchedulingContext;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;

public class MinimumCoverageConstraint implements Constraint {

  @Override
  public void apply(SchedulingContext context) {
    context.ensureVariablesInitialized();

    for (int s = 0; s < context.getShiftCount(); s++) {
      Shift shift = context.getShifts().get(s);

      // Compter le nombre d'employés assignés
      LinearExprBuilder sumEmployees = LinearExpr.newBuilder();
      for (int e = 0; e < context.getEmployeeCount(); e++) {
        sumEmployees.add(context.getAssignments()[e][s]);
      }

      // Ajouter la contrainte : somme >= minimum requis
      context.getModel().addGreaterOrEqual(sumEmployees, shift.minEmployes());

      // Et aussi <= maximum si défini
      if (shift.maxEmployes() > 0) {
        context.getModel().addLessOrEqual(sumEmployees, shift.maxEmployes());
      }

      // Lier avec la variable employeesPerShift
      context.getModel().addEquality(context.getEmployeesPerShift()[s], sumEmployees);
    }
  }

  @Override
  public String getName() {
    return "MinimumCoverage";
  }

  @Override
  public int getPriority() {
    return ConstraintPriority.FUNDAMENTAL;
  }
}