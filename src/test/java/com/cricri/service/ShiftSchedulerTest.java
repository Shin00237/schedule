package com.cricri.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.model.ShiftType;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;

class ShiftSchedulerTest {

    private ShiftScheduler scheduler;
    private List<Employee> employees;
    private List<Shift> shifts;
    private List<ShiftType> shiftTypes;

    @BeforeEach
    void setUp() {
        Loader.loadNativeLibraries();
        
        // Créer des employés
        Employee emp1 = new Employee("E1", "Alice");
        Employee emp2 = new Employee("E2", "Bob");
        
        employees = Arrays.asList(emp1, emp2);
        
        // Créer des types de shift
        ShiftType matin = new ShiftType("MATIN", 390, 870, 480); // 6h30-14h30, 8h       
        shiftTypes = Arrays.asList(matin);
        
        // Créer des shifts
        Shift shift1 = new Shift("Jour1-MATIN", 1, matin, 1, 2);
        Shift shift2 = new Shift("Jour2-MATIN", 2, matin, 1, 1);
        shifts = Arrays.asList(shift1, shift2);
        
        // Initialiser le scheduler
        scheduler = new ShiftScheduler();
        scheduler.setEmployees(employees);
        scheduler.setShifts(shifts);
        scheduler.setShiftIndexMap(new HashMap<>());
        for (int i = 0; i < shifts.size(); i++) {
            scheduler.getShiftIndexMap().put(shifts.get(i).id(), i);
        }
    }

    @Test
    void testEachShiftMustBeCovered() {
        // Construire le modèle
        scheduler.buildModel();
        
        // Résoudre
        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(scheduler.getModel());
        
        // Vérifier qu'une solution existe
        assertEquals(CpSolverStatus.OPTIMAL, status, "Une solution doit exister");
        
        // Vérifier que chaque shift a au moins un employé assigné
        for (int s = 0; s < shifts.size(); s++) {
            long employeesAssigned = 0;
            for (int e = 0; e < employees.size(); e++) {
                if (solver.value(scheduler.getAssignments()[e][s]) == 1) {
                    employeesAssigned++;
                }
            }
            assertTrue(employeesAssigned >= shifts.get(s).minEmployes(), 
                      "Le shift " + shifts.get(s).id() + " doit avoir au moins " + 
                      shifts.get(s).minEmployes() + " employé(s), mais n'en a que " + employeesAssigned);
        }
    }

    @Test
    void testMaxHoursPerWeekConstraint() {
        // Configuration pour tester la contrainte hebdomadaire - plus réaliste
        Employee emp1 = new Employee("E1", "Alice");
        Employee emp2 = new Employee("E2", "Bob");
        List<Employee> testEmployees = Arrays.asList(emp1, emp2);
        
        ShiftType normalShift = new ShiftType("NORMAL", 480, 960, 480); // 8h-16h = 8h
        
        // Créer des shifts sur 2 semaines - faisable avec 2 employés
        Shift lundi1 = new Shift("Lundi1-NORMAL", 1, normalShift, 1, 1);    // Semaine 1
        Shift mardi1 = new Shift("Mardi1-NORMAL", 2, normalShift, 1, 1);    // Semaine 1  
        Shift mercredi1 = new Shift("Mercredi1-NORMAL", 3, normalShift, 1, 1); // Semaine 1
        Shift jeudi1 = new Shift("Jeudi1-NORMAL", 4, normalShift, 1, 1);    // Semaine 1
        Shift vendredi1 = new Shift("Vendredi1-NORMAL", 5, normalShift, 1, 1); // Semaine 1
        Shift lundi2 = new Shift("Lundi2-NORMAL", 8, normalShift, 1, 1);    // Semaine 2
        Shift mardi2 = new Shift("Mardi2-NORMAL", 9, normalShift, 1, 1);    // Semaine 2
        
        List<Shift> testShifts = Arrays.asList(lundi1, mardi1, mercredi1, jeudi1, vendredi1, lundi2, mardi2);
        
        // Créer un scheduler avec limite réaliste
        ShiftScheduler testScheduler = new ShiftScheduler();
        testScheduler.setEmployees(testEmployees);
        testScheduler.setShifts(testShifts);
        testScheduler.setMaxHoursPerWeek(40 * 60); // Limite: 40h par semaine (5 shifts max)
        testScheduler.setShiftIndexMap(new HashMap<>());
        
        for (int i = 0; i < testShifts.size(); i++) {
            testScheduler.getShiftIndexMap().put(testShifts.get(i).id(), i);
        }
        
        // Construire le modèle
        testScheduler.buildModel();
        
        // Résoudre
        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(testScheduler.getModel());
        
        // Vérifier qu'une solution existe
        assertTrue(status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE, 
                  "Une solution doit exister avec la contrainte d'heures");
        
        // Vérifier que la contrainte d'heures par semaine est respectée
        int[][] hoursPerEmployeePerWeek = new int[2][2]; // [employé][semaine]
        
        for (int e = 0; e < 2; e++) {
            for (int s = 0; s < testShifts.size(); s++) {
                if (solver.value(testScheduler.getAssignments()[e][s]) == 1) {
                    Shift shift = testShifts.get(s);
                    int week = (shift.jour() - 1) / 7;
                    hoursPerEmployeePerWeek[e][week] += shift.type().dureeMinutes();
                }
            }
        }
        
        // Vérifier que chaque employé respecte la limite par semaine
        for (int e = 0; e < 2; e++) {
            for (int w = 0; w < 2; w++) {
                assertTrue(hoursPerEmployeePerWeek[e][w] <= 40 * 60, 
                          "L'employé " + (e + 1) + " semaine " + (w + 1) + " dépasse la limite: " + 
                          (hoursPerEmployeePerWeek[e][w] / 60.0) + "h > 40h");
            }
        }
        
        // Vérifier que tous les shifts sont couverts
        int coveredShifts = 0;
        for (int s = 0; s < testShifts.size(); s++) {
            for (int e = 0; e < 2; e++) {
                if (solver.value(testScheduler.getAssignments()[e][s]) == 1) {
                    coveredShifts++;
                    break; // Un seul employé par shift suffit
                }
            }
        }
        assertEquals(testShifts.size(), coveredShifts, "Tous les shifts doivent être couverts");
    }
}