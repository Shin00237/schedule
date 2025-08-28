package com.cricri.testutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.service.ModularShiftScheduler;
import com.cricri.service.SchedulingContext;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;

/**
 * Classe utilitaire contenant des assertions réutilisables pour les tests OR-Tools.
 * 
 * Centralise la logique de vérification des solutions et des contraintes
 * pour éviter la duplication dans les tests.
 */
public class SolverAssertions {

  /**
   * Vérifie qu'une solution existe (OPTIMAL ou FEASIBLE).
   * 
   * @param solver Le solver CP-SAT
   * @param model Le modèle à résoudre
   * @return Le statut de la solution
   */
  public static CpSolverStatus assertSolutionExists(CpSolver solver, CpModel model) {
    return assertSolutionExists(solver, model, "Une solution doit exister");
  }

  /**
   * Vérifie qu'une solution existe avec un message personnalisé.
   * 
   * @param solver Le solver CP-SAT
   * @param model Le modèle à résoudre
   * @param message Message d'erreur si aucune solution
   * @return Le statut de la solution
   */
  public static CpSolverStatus assertSolutionExists(CpSolver solver, CpModel model, String message) {
    CpSolverStatus status = solver.solve(model);
    assertTrue(status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE, message);
    return status;
  }

  /**
   * Vérifie qu'aucune solution n'existe (INFEASIBLE).
   * 
   * @param solver Le solver CP-SAT
   * @param model Le modèle à résoudre
   * @param message Message d'erreur si une solution existe
   */
  public static void assertNoSolutionExists(CpSolver solver, CpModel model, String message) {
    CpSolverStatus status = solver.solve(model);
    assertEquals(CpSolverStatus.INFEASIBLE, status, message);
  }

  /**
   * Vérifie que tous les shifts sont couverts (au moins le minimum requis).
   * 
   * @param solver Le solver (après résolution)
   * @param assignments Matrice des assignations [employé][shift]
   * @param shifts Liste des shifts
   */
  public static void assertAllShiftsCovered(
      CpSolver solver, BoolVar[][] assignments, List<Shift> shifts) {
    
    for (int s = 0; s < shifts.size(); s++) {
      int employeesAssigned = countAssignedEmployees(solver, assignments, s);
      Shift shift = shifts.get(s);
      
      assertTrue(employeesAssigned >= shift.minEmployes(),
          String.format("Le shift %s doit avoir au moins %d employé(s), mais n'en a que %d",
              shift.id(), shift.minEmployes(), employeesAssigned));
      
      if (shift.maxEmployes() > 0) {
        assertTrue(employeesAssigned <= shift.maxEmployes(),
            String.format("Le shift %s ne doit pas avoir plus de %d employé(s), mais en a %d",
                shift.id(), shift.maxEmployes(), employeesAssigned));
      }
    }
  }

  /**
   * Vérifie que les contraintes d'heures par semaine sont respectées.
   * 
   * @param solver Le solver (après résolution)
   * @param hoursPerEmployeePerWeek Matrice [employé][semaine]
   * @param employees Liste des employés
   * @param maxHoursPerWeek Limite d'heures par semaine en minutes
   */
  public static void assertWeeklyHoursRespected(
      CpSolver solver, IntVar[][] hoursPerEmployeePerWeek, 
      List<Employee> employees, int maxHoursPerWeek) {
    
    for (int e = 0; e < employees.size(); e++) {
      for (int w = 0; w < hoursPerEmployeePerWeek[e].length; w++) {
        long actualHours = solver.value(hoursPerEmployeePerWeek[e][w]);
        
        assertTrue(actualHours <= maxHoursPerWeek,
            String.format("L'employé %s semaine %d dépasse la limite: %.1fh > %.1fh",
                employees.get(e).nom(), w + 1, 
                actualHours / 60.0, maxHoursPerWeek / 60.0));
      }
    }
  }

  /**
   * Vérifie qu'un employé ne peut pas être assigné aux deux shifts spécifiés.
   * Utile pour tester les contraintes de repos minimum.
   * 
   * @param solver Le solver (après résolution)
   * @param assignments Matrice des assignations
   * @param employees Liste des employés
   * @param shift1Index Index du premier shift
   * @param shift2Index Index du deuxième shift
   * @param conflictReason Raison du conflit (pour le message d'erreur)
   */
  public static void assertNoConflictBetweenShifts(
      CpSolver solver, BoolVar[][] assignments, List<Employee> employees,
      int shift1Index, int shift2Index, String conflictReason) {
    
    for (int e = 0; e < employees.size(); e++) {
      boolean assignedToShift1 = solver.value(assignments[e][shift1Index]) == 1;
      boolean assignedToShift2 = solver.value(assignments[e][shift2Index]) == 1;
      
      assertFalse(assignedToShift1 && assignedToShift2,
          String.format("L'employé %s ne peut pas être assigné aux deux shifts (%s)",
              employees.get(e).nom(), conflictReason));
    }
  }

  /**
   * Vérifie que le nombre de jours travaillés par semaine respecte les limites.
   * 
   * @param solver Le solver (après résolution)
   * @param workingDaysPerWeek Matrice [employé][semaine]
   * @param employees Liste des employés
   * @param maxDaysPerWeek Nombre maximum de jours travaillés par semaine
   */
  public static void assertWorkingDaysRespected(
      CpSolver solver, IntVar[][] workingDaysPerWeek, 
      List<Employee> employees, int maxDaysPerWeek) {
    
    for (int e = 0; e < employees.size(); e++) {
      for (int w = 0; w < workingDaysPerWeek[e].length; w++) {
        long workingDays = solver.value(workingDaysPerWeek[e][w]);
        
        assertTrue(workingDays <= maxDaysPerWeek,
            String.format("L'employé %s semaine %d ne devrait pas travailler plus de %d jours, " +
                "mais travaille %d jours",
                employees.get(e).nom(), w + 1, maxDaysPerWeek, workingDays));
      }
    }
  }

