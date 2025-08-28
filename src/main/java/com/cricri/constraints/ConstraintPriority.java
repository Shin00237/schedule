package com.cricri.constraints;

/**
 * Enumération des priorités des contraintes dans le système de planification.
 *
 * <p>Les contraintes sont appliquées dans l'ordre croissant de priorité (valeurs négatives
 * d'abord). Une priorité plus faible (valeur numérique plus petite) indique une contrainte plus
 * critique qui doit être appliquée en premier.
 *
 * <p>Cette énumération facilite la configuration depuis une interface graphique en proposant des
 * choix explicites plutôt que des valeurs numériques arbitraires.
 */
public enum ConstraintPriority {

  /**
   * Contraintes fondamentales - Les plus critiques, doivent absolument être respectées. Exemples :
   * couverture minimum des shifts, assignation de base
   */
  FUNDAMENTAL(-10),

  /**
   * Contraintes de cohérence - Assurent la cohérence du modèle. Exemples : cohérence
   * assignation-heures, intégrité des données
   */
  CONSISTENCY(-5),

  /**
   * Contraintes de sécurité - Protègent la santé et sécurité des employés. Exemples : temps de
   * repos minimum, limites légales
   */
  SAFETY(-3),

  /**
   * Contraintes normales - Règles standard de planification. Exemples : heures maximum par semaine,
   * répartition équitable
   */
  NORMAL(0),

  /**
   * Contraintes de confort - Améliorent les conditions de travail. Exemples : préférences de jours
   * de repos, éviter les doubles shifts
   */
  COMFORT(5),

  /**
   * Contraintes d'optimisation - Améliorent l'efficacité mais non critiques. Exemples :
   * minimisation des coûts, préférences secondaires
   */
  OPTIMIZATION(10);

  private final int value;

  ConstraintPriority(int value) {
    this.value = value;
  }

  /**
   * @return La valeur numérique de cette priorité
   */
  public int getValue() {
    return value;
  }

  /**
   * @return Une description lisible de cette priorité
   */
  public String getDescription() {
    return switch (this) {
      case FUNDAMENTAL -> "Fondamental - Critiques, doivent être respectées";
      case CONSISTENCY -> "Cohérence - Intégrité du modèle";
      case SAFETY -> "Sécurité - Santé et légal";
      case NORMAL -> "Normal - Règles standard";
      case COMFORT -> "Confort - Amélioration des conditions";
      case OPTIMIZATION -> "Optimisation - Efficacité non critique";
    };
  }

  /**
   * Trouve la priorité correspondant à une valeur numérique.
   *
   * @param value La valeur numérique
   * @return La priorité correspondante
   * @throws IllegalArgumentException si aucune priorité ne correspond
   */
  public static ConstraintPriority fromValue(int value) {
    for (ConstraintPriority priority : values()) {
      if (priority.value == value) {
        return priority;
      }
    }
    throw new IllegalArgumentException("Aucune priorité trouvée pour la valeur: " + value);
  }
}
