package com.cricri.factory;

import com.cricri.constraints.AssignmentHoursConstraint;
import com.cricri.constraints.Constraint;
import com.cricri.constraints.MaxHoursPerWeekConstraint;
import com.cricri.constraints.MaximizeWorkingHoursConstraint;
import com.cricri.constraints.MinimumCoverageConstraint;
import com.cricri.constraints.MinimumRestConstraint;
import com.cricri.constraints.MinimumRestDaysConstraint;
import com.cricri.constraints.config.ConstraintConfig;

/**
 * Factory pour créer des contraintes à partir de configurations.
 *
 * <p>Cette classe centralise la logique de création des contraintes et permet une configuration
 * flexible depuis une interface graphique ou des fichiers de configuration.
 *
 * <p>Chaque type de contrainte a ses propres paramètres attendus dans la Map de la
 * ConstraintConfig. Voir la documentation de chaque case pour connaître les paramètres requis.
 */
public class ConstraintFactory {

  /**
   * Crée une contrainte à partir de sa configuration.
   *
   * @param config La configuration complète de la contrainte
   * @return Une instance de contrainte configurée
   * @throws IllegalArgumentException si le type est inconnu ou si les paramètres sont invalides
   */
  public static Constraint create(ConstraintConfig config) {
    return switch (config.type()) {
      case MINIMUM_COVERAGE -> createMinimumCoverageConstraint(config);
      case ASSIGNMENT_HOURS -> createAssignmentHoursConstraint(config);
      case MINIMUM_REST -> createMinimumRestConstraint(config);
      case MINIMUM_REST_DAYS -> createMinimumRestDaysConstraint(config);
      case MAX_HOURS_PER_WEEK -> createMaxHoursPerWeekConstraint(config);
      case MAXIMIZE_WORKING_HOURS -> createMaximizeWorkingHoursConstraint(config);
    };
  }

  /**
   * Crée une contrainte de couverture minimum.
   *
   * <p>Paramètres attendus : aucun
   */
  private static Constraint createMinimumCoverageConstraint(ConstraintConfig config) {
    return new MinimumCoverageConstraint(config.nature());
  }

  /**
   * Crée une contrainte de cohérence assignation-heures.
   *
   * <p>Paramètres attendus : - "minHoursPerShift" (Integer) : Heures minimum si assigné à un shift
   */
  private static Constraint createAssignmentHoursConstraint(ConstraintConfig config) {
    int minHoursPerShift = config.getIntParameter("minHoursPerShift", 240); // 4h par défaut
    return new AssignmentHoursConstraint(minHoursPerShift, config.nature());
  }

  /**
   * Crée une contrainte de repos minimum entre shifts.
   *
   * <p>Paramètres attendus : - "minRestHours" (Integer) : Heures de repos minimum entre deux shifts
   */
  private static Constraint createMinimumRestConstraint(ConstraintConfig config) {
    int minRestHours = config.getIntParameter("minRestHours", 11);
    return new MinimumRestConstraint(minRestHours, config.nature());
  }

  /**
   * Crée une contrainte de jours de repos minimum par semaine.
   *
   * <p>Paramètres attendus : - "minRestDaysPerWeek" (Integer) : Nombre minimum de jours de repos
   * par semaine
   */
  private static Constraint createMinimumRestDaysConstraint(ConstraintConfig config) {
    int minRestDaysPerWeek = config.getIntParameter("minRestDaysPerWeek", 2);
    return new MinimumRestDaysConstraint(minRestDaysPerWeek, config.nature());
  }

  /**
   * Crée une contrainte d'heures maximum par semaine.
   *
   * <p>Paramètres attendus : - "maxHoursPerWeek" (Integer) : Heures maximum par semaine en minutes
   */
  private static Constraint createMaxHoursPerWeekConstraint(ConstraintConfig config) {
    int maxHoursPerWeek = config.getIntParameter("maxHoursPerWeek", 39 * 60); // 39h par défaut
    return new MaxHoursPerWeekConstraint(maxHoursPerWeek, config.nature());
  }

  /**
   * Crée une contrainte de maximisation des heures travaillées.
   *
   * <p>Paramètres attendus : - "weekdayMultiplier" (Integer, optionnel) : Multiplicateur de
   * préférence pour jours de semaine (défaut: 2)
   */
  private static Constraint createMaximizeWorkingHoursConstraint(ConstraintConfig config) {
    int weekdayMultiplier = config.getIntParameter("weekdayMultiplier", 2);
    return new MaximizeWorkingHoursConstraint(weekdayMultiplier, config.nature());
  }
}
