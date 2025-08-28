package com.cricri.constraints;

import com.cricri.model.Shift;
import com.cricri.service.SchedulingContext;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;

/**
 * Contrainte de préférence pour les jours de semaine.
 * 
 * Cette contrainte favorise l'assignation durant les jours de semaine
 * (lundi à vendredi) plutôt que le weekend (samedi, dimanche).
 * 
 * Remplace l'ancien WeekdayPreferenceObjective du package objectives.
 */
public class WeekdayPreferenceConstraint implements Constraint {
    private final int multiplier;
    private final ConstraintNature nature;
    private final ConstraintPriority priority;

    public WeekdayPreferenceConstraint() {
        this(1, ConstraintNature.SOFT, ConstraintPriority.COMFORT);
    }

    public WeekdayPreferenceConstraint(int multiplier) {
        this(multiplier, ConstraintNature.SOFT, ConstraintPriority.COMFORT);
    }

    public WeekdayPreferenceConstraint(int multiplier, ConstraintNature nature, ConstraintPriority priority) {
        this.multiplier = multiplier;
        this.nature = nature;
        this.priority = priority;
    }

    @Override
    public void apply(SchedulingContext context) {
        context.ensureVariablesInitialized();

        if (nature == ConstraintNature.HARD) {
            // Contrainte dure : interdire complètement les assignations le weekend
            applyHardConstraint(context);
        } else {
            // Contrainte souple : ajouter une pénalité pour les assignations weekend
            applySoftConstraint(context);
        }
    }

    /**
     * Applique la contrainte en mode HARD : interdit les assignations le weekend.
     */
    private void applyHardConstraint(SchedulingContext context) {
        for (int e = 0; e < context.getEmployeeCount(); e++) {
            for (int s = 0; s < context.getShiftCount(); s++) {
                Shift shift = context.getShifts().get(s);
                if (isWeekend(shift)) {
                    // Forcer l'assignation à 0 (interdire)
                    context.getModel().addEquality(context.getAssignments()[e][s], 0);
                }
            }
        }
    }

    /**
     * Applique la contrainte en mode SOFT : ajoute une pénalité pour les weekends.
     */
    private void applySoftConstraint(SchedulingContext context) {
        // Créer une expression pour compter les assignations de weekend
        LinearExprBuilder weekendAssignments = LinearExpr.newBuilder();

        for (int e = 0; e < context.getEmployeeCount(); e++) {
            for (int s = 0; s < context.getShiftCount(); s++) {
                Shift shift = context.getShifts().get(s);
                if (isWeekend(shift)) {
                    // Ajouter cette assignation au compteur de weekend
                    weekendAssignments.addTerm(context.getAssignments()[e][s], multiplier);
                }
            }
        }

        // En mode SOFT, on utiliserait normalement model.minimize(weekendAssignments)
        // mais pour l'instant on ne fait rien car OR-Tools CP-SAT ne supporte
        // pas directement les contraintes souples.
        // TODO: Implémenter avec des variables de violation si nécessaire
    }

    /**
     * Détermine si un shift tombe un weekend.
     */
    private boolean isWeekend(Shift shift) {
        int dayInWeek = shift.day().getDayInWeek(); // 0=lundi, 6=dimanche
        return dayInWeek == 5 || dayInWeek == 6; // samedi ou dimanche
    }

    @Override
    public String getName() {
        return "WeekdayPreference(×" + multiplier + ", " + nature + ")";
    }

    @Override
    public ConstraintNature getNature() {
        return nature;
    }

    @Override
    public ConstraintPriority getPriority() {
        return priority;
    }
}