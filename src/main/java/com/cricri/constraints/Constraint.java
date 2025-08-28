package com.cricri.constraints;

import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ConstraintPriority;
import com.cricri.service.SchedulingContext;

public interface Constraint {
  /**
   * Applique la contrainte au modèle OR-Tools
   *
   * @param context Le contexte contenant les variables et données
   */
  void apply(SchedulingContext context);

  /** Nom unique de la contrainte pour debugging */
  String getName();

  /** Validation des prérequis avant application */
  default boolean validate(SchedulingContext context) {
    return true;
  }

  /** Priorité d'application (plus faible = appliqué en premier) */
  default ConstraintPriority getPriority() {
    return ConstraintPriority.NORMAL;
  }

  /**
   * Nature de la contrainte (HARD ou SOFT)
   *
   * @return HARD pour une contrainte absolue, SOFT pour une contrainte avec pénalité
   */
  ConstraintNature getNature();

  /**
   * Poids de pénalité pour les contraintes souples.
   *
   * <p>Utilise automatiquement la valeur de la priorité comme poids de pénalité. Plus la priorité
   * est critique (valeur négative), plus la pénalité est forte. Ce poids n'est utilisé que si
   * getNature() retourne SOFT.
   *
   * <p>Exemples : - SAFETY(-3) → pénalité forte de -3 (très important de respecter) - COMFORT(5) →
   * pénalité faible de 5 (moins critique)
   *
   * @return Le poids de pénalité basé sur la priorité (0 si HARD)
   */
  default int getPenaltyWeight() {
    return getNature() == ConstraintNature.SOFT ? getPriority().getValue() : 0;
  }
}
