package com.cricri.constraints;

import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ObjectiveWeight;
import com.cricri.model.Shift;
import com.cricri.service.ObjectiveCollector;
import com.cricri.service.SchedulingContext;

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
  private final ConstraintConfig config;

  public MaximizeWorkingHoursConstraint(ConstraintConfig config) {
    this.weekdayMultiplier = config.getIntParameter("weekdayMultiplier", 2); // 2 par défaut
    this.nature = config.nature();
    this.config = config;
  }

  @Override
  public void applyHardConstraint(SchedulingContext context) {
    // Cette contrainte n'a pas de sens en mode HARD
    // car elle ne peut pas être absolue (c'est un objectif d'optimisation)
    throw new UnsupportedOperationException(
        "MaximizeWorkingHoursConstraint ne peut pas être appliquée en mode HARD");
  }

  @Override
  public void applySoftConstraint(SchedulingContext context, ObjectiveCollector collector) {
    context.ensureVariablesInitialized();

    // Récupérer le poids d'objectif depuis la configuration
    ObjectiveWeight weight =
        (config != null) ? config.getObjectiveWeight() : ObjectiveWeight.MAXIMIZE_CRITICAL;

    // Objectif principal : maximiser les heures réelles travaillées
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int s = 0; s < context.getShiftCount(); s++) {
        collector.addTerm(context.getActualHours()[e][s], weight.getWeight());
      }
    }

    // Objectif secondaire : favoriser les jours de semaine
    for (int s = 0; s < context.getShiftCount(); s++) {
      Shift shift = context.getShifts().get(s);
      if (!isWeekend(shift)) {
        for (int e = 0; e < context.getEmployeeCount(); e++) {
          // Bonus pondéré pour les jours de semaine
          long bonusWeight = (weight.getWeight() * weekdayMultiplier) / 10;
          collector.addTerm(context.getActualHours()[e][s], bonusWeight);
        }
      }
    }
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
