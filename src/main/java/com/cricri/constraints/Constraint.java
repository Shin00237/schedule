package com.cricri.constraints;

import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.service.ObjectiveCollector;
import com.cricri.service.SchedulingContext;

public interface Constraint {
  /**
   * Implémentation de la contrainte en version HARD (absolue)
   *
   * @param context Le contexte contenant les variables et données
   */
  void applyHardConstraint(SchedulingContext context);

  /**
   * Implémentation de la contrainte en version SOFT (avec pénalité)
   *
   * <p>Cette version reçoit un collecteur d'objectif partagé permettant à toutes les contraintes
   * SOFT de contribuer à un objectif global unifié.
   *
   * @param context Le contexte contenant les variables et données
   * @param collector Le collecteur pour ajouter les termes d'objectif de cette contrainte
   */
  void applySoftConstraint(SchedulingContext context, ObjectiveCollector collector);

  /** Nom unique de la contrainte pour debugging */
  String getName();

  /** Validation des prérequis avant application */
  default boolean validate(SchedulingContext context) {
    return true;
  }

  /**
   * Nature de la contrainte (HARD ou SOFT)
   *
   * @return HARD pour une contrainte absolue, SOFT pour une contrainte avec pénalité
   */
  ConstraintNature getNature();
}
