package com.cricri.constraints.enums;

/**
 * Poids d'objectif pour les contraintes SOFT dans le système d'optimisation unifié.
 *
 * <p>Cette enum définit les différents niveaux de priorité pour les contraintes souples :
 * - Poids positifs pour maximiser (ex: heures travaillées)
 * - Poids négatifs pour minimiser (ex: violations de contraintes)
 *
 * <p>Les valeurs sont calibrées pour permettre une hiérarchisation claire :
 * CRITICAL > HIGH > MEDIUM > LOW
 */
public enum ObjectiveWeight {
    // Pour maximiser (poids positifs)
    /** Objectif critique - priorité maximale (ex: MaximizeWorkingHours) */
    MAXIMIZE_CRITICAL(10000),
    /** Objectif important - priorité élevée */
    MAXIMIZE_HIGH(1000),
    /** Objectif moyen - priorité modérée */
    MAXIMIZE_MEDIUM(100),
    /** Objectif mineur - priorité faible */
    MAXIMIZE_LOW(10),
    
    // Pour minimiser (poids négatifs) - APPROCHE SIMPLE
    /** Violation critique - pénalité maximale (ex: sécurité, repos minimum) */
    MINIMIZE_CRITICAL(-10000),
    /** Violation importante - pénalité élevée (ex: légal, heures max) */
    MINIMIZE_HIGH(-1000),
    /** Violation moyenne - pénalité modérée (ex: confort, jours de repos) */
    MINIMIZE_MEDIUM(-100),
    /** Violation mineure - pénalité faible */
    MINIMIZE_LOW(-10),
    
    /** Pas d'objectif - utilisé pour les contraintes HARD */
    DISABLED(0);

    private final long weight;

    ObjectiveWeight(long weight) {
        this.weight = weight;
    }

    /**
     * Retourne le poids numérique de cet objectif.
     * 
     * @return Le poids (positif pour maximiser, négatif pour minimiser, 0 pour désactivé)
     */
    public long getWeight() {
        return weight;
    }

    /**
     * Indique si ce poids correspond à un objectif de maximisation.
     * 
     * @return true si le poids est positif (maximisation)
     */
    public boolean isMaximizing() {
        return weight > 0;
    }

    /**
     * Indique si ce poids correspond à un objectif de minimisation.
     * 
     * @return true si le poids est négatif (minimisation)
     */
    public boolean isMinimizing() {
        return weight < 0;
    }

    /**
     * Indique si ce poids est désactivé.
     * 
     * @return true si le poids est 0 (pas d'objectif)
     */
    public boolean isDisabled() {
        return weight == 0;
    }
}