package com.cricri;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.model.ShiftType;
import com.cricri.model.Week;
import com.cricri.model.Day;
import com.cricri.service.ShiftScheduler;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;

public class Main {
    
    // Configuration du modèle
    private static final int NB_EMPLOYES = 5;
    private static final int MIN_EMPLOYES_PAR_SHIFT = 1;
    private static final int MAX_EMPLOYES_PAR_SHIFT = 2;
    private static final int MAX_HEURES_PAR_SEMAINE = 40 * 60; // 40h en minutes
    private static final int HEURE_DEBUT_MATIN = 420;  // 7h00
    private static final int HEURE_FIN_MATIN = 945;    // 15h45
    private static final int DUREE_SHIFT_MATIN = 525;  // 8h45
    private static final int HEURE_DEBUT_SOIR = 900;   // 15h00
    private static final int HEURE_FIN_SOIR = 1425;    // 23h45
    private static final int DUREE_SHIFT_SOIR = 525;   // 8h45
    private static final String[] JOURS = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"};
    
    public static void main(String[] args) {
        // Charger les bibliothèques OR-Tools
        Loader.loadNativeLibraries();
        
        // Configuration
        ShiftScheduler scheduler = createTestScheduler();
        printConfiguration(scheduler);
        
        // Construire le modèle
        System.out.println("\n=== Construction du modèle ===");
        scheduler.buildModel();
        System.out.println("Modèle construit avec contraintes de couverture minimale");
        
        // Résoudre
        System.out.println("\n=== Résolution ===");
        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(scheduler.getModel());
        
        System.out.println("Status: " + status);
        
        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            printSolution(scheduler, solver);
        } else {
            System.out.println("❌ Aucune solution trouvée!");
        }
    }

        private static ShiftScheduler createTestScheduler() {
        // Créer des employés
        Employee alice = new Employee("E1", "Alice");
        Employee bob = new Employee("E2", "Bob");
        Employee charlie = new Employee("E3", "Charlie");
        Employee david = new Employee("E4", "David");
        Employee eva = new Employee("E5", "Eva");
        List<Employee> employees = Arrays.asList(alice, bob, charlie, david, eva);
        
        // Créer des types de shift
        ShiftType matin = new ShiftType("MATIN", HEURE_DEBUT_MATIN, HEURE_FIN_MATIN, DUREE_SHIFT_MATIN);
        ShiftType soir = new ShiftType("SOIR", HEURE_DEBUT_SOIR, HEURE_FIN_SOIR, DUREE_SHIFT_SOIR);
        
        // Créer une semaine
        Week week = Week.create(0);
        
        // Créer des shifts pour une semaine complète
        List<Shift> shifts = new java.util.ArrayList<>();
        
        for (int i = 0; i < JOURS.length; i++) {
            Day day = week.getDay(i);
            // Shift du matin
            shifts.add(new Shift(JOURS[i] + "-MATIN", day, matin, MIN_EMPLOYES_PAR_SHIFT, MAX_EMPLOYES_PAR_SHIFT));
            // Shift du soir
            shifts.add(new Shift(JOURS[i] + "-SOIR", day, soir, MIN_EMPLOYES_PAR_SHIFT, MAX_EMPLOYES_PAR_SHIFT));
        }
        
        // Créer le scheduler
        ShiftScheduler scheduler = new ShiftScheduler();
        scheduler.setEmployees(employees);
        scheduler.setShifts(shifts);
        scheduler.setMaxHoursPerWeek(MAX_HEURES_PAR_SEMAINE);
        scheduler.setShiftIndexMap(new HashMap<>());
        
        // Indexer les shifts
        for (int i = 0; i < shifts.size(); i++) {
            scheduler.getShiftIndexMap().put(shifts.get(i).id(), i);
        }
        
        return scheduler;
    }
    
    
    private static void printConfiguration(ShiftScheduler scheduler) {
        System.out.println("=== Configuration ===");
        System.out.println("Employés : " + scheduler.getEmployees().size());
        scheduler.getEmployees().forEach(e -> System.out.println("  - " + e.nom() + " (" + e.id() + ")"));
        
        System.out.println("\nShifts :");
        scheduler.getShifts().forEach(s -> System.out.println("  - " + s.id() + 
            " : " + s.minEmployes() + "-" + s.maxEmployes() + " employés"));
    }
    
    private static void printSolution(ShiftScheduler scheduler, CpSolver solver) {
        List<Employee> employees = scheduler.getEmployees();
        List<Shift> shifts = scheduler.getShifts();
        
        System.out.println("\n=== Solution trouvée ===");
        
        // Afficher les assignations
        for (int s = 0; s < shifts.size(); s++) {
            Shift shift = shifts.get(s);
            System.out.println("\n" + shift.id() + " (requis: " + 
                shift.minEmployes() + "-" + shift.maxEmployes() + "):");
            
            int assignedCount = 0;
            for (int e = 0; e < employees.size(); e++) {
                if (solver.value(scheduler.getAssignments()[e][s]) == 1) {
                    System.out.println("  [OK] " + employees.get(e).nom());
                    assignedCount++;
                }
            }
            
            System.out.println("  Total assignés: " + assignedCount);
            
            // Vérifier la contrainte
            if (assignedCount >= shift.minEmployes() && assignedCount <= shift.maxEmployes()) {
                System.out.println("  [OK] Contrainte respectée");
            } else {
                System.out.println("  [ERREUR] Contrainte violée!");
            }
        }
        
        // Afficher les heures par employé par semaine
        printHoursPerEmployee(scheduler, solver, employees, shifts);
    }
    
    private static void printHoursPerEmployee(ShiftScheduler scheduler, CpSolver solver, 
                                            List<Employee> employees, List<Shift> shifts) {
        System.out.println("\n=== Heures par employé par semaine ===");
        
        // Calculer le nombre de semaines
        int nbWeeks = shifts.stream()
            .mapToInt(shift -> shift.day().getWeekNumber())
            .max()
            .orElse(0) + 1;
        
        for (int e = 0; e < employees.size(); e++) {
            Employee employee = employees.get(e);
            System.out.println("\n" + employee.nom() + " (" + employee.id() + "):");
            
            int totalHours = 0;
            
            for (int w = 0; w < nbWeeks; w++) {
                int weekHours = 0;
                
                // Calculer les heures pour cette semaine
                for (int s = 0; s < shifts.size(); s++) {
                    Shift shift = shifts.get(s);
                    int shiftWeek = shift.day().getWeekNumber();
                    
                    if (shiftWeek == w && solver.value(scheduler.getAssignments()[e][s]) == 1) {
                        weekHours += shift.type().dureeMinutes();
                    }
                }
                
                double weekHoursDouble = weekHours / 60.0;
                System.out.printf("  Semaine %d: %.1fh", (w + 1), weekHoursDouble);
                
                // Vérifier si la limite est dépassée
                if (weekHours > MAX_HEURES_PAR_SEMAINE) {
                    System.out.print(" [ERREUR - Limite dépassée!]");
                } else if (weekHours > 0) {
                    System.out.print(" [OK]");
                }
                System.out.println();
                
                totalHours += weekHours;
            }
            
            double totalHoursDouble = totalHours / 60.0;
            System.out.printf("  TOTAL: %.1fh\n", totalHoursDouble);
        }
    }
}