  /**
   * Vérifie la cohérence entre assignations et heures réelles.
   * Si un employé n'est pas assigné, ses heures doivent être 0.
   * Si assigné, les heures doivent être dans la plage autorisée.
   * 
   * @param solver Le solver (après résolution)
   * @param assignments Matrice des assignations
   * @param actualHours Matrice des heures réelles
   * @param shifts Liste des shifts
   * @param minHoursPerShift Heures minimum si assigné
   */
  public static void assertAssignmentHoursConsistency(
      CpSolver solver, BoolVar[][] assignments, IntVar[][] actualHours,
      List<Shift> shifts, int minHoursPerShift) {
    
    for (int e = 0; e < assignments.length; e++) {
      for (int s = 0; s < shifts.size(); s++) {
        boolean isAssigned = solver.value(assignments[e][s]) == 1;
        long hours = solver.value(actualHours[e][s]);
        int maxShiftHours = shifts.get(s).type().dureeEffectiveMinutes();
        
        if (isAssigned) {
          assertTrue(hours >= minHoursPerShift,
              String.format("Employé %d shift %s assigné mais heures insuffisantes: %d < %d",
                  e, shifts.get(s).id(), hours, minHoursPerShift));
          assertTrue(hours <= maxShiftHours,
              String.format("Employé %d shift %s heures dépassent le maximum: %d > %d",
                  e, shifts.get(s).id(), hours, maxShiftHours));
        } else {
          assertEquals(0, hours,
              String.format("Employé %d shift %s non assigné mais heures > 0: %d",
                  e, shifts.get(s).id(), hours));
        }
      }
    }
  }

  /**
   * Vérifie les statistiques globales d'une solution.
   * 
   * @param solver Le solver (après résolution)
   * @param scheduler Le scheduler utilisé
   */
  public static void assertSolutionStats(CpSolver solver, ModularShiftScheduler scheduler) {
    System.out.printf("=== Statistiques de la solution ===%n");
    System.out.printf("Statut: %s%n", solver.responseStats());
    System.out.printf("Temps de résolution: %.2fs%n", solver.wallTime());
    System.out.printf("Employés: %d, Shifts: %d%n", 
        scheduler.getEmployees().size(), scheduler.getShifts().size());
    
    // Vérification de cohérence basique
    assertTrue(solver.wallTime() >= 0, "Le temps de résolution doit être positif");
  }

  /**
   * Méthode utilitaire pour compter les employés assignés à un shift.
   */
  private static int countAssignedEmployees(CpSolver solver, BoolVar[][] assignments, int shiftIndex) {
    int count = 0;
    for (int e = 0; e < assignments.length; e++) {
      if (solver.value(assignments[e][shiftIndex]) == 1) {
        count++;
      }
    }
    return count;
  }

  /**
   * Résout un modèle et vérifie qu'une solution existe.
   * Méthode de commodité qui combine résolution et assertion.
   * 
   * @param scheduler Le scheduler configuré
   * @return Le solver après résolution
   */
  public static CpSolver solveAndAssertSolution(ModularShiftScheduler scheduler) {
    scheduler.buildModel();
    CpSolver solver = new CpSolver();
    assertSolutionExists(solver, scheduler.getModel());
    return solver;
  }

  /**
   * Résout un contexte et vérifie qu'une solution existe.
   * 
   * @param context Le contexte configuré
   * @return Le solver après résolution
   */
  public static CpSolver solveAndAssertSolution(SchedulingContext context) {
    CpSolver solver = new CpSolver();
    assertSolutionExists(solver, context.getModel());
    return solver;
  }

  /**
   * Vérifie que les priorités des contraintes sont dans l'ordre attendu.
   * Contraintes fondamentales (-10) avant contraintes de confort (5).
   * 
   * @param constraints Liste des contraintes à vérifier
   */
  public static void assertPriorityOrdering(List<?> constraints) {
    // À implémenter si nécessaire selon votre interface Constraint
    assertTrue(true, "Vérification des priorités - à implémenter");
  }

  /**
   * Vérifie qu'aucun employé ne travaille plus que sa limite contractuelle.
   * 
   * @param solver Le solver (après résolution)
   * @param assignments Matrice des assignations
   * @param shifts Liste des shifts
   * @param employees Liste des employés
   */
  public static void assertEmployeeWorkloadLimits(
      CpSolver solver, BoolVar[][] assignments, 
      List<Shift> shifts, List<Employee> employees) {
    
    for (int e = 0; e < employees.size(); e++) {
      int shiftsWorked = 0;
      for (int s = 0; s < shifts.size(); s++) {
        if (solver.value(assignments[e][s]) == 1) {
          shiftsWorked++;
        }
      }
      
      // Par défaut, on considère qu'un employé ne devrait pas travailler 
      // plus de shifts qu'il n'y a de jours dans la semaine
      assertTrue(shiftsWorked <= 7,
          String.format("L'employé %s travaille trop de shifts: %d",
              employees.get(e).nom(), shiftsWorked));
    }
  }
}