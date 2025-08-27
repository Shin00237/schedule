package com.cricri.service;

import java.util.List;
import java.util.Map;

import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;

import lombok.Data;


@Data
public class ShiftScheduler {
    private CpModel model;
    private List<Employee> employees;
    private List<Shift> shifts;
    private Map<String, Integer> shiftIndexMap;  // pour retrouver facilement l'index d'un shift
    private int maxHoursPerWeek = 40 * 60; // 40h en minutes par défaut
    private int minRestHours = 11; // repos minimum entre shifts en heures
    
    // Variables OR-Tools
    private BoolVar[][] assignments;
    private IntVar[] employeesPerShift;
    private BoolVar[][] workingDays;
    private IntVar[] hoursPerEmployee;
    private IntVar[][] hoursPerEmployeePerWeek; // [employé][semaine]

    public void buildModel() {
        model = new CpModel();
        
        // Créer les variables
        assignments = new BoolVar[employees.size()][shifts.size()];
        for (int e = 0; e < employees.size(); e++) {
            for (int s = 0; s < shifts.size(); s++) {
                assignments[e][s] = model.newBoolVar("assign_e" + e + "_s" + s);
            }
        }
        
        // Variables pour compter les employés
        employeesPerShift = new IntVar[shifts.size()];
        for (int s = 0; s < shifts.size(); s++) {
            employeesPerShift[s] = model.newIntVar(0, employees.size(), 
                                                   "nbEmployees_s" + s);
        }

        // Variables pour les heures par employé par semaine
        int nbWeeks = calculateNumberOfWeeks();
        hoursPerEmployeePerWeek = new IntVar[employees.size()][nbWeeks];
        for (int e = 0; e < employees.size(); e++) {
            for (int w = 0; w < nbWeeks; w++) {
                hoursPerEmployeePerWeek[e][w] = model.newIntVar(0, maxHoursPerWeek, 
                    "hours_e" + e + "_w" + w);
            }
        }
        
        // workingDays = new BoolVar[employees.size()][nombreJoursDansMois];
        // for (int e = 0; e < employees.size(); e++) {
        //     for (int d = 0; d < nombreJoursDansMois; d++) {
        //         workingDays[e][d] = model.newBoolVar("workDay_e" + e + "_d" + (d+1));
        //     }
        // }

        
        // Contraintes
        addMinimumEmployeesConstraint();
        addMaxHoursPerWeekConstraint();
        addMinimumRestConstraint();
    }

    public void addMinimumEmployeesConstraint() {
        // Pour chaque shift
        for (int s = 0; s < shifts.size(); s++) {
            Shift shift = shifts.get(s);
            
            // Compter le nombre d'employés assignés
            LinearExprBuilder sumEmployees = LinearExpr.newBuilder();
            for (int e = 0; e < employees.size(); e++) {
                sumEmployees.add(assignments[e][s]);
            }
            
            // Ajouter la contrainte : somme >= minimum requis
            model.addGreaterOrEqual(sumEmployees, shift.minEmployes());
            
            // Et aussi <= maximum si défini
            if (shift.maxEmployes() > 0) {
                model.addLessOrEqual(sumEmployees, shift.maxEmployes());
            }
            
            // Lier avec la variable employeesPerShift
            model.addEquality(employeesPerShift[s], sumEmployees);
        }
    }

    private int calculateNumberOfWeeks() {
        if (shifts.isEmpty()) return 1;
        
        int maxDay = shifts.stream()
            .mapToInt(Shift::jour)
            .max()
            .orElse(7);
        
        // Calculer le nombre de semaines (arrondi vers le haut)
        return (maxDay + 6) / 7;
    }

    public void addMaxHoursPerWeekConstraint() {
        int nbWeeks = calculateNumberOfWeeks();
        
        for (int e = 0; e < employees.size(); e++) {
            for (int w = 0; w < nbWeeks; w++) {
                LinearExprBuilder hoursInWeek = LinearExpr.newBuilder();
                
                // Pour chaque shift de cette semaine
                for (int s = 0; s < shifts.size(); s++) {
                    Shift shift = shifts.get(s);
                    int shiftWeek = (shift.jour() - 1) / 7; // Semaine du shift (0-indexé)
                    
                    if (shiftWeek == w) {
                        // Ajouter les minutes de ce shift si l'employé y est assigné
                        hoursInWeek.addTerm(assignments[e][s], shift.type().dureeMinutes());
                    }
                }
                
                // Lier avec la variable hoursPerEmployeePerWeek
                model.addEquality(hoursPerEmployeePerWeek[e][w], hoursInWeek);
                
                // Contrainte : ne pas dépasser le maximum d'heures par semaine
                model.addLessOrEqual(hoursPerEmployeePerWeek[e][w], maxHoursPerWeek);
            }
        }
    }

    public void addMinimumRestConstraint() {
        int minRestMinutes = minRestHours * 60; // convertir en minutes
        
        // Pour chaque employé
        for (int e = 0; e < employees.size(); e++) {
            // Pour chaque paire de shifts
            for (int s1 = 0; s1 < shifts.size(); s1++) {
                for (int s2 = 0; s2 < shifts.size(); s2++) {
                    if (s1 != s2 && hasRestConflict(s1, s2, minRestMinutes)) {
                        // Si les shifts sont en conflit, l'employé ne peut pas faire les deux
                        model.addLessOrEqual(
                            LinearExpr.newBuilder()
                                .add(assignments[e][s1])
                                .add(assignments[e][s2])
                                .build(),
                            1
                        );
                    }
                }
            }
        }
    }

    private boolean hasRestConflict(int shiftIndex1, int shiftIndex2, int minRestMinutes) {
        Shift shift1 = shifts.get(shiftIndex1);
        Shift shift2 = shifts.get(shiftIndex2);
        
        // Calculer les temps absolus en minutes depuis le début de la période
        int endTime1 = calculateAbsoluteTime(shift1.jour(), shift1.type().heureFinMinutes());
        int startTime2 = calculateAbsoluteTime(shift2.jour(), shift2.type().heureDebutMinutes());
        
        // Il y a conflit si shift2 commence moins de minRestMinutes après la fin de shift1
        return startTime2 > endTime1 && startTime2 < endTime1 + minRestMinutes;
    }

    private int calculateAbsoluteTime(int jour, int heureMinutes) {
        // Convertir en temps absolu : (jour-1) * 24h * 60min + heureMinutes
        return (jour - 1) * 24 * 60 + heureMinutes;
    }

}
