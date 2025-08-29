package com.cricri.constraints;

import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.model.Shift;
import com.cricri.service.SchedulingContext;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;

public class MaxHoursPerWeekConstraint implements Constraint {
  private final int maxHoursPerWeek;
  private final ConstraintNature nature;

  public MaxHoursPerWeekConstraint(int maxHoursPerWeek) {
    this(maxHoursPerWeek, ConstraintNature.HARD);
  }

  public MaxHoursPerWeekConstraint(int maxHoursPerWeek, ConstraintNature nature) {
    this.maxHoursPerWeek = maxHoursPerWeek;
    this.nature = nature;
  }

  @Override
  public void applyHardConstraint(SchedulingContext context) {
    context.ensureVariablesInitialized();

    int nbWeeks = context.getWeekCount();

    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int w = 0; w < nbWeeks; w++) {
        LinearExprBuilder hoursInWeek = LinearExpr.newBuilder();

        // Pour chaque shift de cette semaine
        for (int s = 0; s < context.getShiftCount(); s++) {
          Shift shift = context.getShifts().get(s);
          int shiftWeek = shift.day().getWeekNumber();

          if (shiftWeek == w) {
            // Ajouter les heures réelles travaillées pour ce shift
            hoursInWeek.add(context.getActualHours()[e][s]);
          }
        }

        // Lier avec la variable hoursPerEmployeePerWeek
        context.getModel().addEquality(context.getHoursPerEmployeePerWeek()[e][w], hoursInWeek);

        // Contrainte HARD : ne pas dépasser le maximum d'heures par semaine
        context
            .getModel()
            .addLessOrEqual(context.getHoursPerEmployeePerWeek()[e][w], maxHoursPerWeek);
      }
    }
  }

  @Override
  public void applySoftConstraint(SchedulingContext context) {
    context.ensureVariablesInitialized();

    int nbWeeks = context.getWeekCount();

    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int w = 0; w < nbWeeks; w++) {
        LinearExprBuilder hoursInWeek = LinearExpr.newBuilder();

        // Pour chaque shift de cette semaine
        for (int s = 0; s < context.getShiftCount(); s++) {
          Shift shift = context.getShifts().get(s);
          int shiftWeek = shift.day().getWeekNumber();

          if (shiftWeek == w) {
            // Ajouter les heures réelles travaillées pour ce shift
            hoursInWeek.add(context.getActualHours()[e][s]);
          }
        }

        // Lier avec la variable hoursPerEmployeePerWeek
        context.getModel().addEquality(context.getHoursPerEmployeePerWeek()[e][w], hoursInWeek);

        // Contrainte SOFT : variable de violation pour les heures dépassées
        var violationVar =
            context.getModel().newIntVar(0, 100 * 60, "max_hours_violation_e" + e + "_w" + w);

        // violationVar >= hoursPerWeek - maxHoursPerWeek
        context
            .getModel()
            .addGreaterOrEqual(
                violationVar,
                LinearExpr.newBuilder()
                    .add(context.getHoursPerEmployeePerWeek()[e][w])
                    .add(-maxHoursPerWeek)
                    .build());

        // TODO: Ajouter cette violation à un objectif global de minimisation
      }
    }
  }

  @Override
  public String getName() {
    return "MaxHoursPerWeek(" + (maxHoursPerWeek / 60.0) + "h, " + nature + ")";
  }

  @Override
  public ConstraintNature getNature() {
    return nature;
  }
}
