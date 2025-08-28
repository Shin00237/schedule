package com.cricri.constraints;

import com.cricri.service.SchedulingContext;

public interface Constraint {
  /**
   * Applique la contrainte au modèle OR-Tools
   *
   * @param context Le contexte contenant les variables et données
   */
  void apply(SchedulingContext context);

  /**
   * Nom unique de la contrainte pour debugging
   */
  String getName();

  /**
   * Validation des prérequis avant application
   */
  default boolean validate(SchedulingContext context) {
    return true;
  }

  /**
   * Priorité d'application (plus faible = appliqué en premier)
   */
  default int getPriority() {
    return 0;
  }
}