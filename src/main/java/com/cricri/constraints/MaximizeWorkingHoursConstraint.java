package com.cricri.constraints;

import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.model.Shift;
import com.cricri.service.SchedulingContext;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;

/**
 * Contrainte de maximisation des heures travaillées avec préférence pour les jours de semaine.
 *
 * <p>Cette contrainte reproduit la logique de l'ancienne addWeekdayStaffingObjective() : - Maximise
 * les heures réelles travaillées (pousse vers la durée complète des shifts) - Favorise les
 * assignations en semaine plutôt qu'en weekend
 *
 * <p>Remplace l'ancien système d'objectifs par une contrainte SOFT qui utilise la fonction objectif
 * de OR-Tools.
 */
public class MaximizeWorkingHoursConstraint implements Constraint {
  private final int weekdayMultiplier;
  private final ConstraintNature nature;

  public MaximizeWorkingHoursConstraint() {
    this(2, ConstraintNature.SOFT);
  }

  public MaximizeWorkingHoursConstraint(int weekdayMultiplier) {
    this(weekdayMultiplier, ConstraintNature.SOFT);
  }

  public MaximizeWorkingHoursConstraint(int weekdayMultiplier, ConstraintNature nature) {
    this.weekdayMultiplier = weekdayMultiplier;
    this.nature = nature;
  }

  @Override
  public void applyHardConstraint(SchedulingContext context) {
    // Cette contrainte n'a pas de sens en mode HARD
    // car elle ne peut pas être absolue (c'est un objectif d'optimisation)
    throw new UnsupportedOperationException(
        "MaximizeWorkingHoursConstraint ne peut pas être appliquée en mode HARD");
  }

  @Override
  public void applySoftConstraint(SchedulingContext context) {
    context.ensureVariablesInitialized();

    // Créer l'expression objectif comme dans l'ancienne addWeekdayStaffingObjective()
    LinearExprBuilder objective = LinearExpr.newBuilder();

    // Objectif principal : maximiser les heures réelles travaillées
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int s = 0; s < context.getShiftCount(); s++) {
        objective.add(context.getActualHours()[e][s]); // Encourager plus d'heures
      }
    }

    // Objectif secondaire : favoriser les jours de semaine
    for (int s = 0; s < context.getShiftCount(); s++) {
      Shift shift = context.getShifts().get(s);
      if (!isWeekend(shift)) {
        for (int e = 0; e < context.getEmployeeCount(); e++) {
          // Pondération supplémentaire pour les jours de semaine
          objective.addTerm(context.getActualHours()[e][s], weekdayMultiplier);
        }
      }
    }

    // Maximiser les heures totales (comme dans l'ancienne version)
    context.getModel().maximize(objective);
  }

  /** Détermine si un shift tombe un weekend. */
  private boolean isWeekend(Shift shift) {
    int dayInWeek = shift.day().getDayInWeek(); // 0=lundi, 6=dimanche
    return dayInWeek == 5 || dayInWeek == 6; // samedi ou dimanche
  }

  @Override
  public String getName() {
    return "MaximizeWorkingHours(weekday×" + (1 + weekdayMultiplier) + ", " + nature + ")";
  }

  @Override
  public ConstraintNature getNature() {
    return nature;
  }
}
