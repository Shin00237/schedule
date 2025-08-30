package com.cricri.constraints;

import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.config.ParameterKey;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ObjectiveWeight;
import com.cricri.model.Shift;
import com.cricri.service.ObjectiveCollector;
import com.cricri.service.SchedulingContext;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;

/**
 * Contrainte de chevauchement entre shifts pour assurer la continuité de service.
 *
 * <p>Cette contrainte s'applique aux shifts qui se chevauchent temporellement
 * pour garantir qu'au moins N employés soient présents dans les deux shifts
 * pendant la période de chevauchement pour assurer la transmission d'informations.
 *
 * @param minOverlapEmployees Nombre minimum d'employés qui doivent être présents dans les deux shifts chevauchants
 *
 * Exemple d'usage:
 * scheduler.withConstraint(new ShiftOverlapConstraint(1, ConstraintNature.HARD, null));
 *
 * Complexité: O(shifts² × employees)
 */
public class ShiftOverlapConstraint implements Constraint {

    private final int minOverlapEmployees;
    private final ConstraintNature nature;
    private final ConstraintConfig config;

    public ShiftOverlapConstraint(ConstraintConfig config) {
        this.minOverlapEmployees = config.getParameter(ParameterKey.MIN_OVERLAP_EMPLOYEES);
        this.nature = config.nature();
        this.config = config;
    }

    @Override
    public void applyHardConstraint(SchedulingContext context) {
        context.ensureVariablesInitialized();

        int shiftCount = context.getShiftCount();
        int employeeCount = context.getEmployeeCount();

        // Pour chaque paire de shifts qui se chevauchent
        for (int s1 = 0; s1 < shiftCount; s1++) {
            for (int s2 = s1 + 1; s2 < shiftCount; s2++) {
                Shift shift1 = context.getShifts().get(s1);
                Shift shift2 = context.getShifts().get(s2);

                // Vérifier s'ils se chevauchent temporellement
                if (shiftsOverlap(shift1, shift2)) {
                    // Au moins minOverlapEmployees employés doivent être dans les deux shifts
                    LinearExprBuilder overlapSum = LinearExpr.newBuilder();
                    for (int e = 0; e < employeeCount; e++) {
                        IntVar overlapVar = context.getModel().newBoolVar("overlap_e" + e + "_s" + s1 + "_s" + s2);

                        // overlapVar = assignments[e][s1] AND assignments[e][s2]
                        // overlapVar <= assignments[e][s1] ET overlapVar <= assignments[e][s2]
                        context.getModel().addLessOrEqual(overlapVar, context.getAssignments()[e][s1]);
                        context.getModel().addLessOrEqual(overlapVar, context.getAssignments()[e][s2]);

                        // overlapVar >= assignments[e][s1] + assignments[e][s2] - 1
                        LinearExpr sumAssignments = LinearExpr.newBuilder()
                            .addTerm(context.getAssignments()[e][s1], 1)
                            .addTerm(context.getAssignments()[e][s2], 1)
                            .add(-1)
                            .build();
                        context.getModel().addGreaterOrEqual(overlapVar, sumAssignments);

                        overlapSum.addTerm(overlapVar, 1);
                    }

                    context.getModel().addGreaterOrEqual(overlapSum.build(), minOverlapEmployees);
                }
            }
        }
    }

    @Override
    public void applySoftConstraint(SchedulingContext context, ObjectiveCollector collector) {
        context.ensureVariablesInitialized();

        int shiftCount = context.getShiftCount();
        int employeeCount = context.getEmployeeCount();

        ObjectiveWeight weight = (config != null) ? config.getObjectiveWeight() : ObjectiveWeight.MINIMIZE_MEDIUM;

        // Pour chaque paire de shifts qui se chevauchent
        for (int s1 = 0; s1 < shiftCount; s1++) {
            for (int s2 = s1 + 1; s2 < shiftCount; s2++) {
                Shift shift1 = context.getShifts().get(s1);
                Shift shift2 = context.getShifts().get(s2);

                if (shiftsOverlap(shift1, shift2)) {
                    // Variable de violation pour cette paire de shifts
                    IntVar violationVar = context.getModel().newIntVar(0, employeeCount,
                        "overlap_violation_s" + s1 + "_s" + s2);

                    // Compter les employés qui sont dans les DEUX shifts (chevauchement réel)
                    LinearExprBuilder overlapCount = LinearExpr.newBuilder();
                    for (int e = 0; e < employeeCount; e++) {
                        IntVar overlapVar = context.getModel().newBoolVar("overlap_e" + e + "_s" + s1 + "_s" + s2);

                        // overlapVar = assignments[e][s1] AND assignments[e][s2]
                        // overlapVar <= assignments[e][s1] ET overlapVar <= assignments[e][s2]
                        context.getModel().addLessOrEqual(overlapVar, context.getAssignments()[e][s1]);
                        context.getModel().addLessOrEqual(overlapVar, context.getAssignments()[e][s2]);

                        // overlapVar >= assignments[e][s1] + assignments[e][s2] - 1
                        LinearExpr sumAssignments = LinearExpr.newBuilder()
                            .addTerm(context.getAssignments()[e][s1], 1)
                            .addTerm(context.getAssignments()[e][s2], 1)
                            .add(-1)
                            .build();
                        context.getModel().addGreaterOrEqual(overlapVar, sumAssignments);

                        overlapCount.addTerm(overlapVar, 1);
                    }

                    // violationVar = max(0, minOverlapEmployees - overlapCount)
                    LinearExprBuilder deficitBuilder = LinearExpr.newBuilder();
                    deficitBuilder.add(minOverlapEmployees);
                    deficitBuilder.addTerm(overlapCount.build(), -1);
                    LinearExpr deficit = deficitBuilder.build();
                    context.getModel().addMaxEquality(violationVar,
                        new LinearExpr[]{
                            LinearExpr.constant(0),
                            deficit
                        });

                    // Ajouter la violation à l'objectif (avec poids négatif pour minimiser)
                    collector.addTerm(violationVar, weight.getWeight());
                }
            }
        }
    }

    @Override
    public String getName() {
        return "ShiftOverlap(minOverlap=" + minOverlapEmployees + "emp, " + nature + ")";
    }

    @Override
    public ConstraintNature getNature() {
        return nature;
    }

    /**
     * Vérifie si deux shifts se chevauchent temporellement et sont sur le même jour.
     *
     * @param shift1 Premier shift
     * @param shift2 Deuxième shift
     * @return true si les shifts se chevauchent
     */
    private boolean shiftsOverlap(Shift shift1, Shift shift2) {
        // Vérifier d'abord qu'ils sont sur le même jour
        if (!shift1.day().equals(shift2.day())) {
            return false;
        }

        // Vérifier le chevauchement temporel
        int start1 = shift1.type().heureDebut().toSecondOfDay() / 60;
        int end1 = shift1.type().heureFin().toSecondOfDay() / 60;
        int start2 = shift2.type().heureDebut().toSecondOfDay() / 60;
        int end2 = shift2.type().heureFin().toSecondOfDay() / 60;

        // Gérer le cas des shifts qui passent minuit (end < start)
        if (end1 < start1) {
            end1 += 24 * 60; // Ajouter 24h
        }
        if (end2 < start2) {
            end2 += 24 * 60; // Ajouter 24h
        }

        // Vérifier s'il y a chevauchement : start1 < end2 ET start2 < end1
        return start1 < end2 && start2 < end1;
    }
}
