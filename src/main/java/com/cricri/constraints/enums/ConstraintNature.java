package com.cricri.constraints.enums;

/**
 * Définit la nature d'une contrainte dans le système de planification.
 *
 * <p>Cette énumération permet de distinguer entre les contraintes qui doivent absolument être
 * respectées (HARD) et celles qui peuvent être violées moyennant une pénalité (SOFT).
 */
public enum ConstraintNature {

  /**
   * Contrainte dure : doit absolument être respectée.
   *
   * <p>Les contraintes HARD sont implémentées comme des contraintes classiques dans OR-Tools et
   * rendent la solution infaisable si elles ne peuvent pas être satisfaites.
   *
   * <p>Exemples typiques : - Couverture minimum obligatoire - Respect des temps de repos légaux -
   * Cohérence des données du modèle
   */
  HARD,

  /**
   * Contrainte souple : peut être violée moyennant une pénalité.
   *
   * <p>Les contraintes SOFT sont implémentées avec des variables de violation et des pénalités dans
   * la fonction objectif. Le solveur essaiera de les respecter mais peut les violer si nécessaire
   * pour trouver une solution.
   *
   * <p>Exemples typiques : - Préférences d'horaires - Limites souples d'heures de travail -
   * Optimisations de confort
   */
  SOFT
}
