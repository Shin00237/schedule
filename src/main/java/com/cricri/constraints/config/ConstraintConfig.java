package com.cricri.constraints.config;

import java.util.Map;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ConstraintType;
import com.cricri.constraints.enums.ObjectiveWeight;

/**
 * Configuration complète d'une contrainte dans le système de planification.
 *
 * <p>Cette classe record encapsule toutes les informations nécessaires pour créer et configurer une
 * contrainte : - Le type de contrainte à appliquer - Sa nature (HARD ou SOFT) - Ses paramètres
 * spécifiques
 *
 * <p>Cette approche permet une configuration flexible depuis une interface graphique ou des
 * fichiers de configuration.
 *
 * @param type Le type de contrainte (ex: MAX_HOURS_PER_WEEK)
 * @param nature La nature de la contrainte (HARD ou SOFT)
 * @param parameters Les paramètres spécifiques à ce type de contrainte
 */
public record ConstraintConfig(
    ConstraintType type, ConstraintNature nature, Map<String, Object> parameters) {

  /**
   * Constructeur de convenance pour une contrainte sans paramètres.
   *
   * @param type Le type de contrainte
   * @param nature La nature (HARD/SOFT)
   * @return Une configuration avec une map de paramètres vide
   */
  public static ConstraintConfig of(ConstraintType type, ConstraintNature nature) {
    return new ConstraintConfig(type, nature, Map.of());
  }

  /**
   * Constructeur de convenance pour une contrainte avec un seul paramètre.
   *
   * @param type Le type de contrainte
   * @param nature La nature (HARD/SOFT)
   * @param parameterName Nom du paramètre
   * @param parameterValue Valeur du paramètre
   * @return Une configuration avec un paramètre
   */
  public static ConstraintConfig of(
      ConstraintType type, ConstraintNature nature, String parameterName, Object parameterValue) {
    return new ConstraintConfig(type, nature, Map.of(parameterName, parameterValue));
  }

  /**
   * Validation basique de la configuration.
   *
   * @throws IllegalArgumentException si la configuration est invalide
   */
  public ConstraintConfig {
    if (type == null) {
      throw new IllegalArgumentException("Le type de contrainte ne peut pas être null");
    }
    if (nature == null) {
      throw new IllegalArgumentException("La nature de contrainte ne peut pas être null");
    }
    if (parameters == null) {
      throw new IllegalArgumentException(
          "Les paramètres ne peuvent pas être null (utiliser Map.of() pour une map vide)");
    }
  }

  /**
   * Récupère un paramètre typé avec une valeur par défaut.
   *
   * @param <T> Le type attendu du paramètre
   * @param key La clé du paramètre
   * @param defaultValue La valeur par défaut si le paramètre n'existe pas
   * @param expectedType La classe du type attendu
   * @return La valeur du paramètre ou la valeur par défaut
   * @throws ClassCastException si le paramètre n'est pas du type attendu
   */
  @SuppressWarnings("unchecked")
  public <T> T getParameter(String key, T defaultValue, Class<T> expectedType) {
    Object value = parameters.get(key);
    if (value == null) {
      return defaultValue;
    }
    if (!expectedType.isInstance(value)) {
      throw new ClassCastException(
          String.format(
              "Le paramètre '%s' devrait être de type %s mais est de type %s",
              key, expectedType.getSimpleName(), value.getClass().getSimpleName()));
    }
    return (T) value;
  }

  /** Récupère un paramètre entier avec valeur par défaut. */
  public int getIntParameter(String key, int defaultValue) {
    return getParameter(key, defaultValue, Integer.class);
  }

  /** Récupère un paramètre chaîne avec valeur par défaut. */
  public String getStringParameter(String key, String defaultValue) {
    return getParameter(key, defaultValue, String.class);
  }

  /** Récupère un paramètre booléen avec valeur par défaut. */
  public boolean getBooleanParameter(String key, boolean defaultValue) {
    return getParameter(key, defaultValue, Boolean.class);
  }

  /**
   * Récupère le poids d'objectif pour les contraintes SOFT.
   *
   * <p>Cette méthode centralise la logique de détermination des poids d'objectif :
   * - Pour les contraintes HARD : retourne toujours DISABLED
   * - Pour les contraintes SOFT : utilise le paramètre "objectiveWeight" ou une valeur par défaut
   *
   * @return Le poids d'objectif approprié selon le type et la nature de la contrainte
   */
  public ObjectiveWeight getObjectiveWeight() {
    if (nature == ConstraintNature.HARD) {
      return ObjectiveWeight.DISABLED;
    }

    // Récupérer depuis les paramètres ou valeur par défaut selon le type
    return getParameter("objectiveWeight", getDefaultWeight(), ObjectiveWeight.class);
  }

  /**
   * Détermine le poids par défaut selon le type de contrainte SOFT.
   *
   * <p>Cette méthode encode la logique métier de prioritisation des objectifs :
   * - MAXIMIZE_WORKING_HOURS : objectif principal (CRITICAL)
   * - MINIMUM_REST : violation critique de sécurité (MINIMIZE_CRITICAL)
   * - MAX_HOURS_PER_WEEK : violation importante légale (MINIMIZE_HIGH)
   * - MINIMUM_REST_DAYS : violation de confort (MINIMIZE_MEDIUM)
   * - Autres : violations mineures (MINIMIZE_LOW)
   *
   * @return Le poids par défaut pour ce type de contrainte
   */
  private ObjectiveWeight getDefaultWeight() {
    return switch (type) {
      case MAXIMIZE_WORKING_HOURS -> ObjectiveWeight.MAXIMIZE_CRITICAL;
      case MINIMUM_REST -> ObjectiveWeight.MINIMIZE_CRITICAL;
      case MAX_HOURS_PER_WEEK -> ObjectiveWeight.MINIMIZE_HIGH;
      case MINIMUM_REST_DAYS -> ObjectiveWeight.MINIMIZE_MEDIUM;
      case MINIMUM_COVERAGE, ASSIGNMENT_HOURS -> ObjectiveWeight.MINIMIZE_LOW;
    };
  }
}
