package com.cricri.constraints;

/**
 * Constantes définissant les priorités des contraintes dans le système de planification.
 * 
 * Les contraintes sont appliquées dans l'ordre croissant de priorité (valeurs négatives d'abord).
 * Une priorité plus faible (valeur numérique plus petite) indique une contrainte plus critique
 * qui doit être appliquée en premier.
 * 
 * @author Claude
 */
public final class ConstraintPriority {

    /**
     * Contraintes fondamentales - Les plus critiques, doivent absolument être respectées.
     * Exemples : couverture minimum des shifts, assignation de base
     */
    public static final int FUNDAMENTAL = -10;

    /**
     * Contraintes de cohérence - Assurent la cohérence du modèle.
     * Exemples : cohérence assignation-heures, intégrité des données
     */
    public static final int CONSISTENCY = -5;

    /**
     * Contraintes de sécurité - Protègent la santé et sécurité des employés.
     * Exemples : temps de repos minimum, limites légales
     */
    public static final int SAFETY = -3;

    /**
     * Contraintes normales - Règles standard de planification.
     * Exemples : heures maximum par semaine, répartition équitable
     */
    public static final int NORMAL = 0;

    /**
     * Contraintes de confort - Améliorent les conditions de travail.
     * Exemples : préférences de jours de repos, éviter les doubles shifts
     */
    public static final int COMFORT = 5;

    /**
     * Contraintes d'optimisation - Améliorent l'efficacité mais non critiques.
     * Exemples : minimisation des coûts, préférences secondaires
     */
    public static final int OPTIMIZATION = 10;

    /**
     * Constructeur privé pour empêcher l'instanciation.
     */
    private ConstraintPriority() {
        throw new UnsupportedOperationException("Cette classe ne peut pas être instanciée");
    }
}