package com.cricri.constraints;

/**
 * Enumération des types de contraintes disponibles dans le système de planification.
 *
 * <p>Chaque type correspond à une classe de contrainte spécifique qui peut être appliquée comme
 * contrainte dure (HARD) ou souple (SOFT) selon la configuration.
 */
public enum ConstraintType {

  /**
   * Assure qu'un nombre minimum d'employés est assigné à chaque shift. Généralement utilisé comme
   * contrainte HARD.
   */
  MINIMUM_COVERAGE,

  /**
   * Assure la cohérence entre les assignations et les heures travaillées. Toujours utilisé comme
   * contrainte HARD pour maintenir l'intégrité.
   */
  ASSIGNMENT_HOURS,

  /**
   * Garantit un temps de repos minimum entre deux shifts consécutifs. Généralement utilisé comme
   * contrainte HARD pour des raisons légales.
   */
  MINIMUM_REST,

  /**
   * Assure un nombre minimum de jours de repos par semaine. Peut être HARD (légal) ou SOFT
   * (confort).
   */
  MINIMUM_REST_DAYS,

  /**
   * Limite le nombre d'heures maximum par semaine pour chaque employé. Peut être HARD (légal) ou
   * SOFT (préférence).
   */
  MAX_HOURS_PER_WEEK,

  /**
   * Favorise l'assignation durant les jours de semaine plutôt que le weekend. Généralement utilisé
   * comme contrainte SOFT.
   */
  WEEKDAY_PREFERENCE,

  /**
   * Assure la cohérence des variables de jours travaillés. Généralement utilisé comme contrainte
   * HARD pour l'intégrité du modèle.
   */
  WORKING_DAYS,

  /**
   * Maximise les heures travaillées avec préférence pour les jours de semaine. Reproduit l'ancienne
   * logique addWeekdayStaffingObjective(). Généralement utilisé comme contrainte SOFT pour
   * optimisation.
   */
  MAXIMIZE_WORKING_HOURS
}
