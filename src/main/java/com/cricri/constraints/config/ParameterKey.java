package com.cricri.constraints.config;

/**
 * Énumération type-safe pour les clés de paramètres des contraintes.
 *
 * <p>Élimine les magic strings et fournit : - Sécurité au compile-time - Auto-completion dans l'IDE
 * - Valeurs par défaut centralisées - Documentation intégrée
 */
public enum ParameterKey {

  // Paramètres pour MaxHoursPerWeekConstraint
  MAX_HOURS_PER_WEEK(Integer.class, 39 * 60, "Limite hebdomadaire en minutes"),

  // Paramètres pour MaximizeWorkingHoursConstraint
  WEEKDAY_MULTIPLIER(Integer.class, 2, "Multiplicateur de bonus pour les jours de semaine"),

  // Paramètres pour MinimumRestConstraint
  MIN_REST_HOURS(Integer.class, 11, "Nombre minimum d'heures de repos entre deux shifts"),

  // Paramètres pour MinimumRestDaysConstraint
  MIN_REST_DAYS_PER_WEEK(Integer.class, 2, "Nombre minimum de jours de repos par semaine"),

  // Paramètres pour AssignmentHoursConstraint
  MIN_HOURS_PER_SHIFT(Integer.class, 240, "Durée minimum d'un shift en minutes (4h par défaut)"),

  // Paramètres pour ShiftOverlapConstraint
  MIN_OVERLAP_EMPLOYEES(Integer.class, 1, "Nombre minimum d'employés en chevauchement"),

  // Paramètres pour BlockShiftForEmployeeConstraint
  BLOCKED_ASSIGNMENTS(
      java.util.Map.class,
      java.util.Map.of(),
      "Map des assignations bloquées : employeeId → liste de shiftIds");

  private final Class<?> type;
  private final Object defaultValue;
  private final String description;

  ParameterKey(Class<?> type, Object defaultValue, String description) {
    this.type = type;
    this.defaultValue = defaultValue;
    this.description = description;
  }

  /**
   * @return Le type attendu pour ce paramètre
   */
  public Class<?> getType() {
    return type;
  }

  /**
   * @return La valeur par défaut pour ce paramètre
   */
  @SuppressWarnings("unchecked")
  public <T> T getDefaultValue() {
    return (T) defaultValue;
  }

  /**
   * @return La description du paramètre
   */
  public String getDescription() {
    return description;
  }

  /**
   * @return Le nom de la clé (pour compatibilité avec le système existant)
   */
  public String getKeyName() {
    return name().toLowerCase().replace("_", "");
  }
}
