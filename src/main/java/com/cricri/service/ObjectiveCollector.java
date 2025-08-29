package com.cricri.service;

import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * Collecteur stateless pour accumuler les termes d'objectif de toutes les contraintes SOFT.
 *
 * <p>Cette classe permet aux contraintes souples de contribuer à un objectif global unifié
 * sans avoir besoin d'un état global mutable dans le SchedulingContext.
 *
 * <p>Fonctionnement :
 * 1. Chaque contrainte SOFT ajoute ses termes via addTerm()
 * 2. À la fin, build() construit l'expression LinearExpr complète
 * 3. Un seul model.maximize() est appelé avec l'objectif unifié
 *
 * <p>Thread-safe : Les méthodes addTerm() sont synchronisées pour permettre
 * un traitement parallèle des contraintes.
 */
public class ObjectiveCollector {
    private final List<ObjectiveTerm> terms = new ArrayList<>();
    
    /**
     * Ajoute un terme pondéré à l'objectif global.
     * 
     * <p>Cette méthode est thread-safe et peut être appelée par plusieurs contraintes
     * en parallèle.
     *
     * @param variable La variable OR-Tools à inclure dans l'objectif
     * @param weight Le poids de cette variable (positif = maximiser, négatif = minimiser)
     */
    public synchronized void addTerm(IntVar variable, long weight) {
        if (variable == null) {
            throw new IllegalArgumentException("La variable ne peut pas être null");
        }
        if (weight == 0) {
            // Optimisation : ignorer les termes avec poids 0
            return;
        }
        
        terms.add(new ObjectiveTerm(variable, weight));
    }
    
    /**
     * Construit l'expression d'objectif finale à partir de tous les termes ajoutés.
     * 
     * <p>Cette méthode peut être appelée plusieurs fois et retournera toujours
     * la même expression basée sur les termes actuels.
     *
     * @return L'expression LinearExpr combinant tous les termes, ou une constante 0 si aucun terme
     */
    public LinearExpr build() {
        if (terms.isEmpty()) {
            return LinearExpr.constant(0);
        }
        
        LinearExprBuilder builder = LinearExpr.newBuilder();
        for (ObjectiveTerm term : terms) {
            builder.addTerm(term.variable, term.weight);
        }
        return builder.build();
    }
    
    /**
     * Retourne le nombre de termes actuellement dans le collecteur.
     * 
     * @return Le nombre de termes d'objectif
     */
    public int getTermCount() {
        return terms.size();
    }
    
    /**
     * Indique si le collecteur contient des termes d'objectif.
     * 
     * @return true s'il y a au moins un terme, false sinon
     */
    public boolean isEmpty() {
        return terms.isEmpty();
    }
    
    /**
     * Représentation interne d'un terme d'objectif.
     * 
     * @param variable La variable OR-Tools
     * @param weight Le poids de cette variable dans l'objectif
     */
    private record ObjectiveTerm(IntVar variable, long weight) {}
}