package com.cricri.constraints;

import com.cricri.constraints.enums.ConstraintNature;
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
   * @param context Le contexte contenant les variables et données
   */
  void applySoftConstraint(SchedulingContext context);

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
