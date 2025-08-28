package com.cricri.objectives;

import com.cricri.service.SchedulingContext;

public interface ObjectiveFunction {
  /**
   * Applique la fonction objectif au modèle
   *
   * @param context Le contexte contenant les variables et données
   */
  void apply(SchedulingContext context);

  /**
   * Nom de la fonction objectif
   */
  String getName();

  /**
   * Priorité/poids de l'objectif (plus élevé = plus important)
   */
  default int getWeight() {
    return 1;
  }
}