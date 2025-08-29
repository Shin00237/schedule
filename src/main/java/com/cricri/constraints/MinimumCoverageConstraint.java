package com.cricri.constraints;

import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.model.Shift;
import com.cricri.service.SchedulingContext;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;

public class MinimumCoverageConstraint implements Constraint {
  private final ConstraintNature nature;

  public MinimumCoverageConstraint() {
    this(ConstraintNature.HARD);
  }

  public MinimumCoverageConstraint(ConstraintNature nature) {
    this.nature = nature;
  }

  @Override
  public void applyHardConstraint(SchedulingContext context) {
    context.ensureVariablesInitialized();

    for (int s = 0; s < context.getShiftCount(); s++) {
      Shift shift = context.getShifts().get(s);

      // Compter le nombre d'employés assignés
      LinearExprBuilder sumEmployees = LinearExpr.newBuilder();
      for (int e = 0; e < context.getEmployeeCount(); e++) {
        sumEmployees.add(context.getAssignments()[e][s]);
      }

      // Contrainte HARD : somme >= minimum requis
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
  public void applySoftConstraint(SchedulingContext context) {
    context.ensureVariablesInitialized();

    for (int s = 0; s < context.getShiftCount(); s++) {
      Shift shift = context.getShifts().get(s);

      // Compter le nombre d'employés assignés
      LinearExprBuilder sumEmployees = LinearExpr.newBuilder();
      for (int e = 0; e < context.getEmployeeCount(); e++) {
        sumEmployees.add(context.getAssignments()[e][s]);
      }

      // Lier avec la variable employeesPerShift
      context.getModel().addEquality(context.getEmployeesPerShift()[s], sumEmployees);

      // Contrainte SOFT : variables de violation pour sous-couverture et sur-couverture

      // Variable pour sous-couverture (moins d'employés que le minimum)
      var underCoverageVar =
          context.getModel().newIntVar(0, shift.minEmployes(), "under_coverage_s" + s);
      context
          .getModel()
          .addGreaterOrEqual(
              underCoverageVar,
              LinearExpr.newBuilder()
                  .add(shift.minEmployes())
                  .addTerm(context.getEmployeesPerShift()[s], -1)
                  .build());

      // Variable pour sur-couverture (plus d'employés que le maximum, si défini)
      if (shift.maxEmployes() > 0) {
        var overCoverageVar =
            context.getModel().newIntVar(0, context.getEmployeeCount(), "over_coverage_s" + s);
        context
            .getModel()
            .addGreaterOrEqual(
                overCoverageVar,
                LinearExpr.newBuilder()
                    .add(context.getEmployeesPerShift()[s])
                    .add(-shift.maxEmployes())
                    .build());

        // TODO: Ajouter ces violations à un objectif global de minimisation
      }

      // TODO: Ajouter underCoverageVar à un objectif global de minimisation
    }
  }

  @Override
  public String getName() {
    return "MinimumCoverage(" + nature + ")";
  }

  @Override
  public ConstraintNature getNature() {
    return nature;
  }
}
