package com.cricri.constraints;

import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.config.ParameterKey;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ObjectiveWeight;
import com.cricri.model.Day;
import com.cricri.model.Shift;
import com.cricri.service.ObjectiveCollector;
import com.cricri.service.SchedulingContext;
import com.google.ortools.sat.LinearExpr;

public class MinimumRestConstraint implements Constraint {
  private final int minRestHours;
  private final ConstraintNature nature;
  private final ConstraintConfig config;

  public MinimumRestConstraint(ConstraintConfig config) {
    this.minRestHours = config.getParameter(ParameterKey.MIN_REST_HOURS);
    this.nature = config.nature();
    this.config = config;
  }

  @Override
  public void applyHardConstraint(SchedulingContext context) {
    context.ensureVariablesInitialized();

    int minRestMinutes = minRestHours * 60;

    // Pour chaque employé
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      // Pour chaque paire de shifts
      for (int s1 = 0; s1 < context.getShiftCount(); s1++) {
        for (int s2 = 0; s2 < context.getShiftCount(); s2++) {
          if (s1 != s2 && hasRestConflict(context, s1, s2, minRestMinutes)) {
            // Contrainte HARD : si les shifts sont en conflit, l'employé ne peut pas faire les deux
            context
                .getModel()
                .addLessOrEqual(
                    LinearExpr.newBuilder()
                        .add(context.getAssignments()[e][s1])
                        .add(context.getAssignments()[e][s2])
                        .build(),
                    1);
          }
        }
      }
    }
  }

  @Override
  public void applySoftConstraint(SchedulingContext context, ObjectiveCollector collector) {
    context.ensureVariablesInitialized();

    int minRestMinutes = minRestHours * 60;

    // Contrainte SOFT : variables de violation pour chaque conflit de repos
    for (int e = 0; e < context.getEmployeeCount(); e++) {
      for (int s1 = 0; s1 < context.getShiftCount(); s1++) {
        for (int s2 = 0; s2 < context.getShiftCount(); s2++) {
          if (s1 != s2 && hasRestConflict(context, s1, s2, minRestMinutes)) {
            // Variable de violation pour ce conflit
            var violationVar =
                context
                    .getModel()
                    .newBoolVar("rest_conflict_violation_e" + e + "_s" + s1 + "_s" + s2);

            // violationVar = 1 si les deux shifts sont assignés (conflit)
            // violationVar >= assignments[e][s1] + assignments[e][s2] - 1
            context
                .getModel()
                .addGreaterOrEqual(
                    violationVar,
                    com.google.ortools.sat.LinearExpr.newBuilder()
                        .add(context.getAssignments()[e][s1])
                        .add(context.getAssignments()[e][s2])
                        .add(-1)
                        .build());

            // Ajouter cette violation au collecteur d'objectif avec pénalité critique
            ObjectiveWeight weight =
                (config != null) ? config.getObjectiveWeight() : ObjectiveWeight.MINIMIZE_CRITICAL;
            collector.addTerm(violationVar, weight.getWeight()); // Poids négatif = minimisation
          }
        }
      }
    }
  }

  private boolean hasRestConflict(
      SchedulingContext context, int shiftIndex1, int shiftIndex2, int minRestMinutes) {
    Shift shift1 = context.getShifts().get(shiftIndex1);
    Shift shift2 = context.getShifts().get(shiftIndex2);

    // Calculer les temps absolus en minutes depuis le début de la période
    int endTime1 = calculateAbsoluteTime(shift1.day(), shift1.type().heureFinMinutes());
    int startTime2 = calculateAbsoluteTime(shift2.day(), shift2.type().heureDebutMinutes());

    // Cas 1: Chevauchement - seulement si c'est le même jour absolu
    int day1 = shift1.day().dayNumber(); // Déjà global et 0-indexé
    int day2 = shift2.day().dayNumber(); // Déjà global et 0-indexé

    if (day1 == day2 && startTime2 < endTime1) {
      return true;
    }

    // Cas 2: Repos insuffisant - shift2 commence moins de minRestMinutes après la fin de shift1
    return startTime2 > endTime1 && startTime2 < endTime1 + minRestMinutes;
  }

  private int calculateAbsoluteTime(Day day, int heureMinutes) {
    // Convertir en temps absolu : jour * 24h * 60min + heureMinutes (déjà 0-indexé)
    int absoluteDay = day.dayNumber();
    return absoluteDay * 24 * 60 + heureMinutes;
  }

  @Override
  public String getName() {
    return "MinimumRest(" + minRestHours + "h, " + nature + ")";
  }

  @Override
  public ConstraintNature getNature() {
    return nature;
  }
}
